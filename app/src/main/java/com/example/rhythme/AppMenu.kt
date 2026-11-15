package com.example.rhythme

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Items of the ☰ menu. */
enum class MenuItem(@StringRes val titleRes: Int, val icon: ImageVector) {
    PROFILE(R.string.menu_profile, Icons.Filled.Person),
    LANGUAGE(R.string.menu_language, Icons.Filled.Language),
    INSTRUMENT(R.string.menu_instrument, Icons.Filled.MusicNote),
    ABOUT(R.string.menu_about, Icons.Filled.Info),
}

@Composable
fun AppDrawerContent(settings: PlaySettings, onSelect: (MenuItem) -> Unit) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                settings.profileName.ifBlank { stringResource(R.string.profile_guest) },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        MenuItem.entries.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.titleRes)) },
                icon = { Icon(item.icon, contentDescription = null) },
                selected = false,
                onClick = { onSelect(item) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

/** Common frame for the menu screens: back arrow + title, scrollable content. */
@Composable
private fun MenuPage(@StringRes title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(title), fontSize = 24.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            content = content
        )
    }
}

@Composable
fun ProfileScreen(settings: PlaySettings, progressManager: ProgressManager, onBack: () -> Unit) {
    val all = remember { progressManager.getAllProgress() }
    val lessonCount = LessonCurriculum.lessons.size
    MenuPage(R.string.menu_profile, onBack) {
        OutlinedTextField(
            value = settings.profileName,
            onValueChange = { settings.profileName = it.take(30) },
            label = { Text(stringResource(R.string.profile_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.profile_stats_stars, all.sumOf { it.stars }, lessonCount * LessonProgress.MAX_STARS),
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.profile_stats_lessons, all.count { it.attempts > 0 }, lessonCount), fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.profile_stats_holds, all.sumOf { it.attempts }), fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.profile_cloud_note),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    MenuPage(R.string.menu_language, onBack) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Text(stringResource(R.string.lang_unsupported), fontSize = 15.sp)
            return@MenuPage
        }
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val current = localeManager.applicationLocales.toLanguageTags()
        // (tag, label): "" = follow the phone; Hebrew is "iw" on Android
        val options = listOf(
            "" to stringResource(R.string.lang_system),
            "en" to stringResource(R.string.lang_english),
            "iw" to stringResource(R.string.lang_hebrew),
        )
        options.forEach { (tag, label) ->
            val selected = if (tag.isEmpty()) current.isEmpty() else current.startsWith(tag) ||
                (tag == "iw" && current.startsWith("he"))
            ChoiceRow(label, selected) {
                // Android restarts the screen in the new language
                localeManager.applicationLocales =
                    if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstrumentScreen(settings: PlaySettings, audioManager: AudioManager, hapticManager: HapticManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun preview() { // short preview of the current instrument and note
        audioManager.instrument = settings.instrument
        audioManager.frequency = PITCHES_HZ[settings.pitchIndex]
        audioManager.toneLevel = settings.toneVolume / 100f
        previewJob?.cancel()
        previewJob = scope.launch {
            audioManager.startTone(0.35f)
            delay(700)
            audioManager.stopTone()
        }
    }
    MenuPage(R.string.menu_instrument, onBack) {
        Text(
            stringResource(R.string.instrument_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Instrument.entries.forEach { instrument ->
            ChoiceRow(stringResource(instrument.nameRes), settings.instrument == instrument) {
                settings.instrument = instrument
                preview()
            }
        }

        // The note (pitch) the instrument plays while holding
        Text(
            stringResource(R.string.note_pitch_title),
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
        )
        PitchChips(settings.pitchIndex) {
            settings.pitchIndex = it
            preview()
        }

        LevelSlider(R.string.volume_tone, settings.toneVolume, { settings.toneVolume = it }) { preview() }

        // Successive notes can walk up and down a scale that starts on that note
        Text(
            stringResource(R.string.scale_title),
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 28.dp)
        )
        Text(
            stringResource(R.string.scale_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Scale.entries.forEach { scale ->
            ChoiceRow(stringResource(scale.nameRes), settings.scale == scale) {
                settings.scale = scale
                // Hear it: once up the scale
                audioManager.instrument = settings.instrument
                previewJob?.cancel()
                previewJob = scope.launch {
                    try {
                        for (step in scale.semitones.indices) {
                            audioManager.frequency = scale.hz(PITCHES_HZ[settings.pitchIndex], step)
                            audioManager.strike()
                            audioManager.startTone(0.35f)
                            delay(230)
                        }
                    } finally {
                        audioManager.stopTone()
                        audioManager.frequency = PITCHES_HZ[settings.pitchIndex]
                    }
                }
            }
        }

        // The beat fades as the player locks in (off: the beat always plays)
        SwitchRow(stringResource(R.string.beat_fade_title), settings.beatFade) { settings.beatFade = it }
        Text(
            stringResource(R.string.beat_fade_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        LevelSlider(R.string.volume_click, settings.clickVolume, { settings.clickVolume = it }) {
            audioManager.playClick(settings.clickVolume / 100f, accent = 2)
        }

        // A sound where the note ends and the rest begins, its loudness and its note
        SwitchRow(stringResource(R.string.rest_sound_title), settings.restSound) {
            settings.restSound = it
            if (it) audioManager.playRestCue(settings.restPitch, settings.restVolume / 100f)
        }
        if (settings.restSound) {
            LevelSlider(R.string.volume_rest, settings.restVolume, { settings.restVolume = it }) {
                audioManager.playRestCue(settings.restPitch, settings.restVolume / 100f)
            }
            PitchTitle(R.string.rest_pitch_title)
            PitchChips(settings.restPitch) {
                settings.restPitch = it
                audioManager.playRestCue(it, settings.restVolume / 100f)
            }
        }

        // Imitate mode: the sound of the player's own press
        Text(
            stringResource(R.string.touch_sound_title),
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 28.dp)
        )
        Text(
            stringResource(R.string.touch_sound_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        TouchSound.entries.forEach { sound ->
            ChoiceRow(stringResource(sound.nameRes), settings.touchSound == sound) {
                settings.touchSound = sound
                audioManager.playTouchSound(sound, settings.touchPitch, settings.touchVolume / 100f)
            }
        }
        if (settings.touchSound != TouchSound.OFF) {
            LevelSlider(R.string.volume_touch, settings.touchVolume, { settings.touchVolume = it }) {
                audioManager.playTouchSound(settings.touchSound, settings.touchPitch, settings.touchVolume / 100f)
            }
        }
        if (settings.touchSound != TouchSound.OFF && settings.touchSound != TouchSound.CLICK) {
            PitchTitle(R.string.touch_pitch_title)
            PitchChips(settings.touchPitch) {
                settings.touchPitch = it
                audioManager.playTouchSound(settings.touchSound, it, settings.touchVolume / 100f)
            }
        }

        // Vibration strength
        Text(
            stringResource(R.string.vibration_strength_title, settings.vibrationStrength),
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 28.dp)
        )
        if (hapticManager.hasAmplitudeControl) {
            Slider(
                value = settings.vibrationStrength.toFloat(),
                onValueChange = { settings.vibrationStrength = ((it + 5) / 10).toInt() * 10 },
                onValueChangeFinished = { // feel the chosen strength
                    hapticManager.strength = settings.vibrationStrength / 100f
                    hapticManager.vibrate(150, 255)
                },
                valueRange = 10f..100f,
                steps = 8
            )
        } else {
            Text(
                stringResource(R.string.vibration_strength_fixed),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AboutScreen(updates: UpdateController, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    MenuPage(R.string.menu_about, onBack) {
        Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.about_version, version), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.about_text), fontSize = 16.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(32.dp))
        UpdateSection(updates)
    }
}

/** Loudness of one kind of sound: its name with the percent, and a slider (10%..150%). Letting go plays it. */
@Composable
private fun LevelSlider(@StringRes title: Int, value: Int, onChange: (Int) -> Unit, onRelease: () -> Unit) {
    Text(
        stringResource(title, value),
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp)
    )
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(((it + 5) / 10).toInt() * 10) },
        onValueChangeFinished = onRelease,
        valueRange = 10f..150f,
        steps = 13
    )
}

/** Small heading over a row of pitch chips, saying which sound they tune. */
@Composable
private fun PitchTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp)
    )
}

/** The eight notes as chips; used for the held note and for the cue sounds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PitchChips(selected: Int, onSelect: (Int) -> Unit) {
    val names = stringArrayResource(R.array.pitch_names)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, name ->
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = { Text(name, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) }
            )
        }
    }
}

/** A setting that is on or off: label and switch; the whole row toggles it. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 18.sp)
    }
}
