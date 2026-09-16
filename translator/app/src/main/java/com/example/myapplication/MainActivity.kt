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

    // ---> NEW: Declare the database helper
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

        // ---> NEW: Initialize the database helper right away
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

                // ---> NEW: Hybrid Logic - Check word count
                val wordCount = textToTranslate.split("\\s+".toRegex()).size
                var localSantaliMatch: String? = null

                // If 1 or 2 words, check the instant SQLite database first
                if (wordCount <= 2) {
                    localSantaliMatch = dictHelper.lookup(textToTranslate)
                }

                if (localSantaliMatch != null) {
                    santaliResult.text = localSantaliMatch // Instant DB result!
                } else {
                    santaliResult.text = "Translating..."
                }

                // Run AI inference in the background
                thread {
                    // Always translate Hindi via neural network
                    val hindi = translateNativeText(textToTranslate, "hin_Deva")

                    // ---> NEW: Only run Santali neural translation if it WASN'T in the database
                    val santali = if (localSantaliMatch != null) {
                        localSantaliMatch
                    } else {
                        translateNativeText(textToTranslate, "sat_Olck")
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

    companion object {
        init {
            System.loadLibrary("sihtranslator")
        }
    }
}