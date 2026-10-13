package com.example.rhythme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.math.PI

class AudioManager {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val sampleRate = 44100
    private val frequency = 440.0 // A4 note

    fun startTone(volume: Float = 0.3f) {
        if (isPlaying) return
        isPlaying = true

        Thread {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                audioTrack = AudioTrack(
                    audioAttributes,
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    sampleRate * 2,
                    AudioTrack.MODE_STREAM,
                    android.media.AudioManager.STREAM_MUSIC
                )

                audioTrack?.play()

                val bufferSize = sampleRate / 10 // 100ms chunks
                val audioBuffer = ShortArray(bufferSize)

                var phase = 0.0

                while (isPlaying) {
                    for (i in 0 until bufferSize) {
                        val sample = (sin(phase) * 32767 * volume).toShort()
                        audioBuffer[i] = sample
                        phase += 2.0 * PI * frequency / sampleRate
                        if (phase > 2.0 * PI) phase -= 2.0 * PI
                    }
                    audioTrack?.write(audioBuffer, 0, bufferSize)
                }

                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun updateVolume(volume: Float) {
        // Volume already controlled in tone generation
    }

    fun stopTone() {
        isPlaying = false
    }

    fun release() {
        stopTone()
        audioTrack?.release()
    }
}
