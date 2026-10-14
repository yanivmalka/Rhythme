package com.example.rhythme

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
            // Layout direction follows the active language. Android can leave it stale after the
            // per-app language is cleared (Hebrew text in a left-to-right layout until restart).
            val locale = LocalConfiguration.current.locales[0]
            val direction = if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL)
                LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
            MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var currentScreen by remember { mutableStateOf<Screen>(Screen.Play(null)) }
                        var lastPlay by remember { mutableStateOf(Screen.Play(null)) } // where "back" returns to
                        val playSettings = remember { PlaySettings(getSharedPreferences("rhythme_settings", MODE_PRIVATE)) }
                        val drawerState = rememberDrawerState(DrawerValue.Closed)
                        val scope = rememberCoroutineScope()

                        fun goPlay(screen: Screen.Play) { lastPlay = screen; currentScreen = screen }

                        // Phone back button: menu closes first, then sub-screens return to play
                        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
                        BackHandler(enabled = !drawerState.isOpen && currentScreen !is Screen.Play) { currentScreen = lastPlay }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            gesturesEnabled = drawerState.isOpen, // edge swipes stay free for the slider and pager
                            drawerContent = {
                                AppDrawerContent(playSettings) { item ->
                                    scope.launch { drawerState.close() }
                                    currentScreen = Screen.Menu(item)
                                }
                            }
                        ) {
                            when (val screen = currentScreen) {
                                is Screen.Play -> {
                                    RhythmPlayScreen(
                                        playSettings,
                                        hapticManager,
                                        audioManager,
                                        progressManager,
                                        screen.lesson,
                                        onOpenLessons = { currentScreen = Screen.Lessons },
                                        onOpenMenu = { scope.launch { drawerState.open() } },
                                        onLessonChange = { lesson -> goPlay(Screen.Play(lesson)) }
                                    )
                                }
                                is Screen.Lessons -> {
                                    LessonScreen(
                                        progressManager,
                                        onLessonSelected = { lesson -> goPlay(Screen.Play(lesson)) },
                                        onBack = { goPlay(Screen.Play(null)) }
                                    )
                                }
                                is Screen.Menu -> {
                                    val back = { currentScreen = lastPlay }
                                    when (screen.item) {
                                        MenuItem.PROFILE -> ProfileScreen(playSettings, progressManager, back)
                                        MenuItem.LANGUAGE -> LanguageScreen(back)
                                        MenuItem.INSTRUMENT -> InstrumentScreen(playSettings, audioManager, back)
                                        MenuItem.ABOUT -> AboutScreen(back)
                                    }
                                }
                            }
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
    data class Menu(val item: MenuItem) : Screen()
}
