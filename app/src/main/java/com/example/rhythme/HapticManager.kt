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

    private var holdActive = false

    /** Tap on each beat, longer when accented (beat 1 of the bar). Skipped during a hold: the hold pattern owns the motor. */
    fun tick(accent: Int = 0) {
        if (holdActive) return
        vibrate(tickMs(accent), 255)
    }

    private fun tickMs(accent: Int): Long = when (accent) { 2 -> 50L; 1 -> 35L; else -> 25L }

    /** Demo mode: one firm tap where each note of the guide starts. */
    fun cue() {
        if (holdActive) return
        vibrate(45, 255)
    }

    /** Demo mode: the player's own press, two quick light taps, unlike the guide's single firm one. */
    fun touch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || holdActive) return
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 45, 14), -1))
    }

    /**
     * Plays a hold as one looping waveform, so timing doesn't depend on the UI thread. Every cycle
     * of [cycleMs] (the note's length) starts weak and ends strong, then starts over: the same
     * cycle as the ring, the pad's fill and the tone. Motors without amplitude control pulse
     * slowly at first and faster toward the end instead.
     * [playFrom] > [startAt] rejoins a hold already in progress (e.g. the tempo changed).
     * All times are SystemClock.uptimeMillis().
     */
    fun startHold(startAt: Long, cycleMs: Long, playFrom: Long = startAt) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || cycleMs <= 0) return
        val cycle = if (hasAmplitudeControl) rampCycle(cycleMs) else pulseCycle(cycleMs)
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()

        // Joining mid-cycle: the rest of the cycle in progress is played once...
        val offset = Math.floorMod(playFrom - startAt, cycleMs)
        if (offset > 0) {
            var at = 0L
            for ((ms, amplitude) in cycle) {
                val end = at + ms
                if (end > offset) {
                    timings += end - maxOf(at, offset)
                    amplitudes += amplitude
                }
                at = end
            }
        }
        // ...then whole cycles repeat until release
        val repeatFrom = timings.size
        for ((ms, amplitude) in cycle) {
            timings += ms
            amplitudes += amplitude
        }

        holdActive = true
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), repeatFrom))
    }

    /** One cycle with amplitude control: a buzz that grows from faint to full, then a breath of silence. */
    private fun rampCycle(cycleMs: Long): List<Pair<Long, Int>> {
        val gap = (cycleMs / 8).coerceIn(10L, 40L).coerceAtMost(cycleMs / 2)
        val buzz = cycleMs - gap
        val steps = (buzz / 20).toInt().coerceIn(1, 60)
        val stepMs = buzz / steps
        val out = ArrayList<Pair<Long, Int>>(steps + 1)
        for (i in 0 until steps) {
            val p = (i + 1f) / steps
            val ms = if (i == steps - 1) buzz - stepMs * (steps - 1) else stepMs // the last step takes the remainder
            out += ms to (20 + 235 * p * kotlin.math.sqrt(p)).toInt().coerceIn(1, 255)
        }
        if (gap > 0) out += gap to 0
        return out
    }

    /** One cycle on an on/off motor: short taps, far apart at first and closing in toward the end. */
    private fun pulseCycle(cycleMs: Long): List<Pair<Long, Int>> {
        val on = 25L
        val fastGap = 25L
        val slowGap = (cycleMs / 4).coerceIn(60L, 260L)
        val out = ArrayList<Pair<Long, Int>>()
        var t = 0L
        while (cycleMs - t >= on + fastGap) {
            out += on to 255
            t += on
            val gap = (slowGap + (fastGap - slowGap) * t.toFloat() / cycleMs).toLong().coerceIn(fastGap, cycleMs - t)
            out += gap to 0
            t += gap
        }
        if (out.isEmpty()) return listOf(cycleMs to 255) // too short for taps: one buzz
        if (t < cycleMs) out[out.lastIndex] = (out.last().first + cycleMs - t) to 0
        return out
    }

    /** Ends the hold's vibration. */
    fun endHold() {
        holdActive = false
        vibrator.cancel()
    }

    /** Celebration: three quick taps and a long one, rising like a fanfare. */
    fun celebrate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        holdActive = false
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40, 70, 40, 90, 220), -1))
    }

    fun stop() {
        holdActive = false
        vibrator.cancel()
    }
}
