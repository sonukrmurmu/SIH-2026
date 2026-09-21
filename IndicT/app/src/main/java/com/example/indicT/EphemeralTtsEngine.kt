package com.example.indicT

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class EphemeralTtsEngine(
    private val ttsFolderPath: String,
    private val modelFileName: String = "hi_IN-pratham-medium.onnx"
) {

    private val TAG = "TTS_FORENSICS"

    fun generateSpeech(text: String): GeneratedAudio {
        val modelPath = "$ttsFolderPath/$modelFileName"
        val tokensPath = "$ttsFolderPath/tokens.txt"
        val dataDirPath = "$ttsFolderPath/espeak-ng-data"

        val espeakDir = File(dataDirPath)
        Log.d(TAG, "=== ESPEAK-NG-DATA FORENSIC CHECK ===")
        Log.d(TAG, "Directory exists: ${espeakDir.exists()}")
        if (espeakDir.exists()) {
            val files = espeakDir.listFiles()
            Log.d(TAG, "Total files inside espeak-ng-data: ${files?.size ?: 0}")
        }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelPath,
                    tokens = tokensPath,
                    dataDir = dataDirPath
                ),
                numThreads = 1,
                debug = true,
                provider = "cpu"
            )
        )

        Log.d(TAG, "Initializing OfflineTts ($modelFileName)...")
        val tts = OfflineTts(assetManager = null, config = config)
        Log.d(TAG, "SUCCESS! OfflineTts initialized.")

        val audio = tts.generate(text, sid = 0, speed = 1.0f)
        tts.release()
        Log.d(TAG, "TTS Engine Terminated: Native RAM wiped successfully.")
        return audio
    }
}
