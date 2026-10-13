package com.example.rhythme

data class Lesson(
    val id: Int,
    val title: String,
    val description: String,
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
            title = "Steady Beat",
            description = "Feel the constant beat. Just tap along.",
            targetBpm = 120,
            targetNoteLength = NoteLength.QUARTER,
            order = 1
        ),
        Lesson(
            id = 2,
            title = "Quarter Notes",
            description = "Hold for a quarter note (half second at 120 BPM)",
            targetBpm = 120,
            targetNoteLength = NoteLength.QUARTER,
            order = 2
        ),
        Lesson(
            id = 3,
            title = "Half Notes",
            description = "Hold for a half note (one full second)",
            targetBpm = 120,
            targetNoteLength = NoteLength.HALF,
            order = 3
        ),
        Lesson(
            id = 4,
            title = "Whole Notes",
            description = "Hold for two full seconds",
            targetBpm = 120,
            targetNoteLength = NoteLength.WHOLE,
            order = 4
        ),
        Lesson(
            id = 5,
            title = "Dotted Quarters",
            description = "Hold for 0.75 seconds (three-quarter rhythm)",
            targetBpm = 120,
            targetNoteLength = NoteLength.DOTTED_QUARTER,
            order = 5
        ),
        Lesson(
            id = 6,
            title = "Eighth Notes",
            description = "Hold for a quarter second (double speed)",
            targetBpm = 120,
            targetNoteLength = NoteLength.EIGHTH,
            order = 6
        )
    )

    fun getLessonById(id: Int): Lesson? = lessons.find { it.id == id }
}
