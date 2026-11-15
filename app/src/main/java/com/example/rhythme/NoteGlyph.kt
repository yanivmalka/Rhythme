package com.example.rhythme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a note value in standard notation. Drawn with vector paths rather than
 * Unicode music symbols, which many phones have no font for. Never mirrored in RTL.
 */
@Composable
fun NoteGlyph(note: NoteLength, color: Color, modifier: Modifier = Modifier, height: Dp = 32.dp) {
    Canvas(modifier.size(width = height * 0.75f, height = height)) {
        val w = size.width
        val h = size.height
        val hollow = note == NoteLength.HALF || note == NoteLength.DOTTED_HALF || note == NoteLength.WHOLE
        val hasStem = note != NoteLength.WHOLE

        val rx = w * if (note == NoteLength.WHOLE) 0.34f else 0.28f
        val ry = rx * 0.68f
        val cx = if (note == NoteLength.WHOLE) w * 0.45f else w * 0.36f
        val cy = if (hasStem) h * 0.80f else h * 0.55f

        rotate(degrees = -20f, pivot = Offset(cx, cy)) {
            val topLeft = Offset(cx - rx, cy - ry)
            val ovalSize = Size(rx * 2, ry * 2)
            if (hollow) {
                drawOval(color, topLeft, ovalSize, style = Stroke(width = w * 0.09f))
            } else {
                drawOval(color, topLeft, ovalSize)
            }
        }

        if (hasStem) {
            val stemX = cx + rx * 0.88f
            val stemTop = h * 0.06f
            drawLine(color, Offset(stemX, cy - ry * 0.2f), Offset(stemX, stemTop), strokeWidth = w * 0.08f)
            val flags = when (note) {
                NoteLength.EIGHTH -> 1
                NoteLength.SIXTEENTH -> 2
                else -> 0
            }
            for (f in 0 until flags) {
                val top = stemTop + f * h * 0.17f
                val flag = Path().apply {
                    moveTo(stemX, top)
                    cubicTo(stemX + w * 0.05f, top + h * 0.18f, stemX + w * 0.42f, top + h * 0.22f, stemX + w * 0.26f, top + h * 0.46f)
                }
                drawPath(flag, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
            }
        }

        if (note == NoteLength.DOTTED_QUARTER || note == NoteLength.DOTTED_HALF) {
            drawCircle(color, radius = w * 0.07f, center = Offset(cx + rx + w * 0.26f, cy - ry * 0.1f))
        }
    }
}

/**
 * Draws the rest that lasts as long as [note], in standard notation. Like [NoteGlyph] it is
 * drawn with vector paths and never mirrored in RTL.
 */
@Composable
fun RestGlyph(note: NoteLength, color: Color, modifier: Modifier = Modifier, height: Dp = 32.dp) {
    Canvas(modifier.size(width = height * 0.75f, height = height)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.42f
        val stroke = w * 0.1f
        var dotAt = Offset(cx + w * 0.38f, h * 0.44f)

        when (note) {
            NoteLength.WHOLE, NoteLength.HALF, NoteLength.DOTTED_HALF -> {
                // A block on a staff line: hanging below it = whole rest, sitting on it = half rest
                val lineY = h * 0.5f
                val blockH = h * 0.13f
                drawLine(color, Offset(cx - w * 0.38f, lineY), Offset(cx + w * 0.38f, lineY), strokeWidth = stroke * 0.5f)
                val top = if (note == NoteLength.WHOLE) lineY else lineY - blockH
                drawRect(color, Offset(cx - w * 0.22f, top), Size(w * 0.44f, blockH))
                dotAt = Offset(cx + w * 0.38f, lineY - blockH * 0.5f)
            }
            NoteLength.QUARTER, NoteLength.DOTTED_QUARTER -> {
                // The quarter rest's zigzag with a small hook at the bottom
                val squiggle = Path().apply {
                    moveTo(cx - w * 0.10f, h * 0.16f)
                    lineTo(cx + w * 0.14f, h * 0.36f)
                    lineTo(cx - w * 0.10f, h * 0.52f)
                    lineTo(cx + w * 0.14f, h * 0.68f)
                    cubicTo(cx - w * 0.20f, h * 0.60f, cx - w * 0.26f, h * 0.82f, cx + w * 0.04f, h * 0.86f)
                }
                drawPath(squiggle, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            NoteLength.EIGHTH, NoteLength.SIXTEENTH -> {
                // A slanted stem with one hook (eighth) or two (sixteenth), each ending in a dot
                val top = Offset(cx + w * 0.20f, h * 0.26f)
                val bottom = Offset(cx - w * 0.06f, h * 0.80f)
                drawLine(color, top, bottom, strokeWidth = stroke, cap = StrokeCap.Round)
                val hooks = if (note == NoteLength.SIXTEENTH) 2 else 1
                for (i in 0 until hooks) {
                    val f = i * 0.36f
                    val on = Offset(top.x + (bottom.x - top.x) * f, top.y + (bottom.y - top.y) * f)
                    val dot = Offset(on.x - w * 0.34f, on.y + h * 0.03f)
                    drawLine(color, on, dot, strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
                    drawCircle(color, radius = w * 0.085f, center = dot)
                }
            }
        }

        if (note == NoteLength.DOTTED_QUARTER || note == NoteLength.DOTTED_HALF) {
            drawCircle(color, radius = w * 0.07f, center = dotAt)
        }
    }
}
