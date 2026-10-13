package com.example.rhythme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hapticManager = HapticManager(this)
        audioManager = AudioManager()
        metronomeManager = MetronomeManager(120)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RhythmPlayScreen(hapticManager, audioManager, metronomeManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticManager.stop()
        audioManager.stopTone()
        metronomeManager.release()
        audioManager.release()
    }
}

@Composable
fun RhythmPlayScreen(
    hapticManager: HapticManager,
    audioManager: AudioManager,
    metronomeManager: MetronomeManager
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdDurationMs by remember { mutableStateOf(0L) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var lightEnabled by remember { mutableStateOf(true) }
    var beatEnabled by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf(0L) }
    var bpm by remember { mutableStateOf(120f) }
    var beatPulse by remember { mutableStateOf(0f) }

    // Beat pulse animation
    LaunchedEffect(beatEnabled, bpm) {
        if (beatEnabled) {
            metronomeManager.setBpm(bpm.toInt())
            metronomeManager.start {
                beatPulse = 1f
            }
        } else {
            metronomeManager.stop()
        }
    }

    // Beat pulse decay
    LaunchedEffect(beatPulse) {
        if (beatPulse > 0f) {
            kotlinx.coroutines.delay(100)
            beatPulse *= 0.8f
        }
    }

    // Hold feedback loop
    LaunchedEffect(isHolding) {
        while (isHolding) {
            holdDurationMs = System.currentTimeMillis() - startTime
            val volume = min(1f, holdDurationMs / 4000f)

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

        // Spacer to push circle to center
        Spacer(modifier = Modifier.weight(1f))

        // Center: big hold pad with beat pulse
        val pulseScale = 1f + beatPulse * 0.1f
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
                            holdDurationMs = 0
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Inner animated circle
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
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

        // Bottom: info
        Text(
            text = "Hold to play. Listen to the beat.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}