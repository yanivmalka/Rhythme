package com.example.rhythme

import androidx.annotation.StringRes

data class Lesson(
    val id: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val targetBpm: Int,
    val targetNoteLength: NoteLength,
    val order: Int
)

/** Note values measured in beats (a quarter note = one beat), so targets follow the tempo. */
enum class NoteLength(val beats: Float, @StringRes val nameRes: Int) {
    SIXTEENTH(0.25f, R.string.note_sixteenth),
    EIGHTH(0.5f, R.string.note_eighth),
    QUARTER(1f, R.string.note_quarter),
    DOTTED_QUARTER(1.5f, R.string.note_dotted_quarter),
    HALF(2f, R.string.note_half),
    DOTTED_HALF(3f, R.string.note_dotted_half),
    WHOLE(4f, R.string.note_whole);

    fun durationMs(bpm: Int): Long = (beats * 60000f / bpm).toLong()
}

/**
 * Stars (kept once earned), each for 3 holds in a row:
 * 1 = 80%+, 2 = 90%+, 3 = 90%+ with the beat faded off (keeping the rhythm alone).
 */
data class LessonProgress(
    val lessonId: Int,
    val attempts: Int = 0,
    val bestAccuracy: Float = 0f,
    val stars: Int = 0,
    val streak80: Int = 0,
    val streak90: Int = 0,
    val streakSolo: Int = 0,
    val masteredDate: Long? = null
) {
    val isMastered: Boolean get() = stars >= 1

    /** Streak toward the next star (0..STREAK_FOR_STAR), or null when all stars are earned. */
    val nextStarStreak: Int? get() = when (stars) {
        0 -> streak80
        1 -> streak90
        2 -> streakSolo
        else -> null
    }

    companion object {
        const val MAX_STARS = 3
        const val STREAK_FOR_STAR = 3
    }
}

// Lesson curriculum
object LessonCurriculum {
    val lessons = listOf(
        Lesson(
            id = 1,
            titleRes = R.string.lesson_steady_beat_title,
            descriptionRes = R.string.lesson_steady_beat_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.QUARTER,
            order = 1
        ),
        Lesson(
            id = 2,
            titleRes = R.string.lesson_quarter_title,
            descriptionRes = R.string.lesson_quarter_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.QUARTER,
            order = 2
        ),
        Lesson(
            id = 3,
            titleRes = R.string.lesson_half_title,
            descriptionRes = R.string.lesson_half_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.HALF,
            order = 3
        ),
        Lesson(
            id = 4,
            titleRes = R.string.lesson_whole_title,
            descriptionRes = R.string.lesson_whole_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.WHOLE,
            order = 4
        ),
        Lesson(
            id = 5,
            titleRes = R.string.lesson_dotted_quarter_title,
            descriptionRes = R.string.lesson_dotted_quarter_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.DOTTED_QUARTER,
            order = 5
        ),
        Lesson(
            id = 6,
            titleRes = R.string.lesson_eighth_title,
            descriptionRes = R.string.lesson_eighth_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.EIGHTH,
            order = 6
        ),
        Lesson(
            id = 7,
            titleRes = R.string.lesson_sixteenth_title,
            descriptionRes = R.string.lesson_sixteenth_desc,
            targetBpm = 60,
            targetNoteLength = NoteLength.SIXTEENTH,
            order = 7
        )
    )

    fun getLessonById(id: Int): Lesson? = lessons.find { it.id == id }
}
