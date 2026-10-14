package com.example.rhythme

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs

class ProgressManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rhythme_progress", Context.MODE_PRIVATE)

    /**
     * Records one hold. [solo] = the beat had faded off (the player kept the rhythm alone).
     * Returns the updated progress; compare its stars with the previous value to celebrate.
     */
    fun recordAttempt(lessonId: Int, accuracy: Float, solo: Boolean = false): LessonProgress {
        val old = getProgress(lessonId)
        val streak80 = if (accuracy >= 0.8f) old.streak80 + 1 else 0
        val streak90 = if (accuracy >= 0.9f) old.streak90 + 1 else 0
        // Only solo holds count toward the third star; holds with the beat audible don't break it
        val streakSolo = when {
            !solo -> old.streakSolo
            accuracy >= 0.9f -> old.streakSolo + 1
            else -> 0
        }
        val need = LessonProgress.STREAK_FOR_STAR
        var stars = old.stars
        if (stars == 0 && streak80 >= need) stars = 1
        if (stars == 1 && streak90 >= need) stars = 2
        if (stars == 2 && streakSolo >= need) stars = 3

        val progress = old.copy(
            attempts = old.attempts + 1,
            bestAccuracy = maxOf(old.bestAccuracy, accuracy),
            stars = stars,
            streak80 = streak80,
            streak90 = streak90,
            streakSolo = streakSolo,
            masteredDate = if (stars >= 1 && old.stars == 0) System.currentTimeMillis() else old.masteredDate
        )
        saveProgress(progress)
        return progress
    }

    /** Clears one lesson's progress. */
    fun reset(lessonId: Int) {
        val key = "lesson_$lessonId"
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("${key}_") }.forEach { remove(it) }
            apply()
        }
    }

    /** Puts back progress saved before a reset (for Undo). */
    fun restore(progress: LessonProgress) = saveProgress(progress)

    fun getProgress(lessonId: Int): LessonProgress {
        val key = "lesson_$lessonId"
        val attempts = prefs.getInt("${key}_attempts", 0)
        val bestAccuracy = prefs.getFloat("${key}_bestAccuracy", 0f)
        val legacyMastered = prefs.getBoolean("${key}_isMastered", false) // before stars existed
        return LessonProgress(
            lessonId = lessonId,
            attempts = attempts,
            bestAccuracy = bestAccuracy,
            stars = prefs.getInt("${key}_stars", if (legacyMastered) 1 else 0),
            streak80 = prefs.getInt("${key}_streak", 0),
            streak90 = prefs.getInt("${key}_streak90", 0),
            streakSolo = prefs.getInt("${key}_streakSolo", 0),
            masteredDate = prefs.getLong("${key}_masteredDate", 0).takeIf { it > 0 }
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
            putInt("${key}_stars", progress.stars)
            putInt("${key}_streak", progress.streak80)
            putInt("${key}_streak90", progress.streak90)
            putInt("${key}_streakSolo", progress.streakSolo)
            apply()
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
