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
     * a buzz with a gap + firm tick at each beat, a double-buzz release pulse at the target,
     * a short silence, then a loop that keeps buzzing (with beat ticks) for as long as the
     * hold lasts. All times are SystemClock.uptimeMillis(); [beatIntervalMs] is null when the
     * beat isn't playing.
     */
    fun startHold(startAt: Long, targetMs: Long, beatTimes: List<Long>, beatIntervalMs: Double?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val step = 10L
        val t = targetMs
        val afterPulse = t + 260 // release pulse plus a short silence

        // The loop starts at the first beat gap after the pulse (or right after it without a beat)
        val loopStart = if (beatIntervalMs != null) {
            beatTimes.map { it - startAt - BEAT_GAP_BEFORE }.firstOrNull { it >= afterPulse } ?: afterPulse
        } else afterPulse

        // Part 1, played once: swelling buzz, beat gaps + ticks, release pulse
        val grid = IntArray(((loopStart + step - 1) / step).toInt()) { i ->
            40 + (80 * (i * step).toFloat() / targetMs).toInt().coerceAtMost(80)
        }
        fun set(from: Long, to: Long, amplitude: Int) {
            if (grid.isEmpty()) return
            for (i in (from / step).coerceAtLeast(0)..((to - 1) / step).coerceAtMost(grid.size - 1L)) {
                grid[i.toInt()] = amplitude
            }
        }
        for (beat in beatTimes) {
            val at = beat - startAt
            set(at - BEAT_GAP_BEFORE, at + BEAT_GAP_AFTER, 0) // gap around the beat...
            set(at, at + TICK_MS, 255)                        // ...with the tick in it
        }
        set(t - 60, afterPulse, 0) // pulse: gap, buzz, gap, buzz, silence
        set(t, t + 60, 255)
        set(t + 130, t + 190, 255)

        // Part 2, repeated until release: one beat of buzz with its gap + tick (or plain buzz)
        val loop = if (beatIntervalMs != null) {
            val period = beatIntervalMs.toLong().coerceAtLeast(BEAT_GAP_BEFORE + BEAT_GAP_AFTER + step)
            IntArray((period / step).toInt()) { i ->
                val ms = i * step
                when {
                    ms in BEAT_GAP_BEFORE until BEAT_GAP_BEFORE + TICK_MS -> 255 // the beat tick
                    ms < BEAT_GAP_BEFORE + BEAT_GAP_AFTER -> 0                   // gap around it
                    else -> 120
                }
            }
        } else IntArray(50) { 120 } // 500 ms of steady buzz

        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        fun encode(part: IntArray) { // run-length encode, never merging across the two parts
            var first = true
            for (a in part) {
                if (!first && amplitudes.last() == a) timings[timings.lastIndex] += step
                else { timings += step; amplitudes += a }
                first = false
            }
        }
        encode(grid)
        val repeatFrom = timings.size
        encode(loop)

        pulseFrom = startAt + t
        pulseUntil = startAt + t + 190
        holdActive = true
        holdGeneration++
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), repeatFrom))
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

    /** Celebration: three quick taps and a long one, rising like a fanfare. */
    fun celebrate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        holdActive = false
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40, 70, 40, 90, 220), -1))
    }

    private companion object {
        const val BEAT_GAP_BEFORE = 90L
        const val BEAT_GAP_AFTER = 70L
        const val TICK_MS = 25L
    }

    fun stop() {
        holdActive = false
        vibrator.cancel()
    }
}
