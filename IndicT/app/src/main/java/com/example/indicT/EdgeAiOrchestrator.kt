package com.example.indicT

import android.os.Process
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EdgeAiOrchestrator {
    private const val TAG = "SIH_ORCHESTRATOR"
    private val ramLock = Mutex()

    fun syncTextEngineMemoryState(
        mainActivity: MainActivity,
        srcLang: String,
        tgtLang: String,
        engine1Path: String,
        engine2Path: String
    ) {
        val isEngToIndicNeeded = (tgtLang == "Hindi" || tgtLang == "Santali")
        val isIndicToEngNeeded = (srcLang == "Hindi" || srcLang == "Santali")

        if (!isEngToIndicNeeded) {
            mainActivity.unloadNativeTranslator()
            Log.i(TAG, "RAM OPTIMIZATION: Output is $tgtLang -> Engine 1 (Eng-to-Indic) purged from RAM.")
        }
        if (!isIndicToEngNeeded) {
            mainActivity.unloadNativeIndicToEng()
            Log.i(TAG, "RAM OPTIMIZATION: Input is $srcLang -> Engine 2 (Indic-to-Eng) purged from RAM.")
        }
    }

    suspend fun runIndicToEnglish(
        mainActivity: MainActivity,
        text: String,
        srcLangCode: String,
        modelPath: String,
        beamSize: Int = 1
    ): String {
        return ramLock.withLock {
            var result = ""
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Checking/Loading Engine 2 (beam_size=$beamSize)")
                mainActivity.initNativeIndicToEng(modelPath, "")
                val rawOutput = mainActivity.translateIndicToEng(text, srcLangCode, beamSize)

                result = rawOutput.replace("eng_Latn", "").trim()
                val garbage = charArrayOf(' ', ',', '?', '.', '।', '᱾')
                while (result.isNotEmpty() && garbage.contains(result.first())) {
                    result = result.substring(1).trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Engine 2 translation: ${e.message}")
            } finally {
                Log.i(TAG, "RAM LOCK RELEASED: Engine 2 execution complete.")
            }
            return@withLock result
        }
    }

    suspend fun runEnglishToIndic(
        mainActivity: MainActivity,
        text: String,
        tgtLangCode: String,
        modelPath: String,
        beamSize: Int = 1
    ): String {
        return ramLock.withLock {
            var finalCleanedText = ""
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Checking/Loading Engine 1 (beam_size=$beamSize)")
                mainActivity.initNativeTranslator(modelPath, "")

                val rawOutput = mainActivity.translateNativeText(text, "eng_Latn", tgtLangCode, beamSize)

                var cleaned = rawOutput.trim()
                val garbageChars = charArrayOf(' ', ',', '?', '.', '।', '᱾')
                while (cleaned.isNotEmpty() && garbageChars.contains(cleaned.first())) {
                    cleaned = cleaned.substring(1).trim()
                }

                finalCleanedText = cleaned.ifBlank { "Translation Error" }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Engine 1 translation: ${e.message}")
            } finally {
                Log.i(TAG, "RAM LOCK RELEASED: Engine 1 execution complete.")
            }
            return@withLock finalCleanedText
        }
    }

    suspend fun runAsr(
        asrEngine: EphemeralAsrEngine,
        audioData: FloatArray,
        modelAssetPath: String,
        tokensAssetPath: String
    ): String {
        return ramLock.withLock {
            var transcribedText = ""
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Loading ASR Model ($modelAssetPath)")
                transcribedText = asrEngine.transcribe(audioData, modelAssetPath, tokensAssetPath)
            } finally {
                Log.i(TAG, "RAM LOCK RELEASED: ASR Model ($modelAssetPath) wiped.")
            }
            return@withLock transcribedText
        }
    }

    suspend fun runTts(ttsEngine: EphemeralTtsEngine, text: String): FloatArray {
        return ramLock.withLock {
            var audioSamples = FloatArray(0)
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Loading TTS Model")
                val audioResult = ttsEngine.generateSpeech(text)
                audioSamples = audioResult.samples
            } finally {
                Log.i(TAG, "RAM LOCK RELEASED: TTS Model wiped.")
            }
            return@withLock audioSamples
        }
    }
}