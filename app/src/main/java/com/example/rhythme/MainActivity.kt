package com.example.rhythme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythme.ui.theme.MyApplicationTheme
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var hapticManager: HapticManager
    private lateinit var audioManager: AudioManager
    private lateinit var metronomeManager: MetronomeManager
    private lateinit var progressManager: ProgressManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hapticManager = HapticManager(this)
        audioManager = AudioManager()
        metronomeManager = MetronomeManager(120)
        progressManager = ProgressManager(this)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Play(null)) }

                    when (val screen = currentScreen) {
                        is Screen.Play -> {
                            RhythmPlayScreen(
                                hapticManager,
                                audioManager,
                                metronomeManager,
                                progressManager,
                                screen.lesson,
                                onLessonSelected = { currentScreen = Screen.Lessons },
                                onLessonComplete = { lesson, accuracy ->
                                    progressManager.recordAttempt(lesson.id, accuracy)
                                }
                            )
                        }
                        is Screen.Lessons -> {
                            LessonScreen(
                                progressManager,
                                onLessonSelected = { lesson ->
                                    currentScreen = Screen.Play(lesson)
                                },
                                onBack = { currentScreen = Screen.Play(null) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        audioManager.start()
    }

    override fun onStop() {
        super.onStop()
        audioManager.stopTone()
        audioManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticManager.stop()
        audioManager.stopTone()
        metronomeManager.release()
        audioManager.release()
    }
}

sealed class Screen {
    data class Play(val lesson: Lesson?) : Screen()
    object Lessons : Screen()
}

@Composable
fun RhythmPlayScreen(
    hapticManager: HapticManager,
    audioManager: AudioManager,
    metronomeManager: MetronomeManager,
    progressManager: ProgressManager,
    currentLesson: Lesson?,
    onLessonSelected: () -> Unit,
    onLessonComplete: (Lesson, Float) -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdDurationMs by remember { mutableStateOf(0L) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var lightEnabled by remember { mutableStateOf(true) }
    var beatEnabled by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf(0L) }
    var bpm by remember { mutableStateOf(currentLesson?.targetBpm?.toFloat() ?: 120f) }
    var beatTick by remember { mutableStateOf(0) }
    val ripple = remember { Animatable(1f) }

    // Lesson state: latest values for the long-lived gesture handler, last result, progress
    val lesson by rememberUpdatedState(currentLesson)
    var lastResult by remember { mutableStateOf<Pair<Long, Float>?>(null) } // held ms, accuracy
    var lessonProgress by remember {
        mutableStateOf(currentLesson?.let { progressManager.getProgress(it.id) })
    }
    // Note lengths are defined at 120 BPM; scale them to the current tempo
    fun targetMsFor(l: Lesson): Long = l.targetNoteLength.durationMs * 120 / bpm.toInt()

    // Beat clock on the main thread; restarts cleanly when BPM changes
    LaunchedEffect(beatEnabled, bpm.toInt()) {
        metronomeManager.stop()
        if (beatEnabled) {
            val intervalMs = 60000L / bpm.toInt()
            while (true) {
                beatTick++
                if (soundEnabled) audioManager.playClick()
                kotlinx.coroutines.delay(intervalMs)
            }
        }
    }

    // Smooth ring ripple on each beat (circle itself never moves)
    LaunchedEffect(beatTick) {
        if (beatTick > 0) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing))
        }
    }

    // Hold feedback loop
    LaunchedEffect(isHolding) {
        while (isHolding) {
            holdDurationMs = System.currentTimeMillis() - startTime
            val volume = min(1f, holdDurationMs / 4000f)
            audioManager.updateVolume(0.3f + 0.2f * volume) // tone swells as you hold (tone + click stays < 1.0)

            if (vibrationEnabled) {
                val amplitude = min(255, (holdDurationMs / 10).toInt())
                hapticManager.vibrate(50, amplitude)
            }

            kotlinx.coroutines.delay(50)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = { soundEnabled = !soundEnabled },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (soundEnabled) Icons.Filled.VolumeMute else Icons.Filled.VolumeOff,
                    contentDescription = "Sound toggle",
                    tint = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = { vibrationEnabled = !vibrationEnabled },
                modifier = Modifier.size(40.dp)
            ) {
                Text("⚡", fontSize = 20.sp)
            }

            IconButton(
                onClick = { lightEnabled = !lightEnabled },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = "Light toggle",
                    tint = if (lightEnabled) Color.Yellow else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = { beatEnabled = !beatEnabled },
                modifier = Modifier.size(40.dp)
            ) {
                Text("♪", fontSize = 20.sp, color = if (beatEnabled) Color.Green else MaterialTheme.colorScheme.surfaceVariant)
            }

            IconButton(
                onClick = { onLessonSelected() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = "Lessons",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // BPM Slider - always reserve space, show only when beat enabled
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(60.dp)
                .alpha(if (beatEnabled) 1f else 0.3f)
        ) {
            Text("BPM: ${bpm.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = bpm,
                onValueChange = { if (beatEnabled) bpm = it },
                valueRange = 40f..180f,
                modifier = Modifier.fillMaxWidth(),
                enabled = beatEnabled
            )
        }

        // Lesson banner - fixed height so the layout never shifts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            currentLesson?.let { l ->
                Text(l.title, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                val status = if (lessonProgress?.isMastered == true) "Mastered ✓"
                    else "Streak ${lessonProgress?.streak ?: 0}/3"
                Text(
                    "Target: hold ${String.format("%.2f", targetMsFor(l) / 1000.0)}s  ·  $status",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Spacer to push circle to center
        Spacer(modifier = Modifier.weight(1f))

        // Center: big hold pad with beat pulse
        val lightColor = if (lightEnabled && isHolding) {
            val progress = min(1f, holdDurationMs / 4000f)
            Color(
                red = (0.2f + progress * 0.8f),
                green = (0.5f - progress * 0.2f),
                blue = (0.9f - progress * 0.7f),
                alpha = 1f
            )
        } else {
            Color(red = 0.2f, green = 0.5f, blue = 0.9f)
        }

        // Fixed outer container to prevent layout jumping
        Box(
            modifier = Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            isHolding = true
                            startTime = System.currentTimeMillis()
                            if (soundEnabled) {
                                audioManager.startTone(0.3f)
                            }
                            tryAwaitRelease()
                            isHolding = false
                            hapticManager.stop()
                            audioManager.stopTone()

                            // Record lesson attempt if active, using the exact hold time
                            // (holdDurationMs only refreshes every 50ms)
                            lesson?.let { l ->
                                val heldMs = System.currentTimeMillis() - startTime
                                val accuracy = progressManager.calculateAccuracy(targetMsFor(l), heldMs)
                                onLessonComplete(l, accuracy)
                                lastResult = heldMs to accuracy
                                lessonProgress = progressManager.getProgress(l.id)
                            }

                            holdDurationMs = 0
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Beat ring: expands and fades behind the circle (draw-only, no layout change)
            if (beatEnabled) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            val p = ripple.value
                            scaleX = 1f + 0.15f * p
                            scaleY = 1f + 0.15f * p
                            alpha = 1f - p
                        }
                        .border(width = 4.dp, color = Color(0xFF7FD4FF), shape = CircleShape)
                )
            }

            // Main circle: fixed size, never moves
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(color = lightColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isHolding) "Hold..." else "Press & Hold",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                    Text(
                        text = String.format("%.1f", holdDurationMs / 1000.0) + "s",
                        color = Color.White,
                        fontSize = 28.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Spacer to push info to bottom
        Spacer(modifier = Modifier.weight(1f))

        // Bottom: info, or the last attempt's result during a lesson
        val result = lastResult
        Text(
            text = when {
                currentLesson == null -> "Hold to play. Listen to the beat."
                result == null -> "Hold for the target time, then release."
                else -> "You held ${String.format("%.2f", result.first / 1000.0)}s  ·  ${(result.second * 100).toInt()}%"
            },
            fontSize = 14.sp,
            color = when {
                result == null || currentLesson == null -> MaterialTheme.colorScheme.onSurfaceVariant
                result.second >= 0.8f -> Color(0xFF66DD77)
                result.second >= 0.5f -> Color(0xFFFFC857)
                else -> Color(0xFFFF6B6B)
            }
        )
    }
}