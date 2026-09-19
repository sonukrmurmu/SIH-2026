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

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var inputText: EditText
    private lateinit var translateButton: Button
    private lateinit var hindiResult: TextView
    private lateinit var santaliResult: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var dictHelper: DictionaryDbHelper
    private var isEngineReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        hindiResult = findViewById(R.id.hindiResult)
        santaliResult = findViewById(R.id.santaliResult)
        progressBar = findViewById(R.id.progressBar)

        dictHelper = DictionaryDbHelper(this)

        try {
            val santaliFont = ResourcesCompat.getFont(this, R.font.noto_sans_ol_chiki)
            santaliResult.typeface = santaliFont
        } catch (e: Exception) {
            e.printStackTrace()
        }

        translateButton.isEnabled = false

        thread {
            val modelPath = copyAssetsToInternalStorage()
            val spmPath = "$modelPath/spiece.model"
            val status = initNativeTranslator(modelPath, spmPath)

            runOnUiThread {
                progressBar.visibility = View.GONE
                if (status == 0) {
                    statusText.text = "Engine Ready! IndicTrans2 200M Loaded."
                    isEngineReady = true
                    translateButton.isEnabled = true
                } else {
                    statusText.text = "Failed to load model. Error: $status"
                }
            }
        }

        translateButton.setOnClickListener {
            val textToTranslate = inputText.text.toString().trim()

            if (textToTranslate.isNotBlank() && isEngineReady) {
                translateButton.isEnabled = false
                progressBar.visibility = View.VISIBLE
                hindiResult.text = "Translating..."

                // Override AI entirely if phrase is mapped in SQLite database
                var localSantaliMatch: String? = dictHelper.lookup(textToTranslate)

                if (localSantaliMatch != null) {
                    santaliResult.text = localSantaliMatch
                } else {
                    santaliResult.text = "Translating..."
                }

                thread {
                    val hindi = translateWithBulletproofShield(textToTranslate, "hin_Deva")

                    val santali = if (localSantaliMatch != null) {
                        localSantaliMatch
                    } else {
                        translateWithBulletproofShield(textToTranslate, "sat_Olck")
                    }

                    runOnUiThread {
                        hindiResult.text = hindi
                        santaliResult.text = santali
                        translateButton.isEnabled = true
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun translateWithBulletproofShield(userText: String, targetLang: String): String {
        val dummyInput = "1 "
        val shieldedInput = "$dummyInput$userText"

        Log.i("SIH_AI", "LAYER 1 [KOTLIN SEND] ($targetLang): $shieldedInput")

        var rawOutput = translateNativeText(shieldedInput, targetLang)

        Log.i("SIH_AI", "LAYER 5 [KOTLIN RAW RECEIVE] ($targetLang): $rawOutput")

        val originalRaw = rawOutput // Keep a backup in case we strip too much
        // 1. The Regex Split
        val parts = rawOutput.split(Regex("[।᱾.]"), limit = 2)
        if (parts.size > 1 && parts[1].isNotBlank()) {
            rawOutput = parts[1].trim()
        } else {
            val knownDummyOutputs = listOf("एक ","१ ", "1 ", "१ ", "᱑ ","Occe", "कर रहे है")
            for (dummy in knownDummyOutputs) {
                if (rawOutput.contains(dummy, ignoreCase = true)) {
                    rawOutput = rawOutput.replaceFirst(Regex(".*?$dummy\\s*"), "").trim()
                    break
                }
            }
        }

        // 2. Strip leading artifacts safely
        val garbageChars = charArrayOf(' ', ',', '?', '.', '।', '᱾')
        while (rawOutput.isNotEmpty() && garbageChars.contains(rawOutput.first())) {
            rawOutput = rawOutput.substring(1).trim()
        }

        // ---> SAFETY NET: If stripping made it completely empty, return the backup instead of blank text
        if (rawOutput.isBlank()) {
            return originalRaw.ifBlank { "Translation Error" }
        }

        return rawOutput
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isEngineReady) {
            unloadNativeTranslator()
        }
    }

    private fun copyAssetsToInternalStorage(): String {
        val modelDir = File(filesDir, "indictrans2_200m")
        if (!modelDir.exists() || modelDir.list().isNullOrEmpty()) {
            modelDir.mkdirs()
            copyAssetFolder("indictrans2_200m", modelDir)
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

    external fun initNativeTranslator(modelDir: String, spmPath: String): Int
    external fun translateNativeText(text: String, tgtLang: String): String
    external fun unloadNativeTranslator()

    companion object {
        init {
            System.loadLibrary("sihtranslator")
        }
    }
}