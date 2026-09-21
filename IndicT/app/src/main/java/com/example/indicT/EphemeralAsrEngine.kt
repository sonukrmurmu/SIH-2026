package com.example.indicT

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*

class EphemeralAsrEngine(private val assetManager: AssetManager) {
    private val TAG = "SIH_ASR"

    fun transcribe(
        pcmAudioArray: FloatArray,
        modelAssetPath: String = "asrenglish/model.int8.onnx",
        tokensAssetPath: String = "asrenglish/tokens.txt"
    ): String {
        Log.i(TAG, "ASR Engine Booting: Loading model ($modelAssetPath) into RAM.")

        val recognizer = OfflineRecognizer(
            assetManager = assetManager,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(
                        model = modelAssetPath
                    ),
                    tokens = tokensAssetPath,
                    numThreads = 4, // Maximize performance during the brief execution window
                    debug = false,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
        )

        val stream = recognizer.createStream()
        stream.acceptWaveform(pcmAudioArray, sampleRate = 16000)
        recognizer.decode(stream)

        val resultText = recognizer.getResult(stream).text

        // CRITICAL: Wipe C++ pointers immediately after transcription
        stream.release()
        recognizer.release()

        Log.i(TAG, "ASR Engine Terminated: Native RAM wiped successfully for $modelAssetPath.")

        return resultText
    }
}