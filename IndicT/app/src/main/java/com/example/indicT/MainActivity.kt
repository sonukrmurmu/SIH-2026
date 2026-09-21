package com.example.indicT

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val TAG = "SIH_MAIN"

    private lateinit var statusText: TextView
    private lateinit var inputText: EditText
    private lateinit var translateButton: Button
    private lateinit var micButton: Button
    private lateinit var btnSpeak: Button
    private lateinit var hindiResult: TextView
    private lateinit var santaliResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sourceLangSpinner: Spinner
    private lateinit var targetLangSpinner: Spinner
    private lateinit var accuracySpinner: Spinner

    private lateinit var dictHelper: DictionaryDbHelper
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var asrEngine: EphemeralAsrEngine

    private var isEngineReady = false
    private var isRecording = false
    private var startupJob: Job? = null

    private lateinit var engine1Path: String
    private lateinit var engine2Path: String
    private lateinit var ttsHindiFolderPath: String
    private lateinit var ttsEnglishFolderPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        statusText = findViewById(R.id.statusText)
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        micButton = findViewById(R.id.micButton)
        btnSpeak = findViewById(R.id.btn_speak)
        hindiResult = findViewById(R.id.hindiResult)
        santaliResult = findViewById(R.id.santaliResult)
        progressBar = findViewById(R.id.progressBar)
        sourceLangSpinner = findViewById(R.id.sourceLangSpinner)
        targetLangSpinner = findViewById(R.id.targetLangSpinner)
        accuracySpinner = findViewById(R.id.accuracySpinner)

        translateButton.isEnabled = false
        micButton.isEnabled = false
        btnSpeak.isEnabled = false

        try {
            val santaliFont = ResourcesCompat.getFont(this, R.font.noto_sans_ol_chiki)
            santaliResult.typeface = santaliFont
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dictHelper = DictionaryDbHelper(this)
        audioRecorder = AudioRecorder()
        asrEngine = EphemeralAsrEngine(assets)

        startAppInitialization()

        translateButton.setOnClickListener { executeTranslation() }
        micButton.setOnClickListener { toggleRecording() }
        btnSpeak.setOnClickListener { executeTts() }

        val langSelectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isEngineReady) {
                    val srcName = sourceLangSpinner.selectedItem.toString()
                    val tgtName = targetLangSpinner.selectedItem.toString()
                    EdgeAiOrchestrator.syncTextEngineMemoryState(this@MainActivity, srcName, tgtName, engine1Path, engine2Path)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        sourceLangSpinner.onItemSelectedListener = langSelectionListener
        targetLangSpinner.onItemSelectedListener = langSelectionListener
    }

    private fun startAppInitialization() {
        startupJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                engine1Path = copyModelAsset("eng-indic")
                engine2Path = copyModelAsset("indic-eng")
                ttsHindiFolderPath = copyModelAsset("tts")
                ttsEnglishFolderPath = copyModelAsset("ttsenglish")

                withContext(Dispatchers.Main) {
                    isEngineReady = true
                    translateButton.isEnabled = true
                    micButton.isEnabled = true
                    btnSpeak.isEnabled = true
                    progressBar.visibility = View.GONE
                    statusText.text = "AI Engines Ready!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Boot Error: ${e.message}"
                }
            }
        }
    }

    private fun executeTranslation() {
        val textToTranslate = inputText.text.toString().trim()
        if (textToTranslate.isBlank() || !isEngineReady) return

        translateButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        hindiResult.text = "Translating..."
        santaliResult.text = "..."

        val srcName = sourceLangSpinner.selectedItem.toString()
        val tgtName = targetLangSpinner.selectedItem.toString()
        val srcCode = getFloresCode(srcName)
        val tgtCode = getFloresCode(tgtName)
        val selectedBeamSize = when (accuracySpinner.selectedItemPosition) {
            0 -> 1 // "5 (Fastest)" -> C++ beam_size 1 (greedy search)
            1 -> 2 // "4 (Fast)" -> C++ beam_size 2
            2 -> 3 // "3 (Balanced)" -> C++ beam_size 3
            3 -> 4 // "2 (High Accuracy)" -> C++ beam_size 4
            4 -> 5 // "1 (Max Accuracy)" -> C++ beam_size 5
            else -> 1
        }
        val displayLevel = 6 - selectedBeamSize

        // Sync text translation engine RAM states based on handwritten blueprint
        EdgeAiOrchestrator.syncTextEngineMemoryState(this, srcName, tgtName, engine1Path, engine2Path)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var intermediateLog = "Direct Translation (Level $displayLevel)"
                val finalTranslation: String

                val localMatch = if (srcCode == "hin_Deva" && tgtCode == "sat_Olck") dictHelper.lookup(textToTranslate) else null

                if (localMatch != null) {
                    finalTranslation = localMatch
                    intermediateLog = "Found in Local SQLite Dictionary"
                } else if (srcCode == tgtCode) {
                    finalTranslation = textToTranslate
                    intermediateLog = "Same language selected."
                } else if (srcCode == "eng_Latn") {
                    finalTranslation = EdgeAiOrchestrator.runEnglishToIndic(this@MainActivity, textToTranslate, tgtCode, engine1Path, selectedBeamSize)
                } else if (tgtCode == "eng_Latn") {
                    finalTranslation = EdgeAiOrchestrator.runIndicToEnglish(this@MainActivity, textToTranslate, srcCode, engine2Path, selectedBeamSize)
                } else {
                    intermediateLog = "Dual-Engine Relay Used (Beam $selectedBeamSize)"
                    val englishPivot = EdgeAiOrchestrator.runIndicToEnglish(this@MainActivity, textToTranslate, srcCode, engine2Path, selectedBeamSize)

                    if (englishPivot.isBlank()) {
                        finalTranslation = "Pivot Error"
                    } else {
                        finalTranslation = EdgeAiOrchestrator.runEnglishToIndic(this@MainActivity, englishPivot, tgtCode, engine1Path, selectedBeamSize)
                    }
                }

                withContext(Dispatchers.Main) {
                    hindiResult.text = intermediateLog
                    santaliResult.text = finalTranslation
                    translateButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    santaliResult.text = "Error: ${e.message}"
                    translateButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun toggleRecording() {
        if (!isRecording) {
            isRecording = true
            micButton.text = "⏹ Stop"
            statusText.text = "Listening..."
            audioRecorder.start()
        } else {
            isRecording = false
            micButton.text = "🎤 Mic"
            statusText.text = "Recognizing Speech..."
            micButton.isEnabled = false
            val audio = audioRecorder.stop()

            val selectedSourceLang = sourceLangSpinner.selectedItem.toString()
            val (modelAssetPath, tokensAssetPath) = when (selectedSourceLang) {
                "English" -> Pair("asrenglish/model.int8.onnx", "asrenglish/tokens.txt")
                "Hindi" -> Pair("asrhindi/model.int8.onnx", "asrhindi/tokens.txt")
                "Santali" -> Pair("voice-santali/model.int8.onnx", "voice-santali/tokens.txt")
                else -> Pair("asrenglish/model.int8.onnx", "asrenglish/tokens.txt")
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val transcribedText = EdgeAiOrchestrator.runAsr(asrEngine, audio, modelAssetPath, tokensAssetPath)
                    withContext(Dispatchers.Main) {
                        if (transcribedText.isNotBlank()) {
                            inputText.setText(transcribedText)
                            statusText.text = "Speech recognized ($selectedSourceLang)"
                        } else {
                            statusText.text = "Could not recognize speech"
                        }
                        micButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "ASR Error: ${e.message}"
                        micButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun executeTts() {
        val selectedTargetLang = targetLangSpinner.selectedItem.toString()
        val targetCode = getFloresCode(selectedTargetLang)
        var textToSpeak = santaliResult.text.toString().trim()

        if (textToSpeak.isBlank() || textToSpeak == "..." || textToSpeak.contains("Translating")) return

        btnSpeak.isEnabled = false
        statusText.text = "Generating Audio..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ttsEngine: EphemeralTtsEngine
                if (selectedTargetLang == "English") {
                    ttsEngine = EphemeralTtsEngine(ttsEnglishFolderPath, "en_US-lessac-medium.onnx")
                } else {
                    if (targetCode == "sat_Olck") {
                        textToSpeak = Transliterator.olChikiToDevanagari(textToSpeak)
                    }
                    ttsEngine = EphemeralTtsEngine(ttsHindiFolderPath, "hi_IN-pratham-medium.onnx")
                }

                val audioSamples = EdgeAiOrchestrator.runTts(ttsEngine, textToSpeak)
                playAudio(audioSamples, 22050)

                withContext(Dispatchers.Main) {
                    statusText.text = "Audio finished."
                    btnSpeak.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "TTS Error: ${e.message}"
                    btnSpeak.isEnabled = true
                }
            }
        }
    }

    private fun playAudio(audioSamples: FloatArray, sampleRate: Int) {
        if (audioSamples.isEmpty()) return
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.play()
        audioTrack.write(audioSamples, 0, audioSamples.size, AudioTrack.WRITE_BLOCKING)
        Thread.sleep(500)
        audioTrack.release()
    }

    private fun getFloresCode(language: String): String {
        return when (language) {
            "English" -> "eng_Latn"
            "Hindi" -> "hin_Deva"
            "Santali" -> "sat_Olck"
            else -> "eng_Latn"
        }
    }

    private fun copyModelAsset(folderName: String): String {
        val destDir = File(filesDir, folderName)
        if (!destDir.exists() || destDir.list().isNullOrEmpty()) {
            destDir.mkdirs()
            copyAssetFolder(folderName, destDir)
        }
        return destDir.absolutePath
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        val files = assets.list(assetPath) ?: return
        if (!destDir.exists()) destDir.mkdirs()

        for (filename in files) {
            val subAssetPath = "$assetPath/$filename"
            val destFile = File(destDir, filename)

            val subFiles = assets.list(subAssetPath)
            if (!subFiles.isNullOrEmpty()) {
                copyAssetFolder(subAssetPath, destFile)
            } else {
                assets.open(subAssetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isRecording) { audioRecorder.stop(); isRecording = false }
        startupJob?.cancel()
        unloadNativeTranslator()
        unloadNativeIndicToEng()
        super.onDestroy()
    }

    external fun initNativeTranslator(modelDir: String, spmPath: String): Int
    external fun translateNativeText(text: String, srcLang: String, tgtLang: String, beamSize: Int): String
    external fun unloadNativeTranslator()

    external fun initNativeIndicToEng(modelDir: String, spmPath: String): Int
    external fun translateIndicToEng(text: String, srcLang: String, beamSize: Int): String
    external fun unloadNativeIndicToEng()

    companion object {
        init {
            System.loadLibrary("sihtranslator")
            System.loadLibrary("sih_indic_to_en")
        }
    }
}