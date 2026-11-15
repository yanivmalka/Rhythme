package com.example.rhythme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.example.rhythme.ui.theme.Ink
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Where "now" sits across the graph: the past before it, the upcoming guide after it. */
const val GRAPH_NOW_AT = 0.72f

/**
 * A stretch of the guide rhythm: from [at] on, notes of [noteMs] alternate with rests of
 * [restMs]. It lasts until the next anchor, or until [until] when the guide was told to stop.
 * Times are SystemClock.uptimeMillis().
 */
data class GuideAnchor(val at: Long, val noteMs: Long, val restMs: Long, val until: Long = Long.MAX_VALUE) {
    val cycleMs: Long get() = noteMs + restMs
    val active: Boolean get() = noteMs > 0

    /** Start of the first guide note at or after [t]. */
    fun nextNoteStart(t: Long): Long =
        if (t <= at) at else at + ((t - at + cycleMs - 1) / cycleMs) * cycleMs

    /** Start of the guide note nearest to [t]. */
    fun nearestNoteStart(t: Long): Long =
        at + ((t - at).toDouble() / cycleMs).roundToLong().coerceAtLeast(0) * cycleMs
}

/** One press on the pad. [to] is null while the finger is still down; [ratio] = held / target once scored. */
data class PressMark(val from: Long, val to: Long?, val ratio: Float?)

/** The beat as tick marks on the time axis: pulse k is at origin + k * pulseMs, and origin is a downbeat. */
class BeatTicks(val origin: Double, val pulseMs: Double, val pulsesPerBar: Int)

/**
 * Live timeline of the rhythm. The guide is a square wave (up = note, down = rest), always there
 * as a soft background: what should be played. The player's presses are painted over it, colored
 * by how close each one was. Everything scrolls with time: [now] is read while drawing, so the
 * graph moves only while the screen's clock runs. [windowMs] is the time shown across the width;
 * the screen shrinks it as the tempo rises, so the spacing looks alike at every tempo.
 */
@Composable
fun LiveGraph(
    now: () -> Long,
    anchors: List<GuideAnchor>,
    presses: List<PressMark>,
    beats: BeatTicks?,
    windowMs: Long,
    modifier: Modifier = Modifier
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val t0 = now().toDouble()
        val w = size.width
        val h = size.height
        val pxPerMs = w / windowMs.coerceAtLeast(1)
        val nowX = w * GRAPH_NOW_AT
        val tMin = t0 - nowX / pxPerMs
        val tMax = t0 + (w - nowX) / pxPerMs
        // Time runs in the reading direction: left to right, right to left in RTL
        fun x(t: Double): Float {
            val ltr = nowX + ((t - t0) * pxPerMs).toFloat()
            return if (rtl) w - ltr else ltr
        }

        val low = h * 0.80f   // rest level, also the time axis
        val high = h * 0.18f  // note level
        val axis = Ink.copy(alpha = 0.28f)
        val thin = 1.dp.toPx()

        // Axes: time along the bottom, level at the start edge with a tick for "rest" and "note"
        drawLine(axis, Offset(0f, low), Offset(w, low), strokeWidth = thin)
        val edge = if (rtl) w else 0f
        val inward = if (rtl) -1f else 1f
        drawLine(axis, Offset(edge, high - 4.dp.toPx()), Offset(edge, low), strokeWidth = thin)
        drawLine(axis, Offset(edge, high), Offset(edge + inward * 5.dp.toPx(), high), strokeWidth = thin)

        // Beat ticks under the time axis; the first beat of each bar is longer and gold
        if (beats != null && beats.pulseMs > 1.0) {
            var k = ceil((tMin - beats.origin) / beats.pulseMs).toLong()
            while (true) {
                val t = beats.origin + k * beats.pulseMs
                if (t > tMax) break
                val barStart = Math.floorMod(k, beats.pulsesPerBar.toLong()) == 0L
                drawLine(
                    if (barStart) StarGold.copy(alpha = 0.75f) else Ink.copy(alpha = 0.4f),
                    Offset(x(t), low),
                    Offset(x(t), low + (if (barStart) 8.dp else 4.dp).toPx()),
                    strokeWidth = thin
                )
                k++
            }
        }

        // The guide, pass 1: its notes as a soft background, then (pass 2, over the presses) its outline
        val guideFill = Ink.copy(alpha = 0.10f)
        val guideColor = Ink.copy(alpha = 0.55f)
        val guideStroke = 1.5.dp.toPx()
        fun drawGuide(outline: Boolean) {
            anchors.forEachIndexed { i, a ->
                if (!a.active) return@forEachIndexed
                val segStart = maxOf(a.at.toDouble(), tMin)
                val segEnd = minOf(anchors.getOrNull(i + 1)?.at?.toDouble() ?: tMax, a.until.toDouble(), tMax)
                if (segEnd <= segStart) return@forEachIndexed
                var k = floor((segStart - a.at) / a.cycleMs).toLong()
                while (true) {
                    val noteStart = a.at.toDouble() + k * a.cycleMs
                    if (noteStart >= segEnd) break
                    val noteEnd = noteStart + a.noteMs
                    val s = maxOf(noteStart, segStart)
                    val e = minOf(noteEnd, segEnd)
                    if (e > s) {
                        if (!outline) {
                            drawRect(guideFill, topLeft = Offset(minOf(x(s), x(e)), high), size = Size(abs(x(e) - x(s)), low - high))
                        } else {
                            drawLine(guideColor, Offset(x(s), high), Offset(x(e), high), strokeWidth = guideStroke)
                            if (noteStart >= segStart) {
                                drawLine(guideColor, Offset(x(noteStart), low), Offset(x(noteStart), high), strokeWidth = guideStroke)
                            }
                            if (noteEnd <= segEnd) {
                                drawLine(guideColor, Offset(x(noteEnd), high), Offset(x(noteEnd), low), strokeWidth = guideStroke)
                            }
                        }
                    }
                    k++
                }
            }
        }
        drawGuide(outline = false)

        // The player's presses, painted over the background
        for (p in presses) {
            val to = (p.to?.toDouble() ?: t0).coerceAtMost(tMax)
            val from = p.from.toDouble().coerceAtLeast(tMin)
            if (to <= from) continue
            val xa = x(from)
            val xb = x(to)
            drawRect(
                (p.ratio?.let { scoreColor(it) } ?: FillBlue).copy(alpha = 0.7f),
                topLeft = Offset(minOf(xa, xb), high),
                size = Size(abs(xb - xa), low - high)
            )
        }

        drawGuide(outline = true)

        // "Now"
        drawLine(Ink.copy(alpha = 0.7f), Offset(x(t0), 0f), Offset(x(t0), h), strokeWidth = thin)
    }
}
