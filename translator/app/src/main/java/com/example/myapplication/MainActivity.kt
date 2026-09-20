package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import java.io.File
import kotlin.concurrent.thread
import android.widget.Spinner
import android.os.Process

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var inputText: EditText
    private lateinit var translateButton: Button
    private lateinit var hindiResult: TextView
    private lateinit var santaliResult: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var sourceLangSpinner: Spinner // ADD THIS
    private lateinit var targetLangSpinner: Spinner // ADD THIS

    private lateinit var dictHelper: DictionaryDbHelper
    private var isEngineReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // MUST BE FIRST

        // Initialize UI IDs exactly once
        statusText = findViewById(R.id.statusText)
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        hindiResult = findViewById(R.id.hindiResult)
        santaliResult = findViewById(R.id.santaliResult)
        progressBar = findViewById(R.id.progressBar)
        sourceLangSpinner = findViewById(R.id.sourceLangSpinner)
        targetLangSpinner = findViewById(R.id.targetLangSpinner)

        dictHelper = DictionaryDbHelper(this)

        try {
            val santaliFont = ResourcesCompat.getFont(this, R.font.noto_sans_ol_chiki)
            santaliResult.typeface = santaliFont
        } catch (e: Exception) {
            e.printStackTrace()
        }

        translateButton.isEnabled = false
        // ... thread { ... } engine loading stays below
        // ... your translateButton.isEnabled = false and thread { ... } loading block stays exactly the same below this
        translateButton.isEnabled = false

        thread {
            try {
                Log.i("SIH_AI", "BOOT 1: Starting file extraction from APK...")
                val engine1Path = copyModelAsset("indictrans2_200m")
                val engine2Path = copyModelAsset("indic-eng")

                Log.i("SIH_AI", "BOOT 2: Extraction finished. Booting C++ Engine 1...")
                val status1 = initNativeTranslator(engine1Path, "")

                Log.i("SIH_AI", "BOOT 3: Booting C++ Engine 2...")
                val status2 = initNativeIndicToEng(engine2Path, "")

                Log.i("SIH_AI", "BOOT 4: Engines returned Status1=$status1, Status2=$status2")

                runOnUiThread {
                    translateButton.isEnabled = true
                    statusText.text = "AI Engines Ready!"

                    // ---> THESE TWO LINES FIX THE ENTIRE BUG <---
                    isEngineReady = true
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("SIH_AI", "BOOT FATAL ERROR: ${e.message}")
            }
        }
        translateButton.setOnClickListener {
            val textToTranslate = inputText.text.toString().trim()

            if (textToTranslate.isNotBlank() && isEngineReady) {
                translateButton.isEnabled = false
                progressBar.visibility = View.VISIBLE
                hindiResult.text = "Translating..."
                santaliResult.text = "..."

                // 1. Read what the user selected in the UI
                val srcString = sourceLangSpinner.selectedItem.toString()
                val tgtString = targetLangSpinner.selectedItem.toString()

                // 2. Convert those UI strings to AI tags
                val srcCode = getFloresCode(srcString)
                val tgtCode = getFloresCode(tgtString)

                // 3. Execute translation on background thread
                thread {
                    try {
                        // SAFE SPEED BOOST: Uses Java VM priority instead of the strict Android Process
                        // This bypasses the ColorOS security crash while keeping the AI fast

                        var intermediateLog = "Direct Translation"
                        val finalTranslation: String

                        if (srcCode == tgtCode) {
                            // User selected same language for source and target
                            finalTranslation = textToTranslate
                            intermediateLog = "Same language selected."
                        } else if (srcCode == "eng_Latn") {
                            // ROUTE 1: English -> Indic (Only uses Engine 1)
                            finalTranslation = translateWithBulletproofShield(textToTranslate, tgtCode)
                        } else if (tgtCode == "eng_Latn") {
                            // ROUTE 2: Indic -> English (Only uses Engine 2)
                            var eng = translateIndicToEng(textToTranslate, srcCode)
                            eng = eng.replace("eng_Latn", "").trim()
                            val garbage = charArrayOf(' ', ',', '?', '.', '।', '᱾')
                            while (eng.isNotEmpty() && garbage.contains(eng.first())) {
                                eng = eng.substring(1).trim()
                            }
                            finalTranslation = eng
                        } else {
                            // ROUTE 3: Indic -> Indic (Uses Engine 2 -> Engine 1 Relay)
                            var localMatch: String? = null
                            if (srcCode == "hin_Deva" && tgtCode == "sat_Olck") {
                                localMatch = dictHelper.lookup(textToTranslate)
                            }

                            if (localMatch != null) {
                                finalTranslation = localMatch
                                intermediateLog = "Found in Local SQLite Dictionary"
                            } else {
                                finalTranslation =
                                    translateIndicToIndic(textToTranslate, srcCode, tgtCode)
                                intermediateLog = "Dual-Engine Relay Used"
                            }
                        }

                        // Update UI with the final result
                        runOnUiThread {
                            hindiResult.text = intermediateLog
                            santaliResult.text = finalTranslation
                            translateButton.isEnabled = true
                            progressBar.visibility = View.GONE
                        }

                    } catch (e: Exception) {
                        // UI SAFETY NET: Catches silent C++ crashes so the loading spinner stops
                        android.util.Log.e("SIH_AI", "CLICK FATAL ERROR: ${e.message}")
                        e.printStackTrace()

                        runOnUiThread {
                            santaliResult.text = "Error: ${e.message}"
                            translateButton.isEnabled = true
                            progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
    private fun getFloresCode(language: String): String {
        return when (language) {
            "English" -> "eng_Latn"
            "Hindi" -> "hin_Deva"
            "Santali" -> "sat_Olck"
            else -> "eng_Latn"
        }
    }

    private fun translateWithBulletproofShield(userText: String, targetLang: String): String {
        Log.i("SIH_AI", "LAYER 1 [KOTLIN SEND] ($targetLang): $userText")

        // Direct send without dummy "1 " tokens (C++ handles punctuation termination)
        var rawOutput = translateNativeText(userText, "eng_Latn", targetLang)

        Log.i("SIH_AI", "LAYER 5 [KOTLIN RAW RECEIVE] ($targetLang): $rawOutput")
        val originalRaw = rawOutput

        // 1. Strict front cleanup: Strips stray punctuation/spaces hallucinated at the start
        val frontGarbage = charArrayOf(' ', ',', '?', '.', '।', '᱾')
        while (rawOutput.isNotEmpty() && frontGarbage.contains(rawOutput.first())) {
            rawOutput = rawOutput.substring(1).trim()
        }

        // 2. Relaxed end cleanup: ONLY strips trailing spaces and commas; preserves '?', '.', '।', '᱾'
        val endGarbage = charArrayOf(' ', ',')
        while (rawOutput.isNotEmpty() && endGarbage.contains(rawOutput.last())) {
            rawOutput = rawOutput.dropLast(1).trim()
        }

        if (rawOutput.isBlank()) {
            return originalRaw.ifBlank { "Translation Error" }
        }

        return rawOutput
    }
    private fun translateIndicToIndic(userText: String, sourceIndicLang: String, targetIndicLang: String): String {
        // HOP 1: Indic -> English (Using Engine 2)
        Log.i("SIH_AI", "HOP 1: $sourceIndicLang -> eng_Latn")
        var intermediateEnglish = translateIndicToEng(userText, sourceIndicLang)

        // Clean up HOP 1 Output (Strip unwanted artifacts before passing to Engine 1)
        intermediateEnglish = intermediateEnglish.replace("eng_Latn", "").trim()
        val garbage = charArrayOf(' ', ',', '?', '.', '।', '᱾')
        while (intermediateEnglish.isNotEmpty() && garbage.contains(intermediateEnglish.first())) {
            intermediateEnglish = intermediateEnglish.substring(1).trim()
        }

        if (intermediateEnglish.isBlank()) return "Error in Hop 1"

        // HOP 2: English -> Indic (Using Engine 1 via the Shield)
        Log.i("SIH_AI", "HOP 2: eng_Latn -> $targetIndicLang")
        return translateWithBulletproofShield(intermediateEnglish, targetIndicLang)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isEngineReady) {
            unloadNativeTranslator()
            unloadNativeIndicToEng() // Add Engine 2 cleanup
        }
    }

    private fun copyModelAsset(folderName: String): String {
        val modelDir = File(filesDir, folderName)
        if (!modelDir.exists() || modelDir.list().isNullOrEmpty()) {
            modelDir.mkdirs()
            copyAssetFolder(folderName, modelDir)
        }
        return modelDir.absolutePath
    }

    private fun copyAssetFolder(assetPath: String, destDir: File): Boolean {
        return try {
            val files = assets.list(assetPath) ?: return false
            if (!destDir.exists()) destDir.mkdirs()

            for (filename in files) {
                val subAssetPath = "$assetPath/$filename"
                val destFile = File(destDir, filename)

                val subFiles = assets.list(subAssetPath)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    copyAssetFolder(subAssetPath, destFile)
                } else {
                    assets.open(subAssetPath).use { inputStream ->
                        destFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Engine 1: English-to-Indic
    external fun initNativeTranslator(modelDir: String, spmPath: String): Int
    external fun translateNativeText(text: String, srcLang: String, tgtLang: String): String
    external fun unloadNativeTranslator()

    // Engine 2: Indic-to-English
    external fun initNativeIndicToEng(modelDir: String, spmPath: String): Int
    external fun translateIndicToEng(text: String, srcLang: String): String
    external fun unloadNativeIndicToEng()

    companion object {
        init {
            System.loadLibrary("sihtranslator")
            System.loadLibrary("sih_indic_to_en")
        }
    }
}