package com.leo.imessage.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import uniffi.imessage_core.ImessageCore
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Microphone in, earpiece out, for a live FaceTime call.
 *
 * rustpush owns everything below this: key exchange, SRTP, packetisation,
 * jitter handling. What it wants is encoded frames, and what it hands back is
 * encoded frames. So this is only two codecs and two audio devices - but the
 * details it gets wrong are audible ones, so they're spelled out below.
 *
 * FaceTime carries AAC. The far end announces its own decoder configuration
 * before its first frame, and that configuration is used verbatim rather than
 * assumed - guessing a sample rate produces a call that runs fast, slow, or
 * as noise, and all three are hard to tell apart by ear.
 */
class CallAudio(
    private val context: Context,
    private val core: ImessageCore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)

    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var callId: String? = null

    /** Frames from the far end, queued so the media thread never blocks. */
    private val incoming = Channel<ByteArray>(capacity = 64)

    /** The far end's AudioSpecificConfig, once it has announced one. */
    @Volatile
    private var farEndConfig: ByteArray? = null

    @Volatile
    var muted: Boolean = false

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Called from the Rust media thread. Must not block. */
    fun onFrame(frame: ByteArray) {
        // trySend, not send: dropping a frame under load is a click, whereas
        // blocking the media thread stalls the whole receive path.
        incoming.trySend(frame)
    }

    /** Called from the Rust media thread when the peer announces its codec. */
    fun onConfig(config: ByteArray) {
        if (farEndConfig == null) {
            Log.i(TAG, "peer audio config: ${config.size} bytes")
            farEndConfig = config
        }
    }

    fun start(callId: String) {
        if (!running.compareAndSet(false, true)) return
        this.callId = callId
        configureRouting()
        playbackJob = scope.launch { playLoop() }
        if (hasMicPermission()) {
            captureJob = scope.launch { captureLoop(callId) }
        } else {
            Log.w(TAG, "no microphone permission - the call will be receive-only")
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        captureJob?.cancel()
        playbackJob?.cancel()
        captureJob = null
        playbackJob = null
        farEndConfig = null
        callId = null
        restoreRouting()
    }

    // --- Routing ------------------------------------------------------------

    private var previousMode = AudioManager.MODE_NORMAL

    private fun configureRouting() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        previousMode = audio.mode
        // IN_COMMUNICATION is what turns on the platform's echo canceller and
        // routes to the earpiece. Without it a speakerphone call feeds itself
        // back to the other side.
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun restoreRouting() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        audio.mode = previousMode
    }

    // --- Playback -----------------------------------------------------------

    private suspend fun playLoop() {
        // Nothing can be decoded until the peer says how, so wait for it
        // rather than starting a decoder on a guess.
        var config = farEndConfig
        while (config == null && running.get()) {
            kotlinx.coroutines.delay(50)
            config = farEndConfig
        }
        if (config == null) return

        val (sampleRate, channels) = parseAudioSpecificConfig(config)
        Log.i(TAG, "decoding ${sampleRate}Hz x$channels")

        val decoder = runCatching {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                val format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels,
                ).apply {
                    setByteBuffer("csd-0", ByteBuffer.wrap(config))
                }
                configure(format, null, null, 0)
                start()
            }
        }.getOrElse {
            Log.e(TAG, "no AAC decoder", it)
            return
        }

        val track = buildTrack(sampleRate, channels) ?: run {
            decoder.release()
            return
        }
        track.play()

        val info = MediaCodec.BufferInfo()
        try {
            while (running.get()) {
                val frame = incoming.receive()

                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    decoder.getInputBuffer(inIndex)?.apply {
                        clear()
                        put(frame)
                    }
                    decoder.queueInputBuffer(inIndex, 0, frame.size, 0, 0)
                }

                var outIndex = decoder.dequeueOutputBuffer(info, 0)
                while (outIndex >= 0) {
                    val out = decoder.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        val pcm = ByteArray(info.size)
                        out.position(info.offset)
                        out.get(pcm, 0, info.size)
                        track.write(pcm, 0, pcm.size)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    outIndex = decoder.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            if (running.get()) Log.e(TAG, "playback stopped", e)
        } finally {
            runCatching { track.stop(); track.release() }
            runCatching { decoder.stop(); decoder.release() }
        }
    }

    private fun buildTrack(sampleRate: Int, channels: Int): AudioTrack? = runCatching {
        val mask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO
        else AudioFormat.CHANNEL_OUT_MONO
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // VOICE_COMMUNICATION, so the volume rocker controls call
                    // volume and the earpiece is used rather than the speaker.
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(mask)
                    .build()
            )
            // Twice the minimum: one buffer's worth of jitter is normal on a
            // mobile network and underrunning is audible as a click.
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.onFailure { Log.e(TAG, "couldn't open the earpiece", it) }.getOrNull()

    // --- Capture ------------------------------------------------------------

    private suspend fun captureLoop(callId: String) {
        // Mirror whatever the peer is using. Both ends of a FaceTime call run
        // the same profile, so matching theirs is a better bet than picking.
        var config = farEndConfig
        var attempts = 0
        while (config == null && running.get() && attempts < 60) {
            kotlinx.coroutines.delay(50)
            config = farEndConfig
            attempts++
        }
        val (sampleRate, channels) = config?.let { parseAudioSpecificConfig(it) }
            ?: (DEFAULT_SAMPLE_RATE to 1)

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            if (channels >= 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val record = runCatching {
            @Suppress("MissingPermission")
            AudioRecord(
                // VOICE_COMMUNICATION gives us the platform's echo cancel and
                // noise suppression, which a call needs and a recorder doesn't.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                if (channels >= 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
        }.getOrElse {
            Log.e(TAG, "couldn't open the microphone", it)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "microphone didn't initialise")
            record.release()
            return
        }

        val encoder = runCatching {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                val format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels,
                ).apply {
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuffer * 2)
                }
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        }.getOrElse {
            Log.e(TAG, "no AAC encoder", it)
            record.release()
            return
        }

        record.startRecording()
        val pcm = ByteArray(minBuffer)
        val info = MediaCodec.BufferInfo()
        // The codec's own clock, advanced by the samples in each frame. Wall
        // time here makes the far end play the call at the wrong speed.
        var timestamp = 0L

        try {
            while (running.get()) {
                val read = record.read(pcm, 0, pcm.size)
                if (read <= 0) continue

                val inIndex = encoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    encoder.getInputBuffer(inIndex)?.apply {
                        clear()
                        // Muting feeds silence rather than stopping the stream:
                        // a stream that stops looks like a dropped call.
                        if (muted) put(ByteArray(read)) else put(pcm, 0, read)
                    }
                    encoder.queueInputBuffer(inIndex, 0, read, 0, 0)
                }

                var outIndex = encoder.dequeueOutputBuffer(info, 0)
                while (outIndex >= 0) {
                    val out = encoder.getOutputBuffer(outIndex)
                    // The codec-config buffer is the encoder describing itself,
                    // not audio - sending it as a frame is a burst of noise.
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !isConfig) {
                        val frame = ByteArray(info.size)
                        out.position(info.offset)
                        out.get(frame, 0, info.size)
                        val stamp = timestamp
                        scope.launch {
                            runCatching {
                                core.sendCallAudio(callId, frame, stamp.toUInt())
                            }
                        }
                        timestamp += SAMPLES_PER_FRAME
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    outIndex = encoder.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            if (running.get()) Log.e(TAG, "capture stopped", e)
        } finally {
            runCatching { record.stop(); record.release() }
            runCatching { encoder.stop(); encoder.release() }
        }
    }

    private companion object {
        const val TAG = "CallAudio"
        const val DEFAULT_SAMPLE_RATE = 24000
        const val BITRATE = 32000

        /** One AAC frame is 1024 samples, which is what the timestamp counts. */
        const val SAMPLES_PER_FRAME = 1024L

        /**
         * Pulls the sample rate and channel count out of an AudioSpecificConfig.
         *
         * The first five bits are the object type, the next four an index into
         * a fixed table of sample rates, then four bits of channel count. This
         * is a small enough piece of the spec to read directly, and it is the
         * difference between a call that sounds right and one that doesn't.
         */
        val SAMPLE_RATES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350,
        )

        fun parseAudioSpecificConfig(config: ByteArray): Pair<Int, Int> {
            if (config.size < 2) return DEFAULT_SAMPLE_RATE to 1
            val first = config[0].toInt() and 0xFF
            val second = config[1].toInt() and 0xFF
            val frequencyIndex = ((first and 0x07) shl 1) or ((second and 0x80) ushr 7)
            val channels = (second and 0x78) ushr 3
            val rate = SAMPLE_RATES.getOrNull(frequencyIndex) ?: DEFAULT_SAMPLE_RATE
            return rate to channels.coerceIn(1, 2)
        }
    }
}
