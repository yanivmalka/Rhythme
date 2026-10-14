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

@Composable
fun InstrumentScreen(settings: PlaySettings, audioManager: AudioManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
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
                audioManager.instrument = instrument
                scope.launch { // short preview at the chosen pitch
                    audioManager.startTone(0.35f)
                    delay(700)
                    audioManager.stopTone()
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    MenuPage(R.string.menu_about, onBack) {
        Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.about_version, version), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.about_text), fontSize = 16.sp, lineHeight = 24.sp)
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
