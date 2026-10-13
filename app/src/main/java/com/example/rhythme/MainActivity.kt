package com.example.rhythme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythme.ui.theme.MyApplicationTheme
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var hapticManager: HapticManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hapticManager = HapticManager(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RhythmPlayScreen(hapticManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticManager.stop()
    }
}

@Composable
fun RhythmPlayScreen(hapticManager: HapticManager) {
    var isHolding by remember { mutableStateOf(false) }
    var holdDurationMs by remember { mutableStateOf(0L) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var lightEnabled by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf(0L) }

    LaunchedEffect(isHolding) {
        while (isHolding) {
            holdDurationMs = System.currentTimeMillis() - startTime

            if (vibrationEnabled) {
                // Amplitude scales with hold duration (0-255)
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
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top bar: toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = { soundEnabled = !soundEnabled },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (soundEnabled) Icons.Filled.VolumeMute else Icons.Filled.VolumeOff,
                    contentDescription = "Sound toggle",
                    tint = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = { vibrationEnabled = !vibrationEnabled },
                modifier = Modifier.size(48.dp)
            ) {
                Text(
                    "⚡",
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            IconButton(
                onClick = { lightEnabled = !lightEnabled },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = "Light toggle",
                    tint = if (lightEnabled) Color.Yellow else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Center: big hold pad
        val lightColor = if (lightEnabled && isHolding) {
            // Shift from cool (blue) to warm (orange) based on hold time
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

        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.CenterHorizontally)
                .background(
                    color = lightColor,
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            isHolding = true
                            startTime = System.currentTimeMillis()
                            tryAwaitRelease()
                            isHolding = false
                            hapticManager.stop()
                            holdDurationMs = 0
                        }
                    )
                },
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

        // Bottom: info
        Text(
            text = "Hold the circle. Feel the vibration grow.",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}