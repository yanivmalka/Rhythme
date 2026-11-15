package com.example.rhythme.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

/**
 * Which theme is showing. The app's own colors (ring, ink, score colors...) are drawn by hand,
 * outside Material's color scheme, so they read this to pick a shade that stands out on the
 * current background: bright on dark, deep on light.
 */
object Palette {
    var light by mutableStateOf(false)

    /** [onDark] in the dark theme, [onLight] in the light one. */
    fun pick(onDark: Color, onLight: Color): Color = if (light) onLight else onDark
}

/** Lines, glyphs and marks drawn straight on the background. */
val Ink: Color get() = Palette.pick(Color.White, Color(0xFF14202E))
