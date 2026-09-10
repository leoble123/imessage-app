package com.leo.imessage.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * Records a voice message as raw PCM in a WAV container.
 *
 * MediaRecorder would give a smaller AAC file, and that's what this used to
 * do - but Android's speech recogniser can only be pointed at a file if that
 * file is raw PCM, so an AAC voice note is one that can never be
 * transcribed. WAV at 16 kHz mono costs about 32 KB a second, which for a
 * message measured in seconds is a fair trade for being able to read it.
 */
class VoiceRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val HEADER_BYTES = 44
    }

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    /** Latest amplitude, 0..1, for driving a live waveform. */
    @Volatile
    var level: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(target: File): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val bufferSize = minBuffer * 2

        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        }.getOrNull() ?: return false

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        recorder = record
        running = true
        record.startRecording()

        worker = thread(name = "voice-recorder") {
            target.outputStream().use { out ->
                // Placeholder header, rewritten with real sizes on stop.
                out.write(ByteArray(HEADER_BYTES))
                val buffer = ByteArray(bufferSize)
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        level = peakOf(buffer, read)
                    }
                }
            }
        }
        return true
    }

    /** Stops and finalises the WAV header. Returns false if nothing usable. */
    fun stop(target: File): Boolean {
        running = false
        runCatching { worker?.join(1500) }
        worker = null
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        level = 0f

        if (!target.exists() || target.length() <= HEADER_BYTES) return false
        return runCatching { writeWavHeader(target) }.isSuccess
    }

    fun cancel(target: File) {
        stop(target)
        target.delete()
    }

    private fun peakOf(buffer: ByteArray, read: Int): Float {
        var peak = 0
        var i = 0
        while (i + 1 < read) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
            val magnitude = kotlin.math.abs(sample)
            if (magnitude > peak) peak = magnitude
            i += 2
        }
        return (peak / 32768f).coerceIn(0f, 1f)
    }

    private fun writeWavHeader(file: File) {
        val dataSize = file.length() - HEADER_BYTES
        val totalSize = dataSize + HEADER_BYTES - 8
        val byteRate = SAMPLE_RATE * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write("RIFF".toByteArray())
            raf.write(intLE(totalSize.toInt()))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(intLE(16))            // PCM chunk size
            raf.write(shortLE(1))           // format: PCM
            raf.write(shortLE(1))           // channels
            raf.write(intLE(SAMPLE_RATE))
            raf.write(intLE(byteRate))
            raf.write(shortLE(2))           // block align
            raf.write(shortLE(16))          // bits per sample
            raf.write("data".toByteArray())
            raf.write(intLE(dataSize.toInt()))
        }
    }

    private fun intLE(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun shortLE(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )
}

/**
 * Speech-to-text over a recorded WAV.
 *
 * Uses the platform recogniser's file-input mode, which arrived in Android
 * 12 - before that there is no way to transcribe anything but the live mic,
 * so this returns null rather than pretending. It also returns null if the
 * device has no recogniser installed or the service declines, and the caller
 * simply shows the voice note without a transcript: a wrong transcription is
 * worse than none, and an error banner on a voice message is worse than
 * both.
 */
suspend fun transcribeWav(context: Context, file: File): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return null

    return withTimeoutOrNull(20_000) {
        withContext(Dispatchers.Main) {
            val result = CompletableDeferred<String?>()
            val recognizer = runCatching {
                SpeechRecognizer.createSpeechRecognizer(context)
            }.getOrNull() ?: return@withContext null

            val descriptor = runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull() ?: run {
                recognizer.destroy()
                return@withContext null
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                    if (!result.isCompleted) result.complete(text)
                }

                override fun onError(error: Int) {
                    if (!result.isCompleted) result.complete(null)
                }

                override fun onEndOfSegmentedSession() {
                    if (!result.isCompleted) result.complete(null)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                    VoiceRecorder.SAMPLE_RATE,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE,
                )
            }

            runCatching { recognizer.startListening(intent) }
                .onFailure { if (!result.isCompleted) result.complete(null) }

            val text = result.await()
            runCatching { recognizer.destroy() }
            runCatching { descriptor.close() }
            text
        }
    }
}
