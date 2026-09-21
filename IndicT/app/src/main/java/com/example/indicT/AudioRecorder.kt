package com.example.indicT

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioRecorder {

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    private val audioData = mutableListOf<Short>()

    fun start() {

        val sampleRate = 16000

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioData.clear()

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        recorder?.startRecording()
        isRecording = true

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize)

            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    synchronized(audioData) {
                        for (i in 0 until read) {
                            audioData.add(buffer[i])
                        }
                    }
                }
            }
        }

        recordingThread?.start()
    }

    fun stop(): FloatArray {

        isRecording = false

        recorder?.stop()
        recorder?.release()
        recorder = null

        recordingThread?.join()
        recordingThread = null

        synchronized(audioData) {
            return FloatArray(audioData.size) { i ->
                audioData[i] / 32768.0f
            }
        }
    }
}
