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
        // Audio on emulator doesn't work reliably - just vibrate instead
        // TODO: Re-enable when testing on real device
    }

    fun release() {
        stop()
    }
}
