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
    EIGHTH(0.5f, R.string.note_eighth),
    QUARTER(1f, R.string.note_quarter),
    DOTTED_QUARTER(1.5f, R.string.note_dotted_quarter),
    HALF(2f, R.string.note_half),
    WHOLE(4f, R.string.note_whole);

    fun durationMs(bpm: Int): Long = (beats * 60000f / bpm).toLong()
}

data class LessonProgress(
    val lessonId: Int,
    val attempts: Int = 0,
    val bestAccuracy: Float = 0f,
    val isMastered: Boolean = false,
    val masteredDate: Long? = null,
    val streak: Int = 0
)

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
        )
    )

    fun getLessonById(id: Int): Lesson? = lessons.find { it.id == id }
}
