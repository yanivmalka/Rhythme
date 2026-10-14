package com.example.rhythme

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticManager(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?: context.getSystemService(Vibrator::class.java)!!
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)!!
    }

    fun vibrate(durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // createOneShot throws on 0: valid values are DEFAULT_AMPLITUDE or 1..255
            val safeAmplitude = when {
                amplitude == VibrationEffect.DEFAULT_AMPLITUDE -> amplitude
                !vibrator.hasAmplitudeControl() -> VibrationEffect.DEFAULT_AMPLITUDE
                else -> amplitude.coerceIn(1, 255)
            }
            val effect = VibrationEffect.createOneShot(durationMs, safeAmplitude)
            vibrator.cancel()
            vibrator.vibrate(effect)
        }
    }

    /** False on on/off-only motors (e.g. Galaxy A24): every vibration is full strength there. */
    val hasAmplitudeControl: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()

    private var pulseFrom = 0L
    private var pulseUntil = 0L
    private var holdActive = false
    private var holdGeneration = 0

    /** Short, firm tap on each beat. Skipped during a hold: the hold pattern contains its own ticks. */
    fun tick() {
        if (holdActive || SystemClock.uptimeMillis() < pulseUntil) return
        vibrate(25, 200)
    }

    /**
     * Plays a whole hold as one waveform, so timing doesn't depend on the UI thread:
     * a buzz throughout, a gap with a firm tick at each beat, and a double-buzz release
     * pulse at the target. All times are SystemClock.uptimeMillis().
     */
    fun startHold(startAt: Long, targetMs: Long, beatTimes: List<Long>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val step = 10L
        val horizon = (targetMs * 2 + 1500).coerceAtMost(15_000)
        val grid = IntArray((horizon / step).toInt()) { i ->
            40 + (80 * (i * step).toFloat() / targetMs).toInt().coerceAtMost(80) // swelling buzz
        }
        fun set(from: Long, to: Long, amplitude: Int) {
            for (i in (from / step).coerceAtLeast(0)..((to - 1) / step).coerceAtMost(grid.size - 1L)) {
                grid[i.toInt()] = amplitude
            }
        }
        for (beat in beatTimes) {
            val at = beat - startAt
            set(at - 90, at + 70, 0)  // gap around the beat...
            set(at, at + 25, 255)     // ...with the tick in it
        }
        val t = targetMs // release pulse: gap, buzz, gap, buzz, gap
        set(t - 60, t + 260, 0)
        set(t, t + 60, 255)
        set(t + 130, t + 190, 255)

        // Run-length encode the grid into waveform segments
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        for (a in grid) {
            if (amplitudes.isNotEmpty() && amplitudes.last() == a) timings[timings.lastIndex] += step
            else { timings += step; amplitudes += a }
        }

        pulseFrom = startAt + t
        pulseUntil = startAt + t + 190
        holdActive = true
        holdGeneration++
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1))
    }

    /**
     * Ends the hold. If the release lands during the release pulse, returns how long to wait
     * before calling [stopHold] so the pulse isn't cut off; otherwise stops now and returns 0.
     */
    fun endHold(now: Long): Pair<Long, Int> {
        holdActive = false
        if (now in (pulseFrom - 60) until pulseUntil) return (pulseUntil - now) to holdGeneration
        vibrator.cancel()
        return 0L to holdGeneration
    }

    /** Delayed stop from [endHold]; ignored if a new hold has started since. */
    fun stopHold(generation: Int) {
        if (generation == holdGeneration && !holdActive) vibrator.cancel()
    }

    fun stop() {
        holdActive = false
        vibrator.cancel()
    }
}
