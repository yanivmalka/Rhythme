package com.example.rhythme

/** Standard Italian tempo marks (universal in music, so not translated). */
object Tempo {
    const val MIN = 40
    const val MAX = 180
    const val DEFAULT = 60 // start slow
    const val STEP = 5     // the + / - buttons

    val marks = listOf(
        40 to "Grave",
        50 to "Largo",
        60 to "Lento",
        72 to "Adagio",
        88 to "Andante",
        108 to "Moderato",
        120 to "Allegro",
        144 to "Vivace",
        168 to "Presto",
    )

    /** Name of the mark this tempo falls in (the highest mark at or below it). */
    fun nameFor(bpm: Int): String = marks.last { it.first <= bpm.coerceAtLeast(MIN) }.second

    fun nextMark(bpm: Int): Int = marks.firstOrNull { it.first > bpm }?.first ?: MAX
    fun previousMark(bpm: Int): Int = marks.lastOrNull { it.first < bpm }?.first ?: MIN

    fun clamp(bpm: Int): Int = bpm.coerceIn(MIN, MAX)
}

/**
 * Time signatures. A "pulse" is one click: a quarter in x/4, an eighth in 6/8 (BPM always counts
 * quarters). Accent per pulse in the bar: 2 = strong (beat 1), 1 = medium, 0 = normal.
 */
enum class TimeSignature(
    val label: String,
    val pulsesPerBar: Int,
    val pulsesPerQuarter: Int,
    val accents: IntArray,
    val barQuarters: Float
) {
    TWO_FOUR("2/4", 2, 1, intArrayOf(2, 0), 2f),
    THREE_FOUR("3/4", 3, 1, intArrayOf(2, 0, 0), 3f),
    FOUR_FOUR("4/4", 4, 1, intArrayOf(2, 0, 0, 0), 4f),
    SIX_EIGHT("6/8", 6, 2, intArrayOf(2, 0, 0, 1, 0, 0), 3f);

    fun pulseMs(bpm: Int): Double = 60000.0 / bpm / pulsesPerQuarter

    /** Note values that fit in one bar. */
    fun fits(note: NoteLength): Boolean = note.beats <= barQuarters
}
