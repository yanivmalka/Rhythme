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
