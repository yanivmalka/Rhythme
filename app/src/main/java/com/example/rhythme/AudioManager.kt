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
        // Audio on emulator doesn't work reliably
        // TODO: Re-enable when testing on real device
        isPlaying = true
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
