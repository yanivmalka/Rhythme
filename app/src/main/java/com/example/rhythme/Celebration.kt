package com.example.rhythme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val StarGold = Color(0xFFFFC94D)

/** Three stars, earned ones filled gold. */
@Composable
fun StarsRow(stars: Int, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(LessonProgress.MAX_STARS) { i ->
            val earned = i < stars
            Icon(
                imageVector = if (earned) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (earned) StarGold else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * Full-screen "wow" when a star is earned: stars spring in one by one over a slowly
 * turning sunburst, with a burst of gold sparks. Dismisses itself, or on tap.
 */
@Composable
fun StarCelebration(stars: Int, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val starScales = remember { List(LessonProgress.MAX_STARS) { Animatable(0f) } }
    val spin = rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rays"
    )
    // Fixed random sparks for this celebration
    val sparks = remember {
        val r = Random(stars * 31 + 7)
        List(36) { Triple(r.nextFloat() * 360f, 0.55f + r.nextFloat() * 0.6f, 3f + r.nextFloat() * 5f) }
    }

    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(250)) }
        launch { burst.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        starScales.forEachIndexed { i, scale ->
            launch {
                delay(150L + i * 160L)
                scale.animateTo(if (i < stars) 1f else 0.85f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
            }
        }
        delay(2600)
        fade.animateTo(0f, tween(350))
        done()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val maxR = size.minDimension * 0.55f
            // Sunburst rays
            rotate(spin.value, c) {
                repeat(12) { k ->
                    val a = Math.toRadians(k * 30.0)
                    val end = Offset(c.x + cos(a).toFloat() * maxR, c.y + sin(a).toFloat() * maxR)
                    drawLine(
                        brush = Brush.linearGradient(listOf(StarGold.copy(alpha = 0.35f), Color.Transparent), c, end),
                        start = c, end = end, strokeWidth = 46f, cap = StrokeCap.Round
                    )
                }
            }
            // Spark burst
            val p = burst.value
            sparks.forEach { (angle, reach, radius) ->
                val a = Math.toRadians(angle.toDouble())
                val d = maxR * reach * p
                drawCircle(
                    color = StarGold.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                    radius = radius * (1.2f - p * 0.6f),
                    center = Offset(c.x + cos(a).toFloat() * d, c.y + sin(a).toFloat() * d)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                starScales.forEachIndexed { i, scale ->
                    val earned = i < stars
                    Icon(
                        imageVector = if (earned) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = if (earned) StarGold else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(if (i == stars - 1) 96.dp else 72.dp) // the new star is the biggest
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    )
                }
            }
            Text(
                text = stringResource(
                    when (stars) {
                        1 -> R.string.star_earned_1
                        2 -> R.string.star_earned_2
                        else -> R.string.star_earned_3
                    }
                ),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .alpha(burst.value)
            )
        }
    }
}
