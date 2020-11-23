package com.haishinkit.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.haishinkit.codec.MediaCodec
import com.haishinkit.net.NetStream
import org.apache.commons.lang3.builder.ToStringBuilder
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An audio source that captures a microphone by the AudioRecord api.
 */
class AudioRecordSource() : AudioSource {
    internal class Callback(private val audio: AudioRecordSource) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: android.media.MediaCodec, index: Int) {
            try {
                val inputBuffer = codec.getInputBuffer(index) ?: return
                val result = audio.read(inputBuffer)
                if (0 <= result) {
                    codec.queueInputBuffer(index, 0, result, audio.currentPresentationTimestamp, 0)
                }
            } catch (e: IllegalStateException) {
                Log.w(javaClass.name, e)
            }
        }
    }

    var channel = DEFAULT_CHANNEL
    var audioSource = DEFAULT_AUDIO_SOURCE
    var sampleRate = DEFAULT_SAMPLE_RATE
    override var stream: NetStream? = null
    override val isRunning = AtomicBoolean(false)

    var minBufferSize: Int = -1
        get() {
            if (field == -1) {
                field = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
            }
            return field
        }

    var audioRecord: AudioRecord? = null
        get() {
            if (field == null) {
                if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
                    field = AudioRecord.Builder()
                        .setAudioSource(audioSource)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(encoding)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channel)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufferSize)
                        .build()
                } else {
                    field = AudioRecord(
                        audioSource,
                        sampleRate,
                        channel,
                        encoding,
                        minBufferSize
                    )
                }
            }
            return field
        }

    var currentPresentationTimestamp: Long = 0L
        private set

    private var encoding = DEFAULT_ENCODING
    private var sampleCount = 1024

    override fun setUp() {
        stream?.audioCodec?.callback = Callback(this)
    }

    override fun tearDown() {
        stream?.audioCodec?.callback = null
    }

    override fun startRunning() {
        if (isRunning.get()) return
        currentPresentationTimestamp = 0
        audioRecord?.startRecording()
        isRunning.set(true)
    }

    override fun stopRunning() {
        if (!isRunning.get()) return
        audioRecord?.stop()
        isRunning.set(false)
    }

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this)
    }

    private fun read(audioBuffer: ByteBuffer): Int {
        val result = audioRecord?.read(audioBuffer, sampleCount * 2) ?: -1
        if (0 <= result) {
            currentPresentationTimestamp += timestamp(result / 2)
        } else {
            val error = when (result) {
                AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                AudioRecord.ERROR -> "ERROR"
                else -> "ERROR($result)"
            }
            Log.w(TAG, error)
        }
        return result
    }

    private fun timestamp(sampleCount: Int): Long {
        return (1000000.0F * (sampleCount.toFloat() / sampleRate.toFloat())).toLong()
    }

    companion object {
        const val DEFAULT_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val DEFAULT_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.CAMCORDER

        private val TAG = AudioRecordSource::class.java.simpleName
    }
}
