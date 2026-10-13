package com.example.rhythme

import kotlin.math.sin
import kotlin.math.PI
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Timer
import java.util.TimerTask

class MetronomeManager(private var _bpm: Int = 120) {
    private var timer: Timer? = null
    private var isRunning = false
    private val sampleRate = 44100
    private val beepFrequency = 800.0 // Higher pitch for beat beep
    private var onBeatCallback: (() -> Unit)? = null

    fun setBpm(newBpm: Int) {
        _bpm = newBpm.coerceIn(40, 180)
    }

    fun start(onBeat: (() -> Unit)? = null) {
        if (isRunning) return
        isRunning = true
        onBeatCallback = onBeat

        val beatIntervalMs = (60000 / _bpm).toLong()

        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                playBeep()
                onBeatCallback?.invoke()
            }
        }, 0, beatIntervalMs)
    }

    fun stop() {
        isRunning = false
        timer?.cancel()
        timer = null
    }

    private fun playBeep() {
        Thread {
            try {
                val beepDurationMs = 100L
                val bufferSize = (sampleRate * beepDurationMs / 1000).toInt()

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioTrack = AudioTrack(
                    audioAttributes,
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    bufferSize * 2,
                    AudioTrack.MODE_STREAM,
                    android.media.AudioManager.STREAM_MUSIC
                )

                audioTrack.play()

                val audioBuffer = ShortArray(bufferSize)
                var phase = 0.0

                for (i in 0 until bufferSize) {
                    val sample = (sin(phase) * 32767 * 0.3).toInt().toShort()
                    audioBuffer[i] = sample
                    phase += 2.0 * PI * beepFrequency / sampleRate
                }

                audioTrack.write(audioBuffer, 0, bufferSize)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun release() {
        stop()
    }
}
