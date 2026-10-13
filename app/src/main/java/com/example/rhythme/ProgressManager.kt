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

    fun calculateAccuracy(targetDurationMs: Long, actualDurationMs: Long): Float {
        val tolerance = 100L // +/- 100ms tolerance
        val diff = abs(targetDurationMs - actualDurationMs)

        return when {
            diff <= tolerance -> 1.0f
            diff <= tolerance * 2 -> 0.9f
            diff <= tolerance * 3 -> 0.8f
            diff <= tolerance * 4 -> 0.7f
            else -> maxOf(0f, 1.0f - (diff.toFloat() / targetDurationMs.toFloat()))
        }
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
