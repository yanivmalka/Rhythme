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

    private var pulseUntil = 0L

    /** Short, firm tap on each beat. Skipped while a release pulse plays (they often coincide). */
    fun tick() {
        if (SystemClock.uptimeMillis() < pulseUntil) return
        vibrate(25, 200)
    }

    /** Double buzz: the hold has reached its target, time to release. Feels distinct from a beat tick. */
    fun pulse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pulseUntil = SystemClock.uptimeMillis() + 190
            vibrator.cancel()
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 60), -1))
        }
    }

    fun stop() {
        vibrator.cancel()
    }
}
