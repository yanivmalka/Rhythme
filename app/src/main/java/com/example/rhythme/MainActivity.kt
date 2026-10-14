package com.example.rhythme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rhythme.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var hapticManager: HapticManager
    private lateinit var audioManager: AudioManager
    private lateinit var progressManager: ProgressManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hapticManager = HapticManager(this)
        audioManager = AudioManager()
        progressManager = ProgressManager(this)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Play(null)) }
                    val playSettings = remember { PlaySettings() }

                    when (val screen = currentScreen) {
                        is Screen.Play -> {
                            RhythmPlayScreen(
                                playSettings,
                                hapticManager,
                                audioManager,
                                progressManager,
                                screen.lesson,
                                onOpenLessons = { currentScreen = Screen.Lessons },
                                onLessonChange = { lesson -> currentScreen = Screen.Play(lesson) }
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
        audioManager.release()
    }
}

sealed class Screen {
    data class Play(val lesson: Lesson?) : Screen()
    object Lessons : Screen()
}
