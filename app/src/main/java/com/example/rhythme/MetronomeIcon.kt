package com.example.rhythme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Classic pyramid metronome with its pendulum (Material icons have none). Tinted like any Icon. */
val MetronomeIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Metronome", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path( // body
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.5f, 2.5f); lineTo(15.5f, 2.5f); lineTo(19.5f, 21.5f); lineTo(4.5f, 21.5f); close()
            moveTo(5.6f, 17f); lineTo(18.4f, 17f) // base
        }
        .path( // pendulum, swung to the right
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 17f); lineTo(16.6f, 5.2f)
        }
        .path(fill = SolidColor(Color.Black)) { // sliding weight on the pendulum
            moveTo(13.4f, 10.0f); lineTo(15.8f, 10.9f); lineTo(15.2f, 12.6f); lineTo(12.8f, 11.7f); close()
        }
        .build()
}
