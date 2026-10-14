package com.example.rhythme

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs

class ProgressManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rhythme_progress", Context.MODE_PRIVATE)

    fun recordAttempt(lessonId: Int, accuracy: Float) {
        val currentProgress = getProgress(lessonId)

        val newAttempts = currentProgress.attempts + 1
        val newBestAccuracy = maxOf(currentProgress.bestAccuracy, accuracy)
        val newStreak = if (accuracy >= 0.8f) currentProgress.streak + 1 else 0

        val isMastered = newStreak >= 3 // Master after 3 good attempts (80%+)
        val masteredDate = if (isMastered && !currentProgress.isMastered) System.currentTimeMillis() else currentProgress.masteredDate

        val progress = LessonProgress(
            lessonId = lessonId,
            attempts = newAttempts,
            bestAccuracy = newBestAccuracy,
            isMastered = isMastered,
            masteredDate = masteredDate,
            streak = newStreak
        )

        saveProgress(progress)
    }

    fun getProgress(lessonId: Int): LessonProgress {
        val key = "lesson_$lessonId"
        val attempts = prefs.getInt("${key}_attempts", 0)
        val bestAccuracy = prefs.getFloat("${key}_bestAccuracy", 0f)
        val isMastered = prefs.getBoolean("${key}_isMastered", false)
        val masteredDate = prefs.getLong("${key}_masteredDate", 0).takeIf { it > 0 }
        val streak = prefs.getInt("${key}_streak", 0)

        return LessonProgress(
            lessonId = lessonId,
            attempts = attempts,
            bestAccuracy = bestAccuracy,
            isMastered = isMastered,
            masteredDate = masteredDate,
            streak = streak
        )
    }

    fun getAllProgress(): List<LessonProgress> {
        return LessonCurriculum.lessons.map { getProgress(it.id) }
    }

    // Scored relative to the note length so short and long notes are judged equally:
    // within 5% of target = 100%, falling linearly to 0% at 45% off.
    // The 80% mastery bar therefore means landing within ~13% of the target.
    fun calculateAccuracy(targetDurationMs: Long, actualDurationMs: Long): Float {
        if (targetDurationMs <= 0) return 0f
        // Forgive touchscreen timing noise first, so very short notes (sixteenths) stay fair
        val diff = (abs(targetDurationMs - actualDurationMs) - TOUCH_NOISE_MS).coerceAtLeast(0)
        val error = diff.toFloat() / targetDurationMs
        return (1f - (error - 0.05f) / 0.4f).coerceIn(0f, 1f)
    }

    private companion object {
        const val TOUCH_NOISE_MS = 12L
    }

    private fun saveProgress(progress: LessonProgress) {
        val key = "lesson_${progress.lessonId}"
        prefs.edit().apply {
            putInt("${key}_attempts", progress.attempts)
            putFloat("${key}_bestAccuracy", progress.bestAccuracy)
            putBoolean("${key}_isMastered", progress.isMastered)
            putLong("${key}_masteredDate", progress.masteredDate ?: 0L)
            putInt("${key}_streak", progress.streak)
            apply()
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
