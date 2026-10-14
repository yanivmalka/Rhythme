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

enum class NoteLength(val durationMs: Long) {
    QUARTER(500),      // 0.5s at 120 BPM
    EIGHTH(250),       // 0.25s at 120 BPM
    HALF(1000),        // 1s at 120 BPM
    WHOLE(2000),       // 2s at 120 BPM
    DOTTED_QUARTER(750) // 0.75s (1.5x quarter)
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
            targetBpm = 120,
            targetNoteLength = NoteLength.QUARTER,
            order = 1
        ),
        Lesson(
            id = 2,
            titleRes = R.string.lesson_quarter_title,
            descriptionRes = R.string.lesson_quarter_desc,
            targetBpm = 120,
            targetNoteLength = NoteLength.QUARTER,
            order = 2
        ),
        Lesson(
            id = 3,
            titleRes = R.string.lesson_half_title,
            descriptionRes = R.string.lesson_half_desc,
            targetBpm = 120,
            targetNoteLength = NoteLength.HALF,
            order = 3
        ),
        Lesson(
            id = 4,
            titleRes = R.string.lesson_whole_title,
            descriptionRes = R.string.lesson_whole_desc,
            targetBpm = 120,
            targetNoteLength = NoteLength.WHOLE,
            order = 4
        ),
        Lesson(
            id = 5,
            titleRes = R.string.lesson_dotted_quarter_title,
            descriptionRes = R.string.lesson_dotted_quarter_desc,
            targetBpm = 120,
            targetNoteLength = NoteLength.DOTTED_QUARTER,
            order = 5
        ),
        Lesson(
            id = 6,
            titleRes = R.string.lesson_eighth_title,
            descriptionRes = R.string.lesson_eighth_desc,
            targetBpm = 120,
            targetNoteLength = NoteLength.EIGHTH,
            order = 6
        )
    )

    fun getLessonById(id: Int): Lesson? = lessons.find { it.id == id }
}
