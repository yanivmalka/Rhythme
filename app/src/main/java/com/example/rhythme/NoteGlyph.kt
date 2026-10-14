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
        val hollow = note == NoteLength.HALF || note == NoteLength.WHOLE
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

        if (note == NoteLength.DOTTED_QUARTER) {
            drawCircle(color, radius = w * 0.07f, center = Offset(cx + rx + w * 0.26f, cy - ry * 0.1f))
        }
    }
}
