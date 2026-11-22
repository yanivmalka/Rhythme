package com.example.rhythme

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythme.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---- Rules of the match (tune these after trying it on the phone) ----
private const val PERFECT_MS = 30L          // a start this close to the guide's note is "perfect"
private const val GOOD_MS = 70L             // ...this close is "good"; anything later or earlier only scores a little
private const val LENGTH_GOOD = 0.13f       // held within 13% of the note's length: full length bonus
private const val LENGTH_OK = 0.25f         // ...within 25%: a small bonus
private const val POINTS_PERFECT = 100
private const val POINTS_GOOD = 50
private const val POINTS_OK = 10
private const val BONUS_LENGTH = 50
private const val BONUS_LENGTH_OK = 20
private const val CHARGE_PERFECTS = 8       // perfect notes needed to charge a skill
private const val SKILL_BEATS = 4           // how long Fog and Freeze last
private const val NOTES_PER_ROUND = 8
private const val VERDICT_MS = 900L         // how long "Perfect", "+150" etc. stay on screen

private val P1Blue = Color(0xFF9CC2FF)
private val P2Orange = Color(0xFFF2A65A)
private val GuideGray = Color(0xFF8A8F99)

/** Moves a player can fire at the other one once enough perfect notes have charged it. */
enum class Skill(@StringRes val nameRes: Int, @StringRes val descRes: Int, val icon: ImageVector) {
    FOG(R.string.tp_skill_fog, R.string.tp_skill_fog_desc, Icons.Filled.Cloud),
    FREEZE(R.string.tp_skill_freeze, R.string.tp_skill_freeze_desc, Icons.Filled.AcUnit),
    SHIELD(R.string.tp_skill_shield, R.string.tp_skill_shield_desc, Icons.Filled.Shield)
}

/** One press on the pad, for the graph; [end] = 0 while it is still held. */
internal data class Mark(val start: Long, val end: Long)

/** One player of a match: name, color, chosen skills, and everything the match counts for them. */
class TwoPlayer(val index: Int, name: String, val color: Color, skills: List<Skill>) {
    var name by mutableStateOf(name)
    val skills = mutableStateListOf<Skill>().apply { addAll(skills) }

    var score by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var bestCombo by mutableIntStateOf(0)
    var perfect by mutableIntStateOf(0)
    var good by mutableIntStateOf(0)
    var ok by mutableIntStateOf(0)
    var missed by mutableIntStateOf(0)
    var charge by mutableIntStateOf(0)
    var fogUntil by mutableLongStateOf(0L)
    var freezeUntil by mutableLongStateOf(0L)
    var shieldUp by mutableStateOf(false)
    var skillsUsed by mutableIntStateOf(0)
    var blocked by mutableIntStateOf(0)

    // The press in progress (pressAt = 0: not pressing)
    var pressAt by mutableLongStateOf(0L)
    internal var pressNote = -1
    internal var pressOnsetMs = 0L
    internal var pressBlind = false
    internal val hit = HashSet<Int>() // guide notes this player has already played
    internal val marks = mutableStateListOf<Mark>()

    // What was said about the last note
    var verdictRes by mutableIntStateOf(0)
    var popup by mutableStateOf("")
    var shownAt by mutableLongStateOf(0L)

    /** Combo bonus: x2 from 8 good notes in a row, x3 from 16, x4 from 32; none while frozen. */
    fun multiplier(t: Long): Int = when {
        t < freezeUntil -> 1
        combo >= 32 -> 4
        combo >= 16 -> 3
        combo >= 8 -> 2
        else -> 1
    }

    fun show(@StringRes verdict: Int, text: String, t: Long) {
        verdictRes = verdict
        popup = text
        shownAt = t
    }

    fun resetForMatch() {
        score = 0; combo = 0; bestCombo = 0; perfect = 0; good = 0; ok = 0; missed = 0; charge = 0
        fogUntil = 0L; freezeUntil = 0L; shieldUp = false; skillsUsed = 0; blocked = 0
        pressAt = 0L; pressNote = -1; hit.clear(); marks.clear()
        verdictRes = 0; popup = ""; shownAt = 0L
    }
}

private enum class Stage { SETUP, GAME, RESULTS }

/**
 * Two players on one phone, face to face: the phone lies flat between them and the screen is
 * split in two, the far half turned around. Both play the same guide (note, rest, note...) on a
 * shared beat, for points, combos and skills they fire at each other.
 */
@Composable
fun TwoPlayerScreen(
    settings: PlaySettings,
    hapticManager: HapticManager,
    audioManager: AudioManager,
    onBack: () -> Unit
) {
    val name1 = settings.profileName.ifBlank { stringResource(R.string.tp_player_1) }
    val name2 = stringResource(R.string.tp_player_2)
    val players = remember {
        listOf(
            TwoPlayer(0, name1, P1Blue, listOf(Skill.FOG, Skill.SHIELD)),
            TwoPlayer(1, name2, P2Orange, listOf(Skill.FOG, Skill.FREEZE))
        )
    }
    var stage by remember { mutableStateOf(Stage.SETUP) }
    var bpm by remember { mutableIntStateOf(80) }
    var note by remember { mutableStateOf(NoteLength.QUARTER) }
    var rounds by remember { mutableIntStateOf(5) }
    var matchId by remember { mutableIntStateOf(0) }

    // One speaker and one motor for two players: no held tone, only beats and short chimes
    DisposableEffect(Unit) {
        audioManager.stopTone()
        hapticManager.endHold()
        onDispose {
            audioManager.stopTone()
            hapticManager.stop()
        }
    }

    fun startMatch() {
        players.forEach { it.resetForMatch() }
        matchId++
        stage = Stage.GAME
    }

    BackHandler(enabled = stage != Stage.SETUP) { stage = Stage.SETUP }

    when (stage) {
        Stage.SETUP -> TwoPlayerSetup(
            players, bpm, note, rounds,
            onBpm = { bpm = Tempo.clamp(it) },
            onNote = { note = it },
            onRounds = { rounds = it },
            onStart = { startMatch() },
            onBack = onBack
        )
        Stage.GAME -> key(matchId) {
            TwoPlayerGame(
                players, bpm, note, rounds, settings, audioManager, hapticManager,
                onFinish = { stage = Stage.RESULTS },
                onQuit = { stage = Stage.SETUP }
            )
        }
        Stage.RESULTS -> TwoPlayerResults(
            players,
            onRematch = { startMatch() },
            onSetup = { stage = Stage.SETUP },
            onDone = onBack
        )
    }
}

// ------------------------------------------------------------------ Setup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPlayerSetup(
    players: List<TwoPlayer>,
    bpm: Int,
    note: NoteLength,
    rounds: Int,
    onBpm: (Int) -> Unit,
    onNote: (NoteLength) -> Unit,
    onRounds: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.tp_title), fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.tp_how),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            players.forEach { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(p.color)
                    )
                    OutlinedTextField(
                        value = p.name,
                        onValueChange = { p.name = it.take(16) },
                        label = { Text(stringResource(if (p.index == 0) R.string.tp_player_1 else R.string.tp_player_2)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.tp_skills_pick), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Skill.entries.forEach { skill ->
                            val selected = skill in p.skills
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) p.skills.remove(skill)
                                    else {
                                        if (p.skills.size >= 2) p.skills.removeAt(0)
                                        p.skills.add(skill)
                                    }
                                },
                                label = { Text(stringResource(skill.nameRes)) },
                                leadingIcon = { Icon(skill.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Skill.entries.forEach { skill ->
                    Text(
                        stringResource(skill.nameRes) + " · " + stringResource(skill.descRes),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(stringResource(R.string.tp_rhythm), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(NoteLength.EIGHTH, NoteLength.QUARTER, NoteLength.HALF).forEach { n ->
                    FilterChip(selected = n == note, onClick = { onNote(n) }, label = { Text(stringResource(n.nameRes)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onBpm(bpm - Tempo.STEP) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tp_slower))
                }
                Text(
                    "$bpm BPM · ${Tempo.nameFor(bpm)}",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(170.dp)
                )
                IconButton(onClick = { onBpm(bpm + Tempo.STEP) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tp_faster))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 7).forEach { r ->
                    FilterChip(selected = r == rounds, onClick = { onRounds(r) }, label = { Text(stringResource(R.string.tp_rounds, r)) })
                }
            }

            Text(
                stringResource(R.string.tp_points_help),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onStart,
            enabled = players.all { it.skills.isNotEmpty() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = P1Blue, contentColor = Color(0xFF0D1A2E))
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tp_start), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ The match

@Composable
private fun TwoPlayerGame(
    players: List<TwoPlayer>,
    bpm: Int,
    note: NoteLength,
    rounds: Int,
    settings: PlaySettings,
    audio: AudioManager,
    haptics: HapticManager,
    onFinish: () -> Unit,
    onQuit: () -> Unit
) {
    val ts = settings.timeSignature
    val pulseMs = ts.pulseMs(bpm)
    val noteMs = note.durationMs(bpm)
    val cycleMs = noteMs * 2 // each note is followed by a rest as long as itself
    val total = rounds * NOTES_PER_ROUND
    val startAt = remember { SystemClock.uptimeMillis() + 400 }
    val t0 = startAt + (ts.pulsesPerBar * pulseMs).toLong() // one bar of count-in, then the first note
    val endAt = t0 + total * cycleMs

    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var beat by remember { mutableIntStateOf(-1) }
    val finish by rememberUpdatedState(onFinish)

    fun noteStart(k: Int): Long = t0 + k * cycleMs

    fun press(p: TwoPlayer, t: Long) {
        if (p.pressAt > 0L) return
        p.pressAt = t
        p.marks.add(Mark(t, 0L))
        while (p.marks.size > 16) p.marks.removeAt(0)
        // Each player has their own chime, so both know whose press they heard
        if (settings.soundEnabled) {
            audio.playChime(doubleArrayOf(if (p.index == 0) 880.0 else 659.25), level = 0.22 * volumeGain(settings.touchVolume))
        }
        p.pressNote = -1
        if (t < t0 - cycleMs / 2 || t > endAt) return
        val k = ((t - t0).toDouble() / cycleMs).roundToInt() // the nearest note of the guide
        if (k !in 0 until total) return
        if (k in p.hit) { // a second press for the same note: no points, and the combo breaks
            p.combo = 0
            return
        }
        p.hit.add(k)
        p.pressNote = k
        p.pressOnsetMs = t - noteStart(k)
        p.pressBlind = t < p.fogUntil
    }

    fun release(p: TwoPlayer, t: Long) {
        if (p.pressAt == 0L) return
        val held = t - p.pressAt
        p.pressAt = 0L
        if (p.marks.isNotEmpty()) p.marks[p.marks.lastIndex] = p.marks.last().copy(end = t)
        if (p.pressNote < 0) return
        p.pressNote = -1

        val onset = abs(p.pressOnsetMs)
        val base = when {
            onset <= PERFECT_MS -> POINTS_PERFECT
            onset <= GOOD_MS -> POINTS_GOOD
            else -> POINTS_OK
        }
        val off = abs(held.toFloat() / noteMs - 1f)
        val lengthBonus = when {
            off <= LENGTH_GOOD -> BONUS_LENGTH
            off <= LENGTH_OK -> BONUS_LENGTH_OK
            else -> 0
        }
        if (base >= POINTS_GOOD) {
            p.combo++
            if (p.combo > p.bestCombo) p.bestCombo = p.combo
        } else {
            p.combo = 0
        }
        when (base) {
            POINTS_PERFECT -> {
                p.perfect++
                if (p.charge < CHARGE_PERFECTS) p.charge++
            }
            POINTS_GOOD -> p.good++
            else -> p.ok++
        }
        // Played in the Fog, by feel alone: double points
        val mult = p.multiplier(t) * (if (p.pressBlind) 2 else 1)
        val points = (base + lengthBonus) * mult
        p.score += points
        val verdict = when {
            base == POINTS_PERFECT -> R.string.tp_perfect
            base == POINTS_GOOD -> R.string.tp_good
            p.pressOnsetMs < 0 -> R.string.tp_early
            else -> R.string.tp_late
        }
        p.show(verdict, "+$points" + if (mult > 1) " ×$mult" else "", t)
    }

    fun fire(p: TwoPlayer, skill: Skill, t: Long) {
        if (p.charge < CHARGE_PERFECTS || t > endAt) return
        p.charge = 0
        p.skillsUsed++
        val other = players[1 - p.index]
        val until = t + (SKILL_BEATS * pulseMs).toLong()
        if (skill == Skill.SHIELD) {
            p.shieldUp = true
        } else if (other.shieldUp) {
            other.shieldUp = false
            other.blocked++
            other.show(R.string.tp_blocked, "", t)
        } else if (skill == Skill.FOG) {
            other.fogUntil = until
        } else {
            other.freezeUntil = until
        }
        if (settings.soundEnabled) audio.playChime(doubleArrayOf(392.0, 523.25, 784.0), gapMs = 60, level = 0.2)
    }

    // The clock: one value per frame for everything drawn; at the end, on to the results
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            now = SystemClock.uptimeMillis()
            if (now >= endAt + 800) break
        }
        players.forEach { release(it, now) }
        finish()
    }

    // The shared beat, scheduled on absolute times so it never drifts
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            val at = startAt + (n * pulseMs).toLong()
            if (at > endAt) break
            delay(at - SystemClock.uptimeMillis())
            beat = n
            val accent = ts.accents[n % ts.pulsesPerBar]
            if (settings.beatSound) audio.playClick(volumeGain(settings.clickVolume), accent)
            if (settings.vibrationEnabled) haptics.tick(accent)
            n++
        }
    }

    // A note nobody pressed for, half a cycle after it started, is a miss
    LaunchedEffect(Unit) {
        for (k in 0 until total) {
            delay(noteStart(k) + cycleMs / 2 - SystemClock.uptimeMillis())
            val t = SystemClock.uptimeMillis()
            players.forEach { p ->
                if (k !in p.hit) {
                    p.missed++
                    p.combo = 0
                    p.show(R.string.tp_miss, "", t)
                }
            }
        }
    }

    val timing = MatchTiming(t0, noteMs, cycleMs, total, pulseMs)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        // The far player's half faces them
        PlayerHalf(
            players[1], now, timing, settings.lightEnabled,
            onPress = { press(players[1], it) },
            onRelease = { release(players[1], it) },
            onFire = { fire(players[1], it, SystemClock.uptimeMillis()) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f)
        )
        CenterStrip(players, now, timing, beat, ts, rounds, onQuit)
        PlayerHalf(
            players[0], now, timing, settings.lightEnabled,
            onPress = { press(players[0], it) },
            onRelease = { release(players[0], it) },
            onFire = { fire(players[0], it, SystemClock.uptimeMillis()) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

/** When the guide's notes fall: note k starts at t0 + k * cycleMs and lasts noteMs. */
private class MatchTiming(val t0: Long, val noteMs: Long, val cycleMs: Long, val total: Int, val pulseMs: Double)

@Composable
private fun PlayerHalf(
    p: TwoPlayer,
    now: Long,
    timing: MatchTiming,
    lightEnabled: Boolean,
    onPress: (Long) -> Unit,
    onRelease: (Long) -> Unit,
    onFire: (Skill) -> Unit,
    modifier: Modifier
) {
    val fogged = now < p.fogUntil
    val frozen = now < p.freezeUntil
    val started = now >= timing.t0
    val index = if (started) ((now - timing.t0) / timing.cycleMs).toInt() else -1
    val pos = if (started) (now - timing.t0) % timing.cycleMs else 0L
    val inNote = started && index < timing.total && pos < timing.noteMs
    val holding = p.pressAt > 0L
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val padLabel = stringResource(R.string.tp_cd_pad, p.name)

    Column(
        modifier
            .background(p.color.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Name, shield, score, combo
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = p.color, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            if (p.shieldUp) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = stringResource(R.string.tp_shield_up),
                    tint = p.color,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp)
                )
            }
            Text("%,d".format(p.score), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            val mult = p.multiplier(now)
            if (frozen || mult > 1) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (frozen) stringResource(R.string.tp_frozen) else "×$mult · ${p.combo}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111318),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (frozen) Color(0xFFB8D8F0) else p.color)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // The pad, with the guide's ring around it
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .semantics { contentDescription = padLabel }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            press(down.uptimeMillis)
                            var upAt = 0L
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    upAt = change.uptimeMillis
                                    change.consume()
                                    break
                                }
                            }
                            release(if (upAt > 0L) upAt else SystemClock.uptimeMillis())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.05f
                    val r = size.minDimension / 2 - stroke
                    if (fogged) {
                        drawCircle(
                            GuideGray.copy(alpha = 0.6f), r,
                            style = Stroke(stroke * 0.35f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 20f)))
                        )
                    } else {
                        drawCircle(p.color.copy(alpha = 0.18f), r, style = Stroke(stroke))
                        if (inNote) {
                            drawArc(
                                p.color, -90f, 360f * pos / timing.noteMs, useCenter = false,
                                topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r),
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    val padR = r - stroke * 1.6f
                    val flash = lightEnabled && !fogged && inNote && pos < 140
                    val fill = when {
                        holding -> p.color
                        fogged -> GuideGray.copy(alpha = 0.4f)
                        flash -> p.color.copy(alpha = 0.6f)
                        else -> p.color.copy(alpha = 0.3f)
                    }
                    drawCircle(fill, padR)
                    if (flash) drawCircle(Color.White.copy(alpha = 0.7f * (1f - pos / 140f)), padR, style = Stroke(stroke * 0.6f))
                }
                val label = when {
                    !started -> ceil((timing.t0 - now) / timing.pulseMs).toInt().coerceAtLeast(1).toString()
                    fogged -> stringResource(R.string.tp_fog_label)
                    p.verdictRes != 0 && now - p.shownAt < VERDICT_MS -> stringResource(p.verdictRes)
                    else -> ""
                }
                Text(
                    label,
                    fontSize = if (!started) 40.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (holding) Color(0xFF111318) else Ink,
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (p.popup.isNotEmpty() && now - p.shownAt < VERDICT_MS) {
                Text(
                    p.popup,
                    color = StarGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        GuideGraph(p, now, timing, fogged, Modifier.fillMaxWidth().height(40.dp))
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.skills.forEach { skill ->
                SkillButton(skill, p.charge, p.color, Modifier.weight(1f)) { onFire(skill) }
            }
        }
    }
}

/** The guide's notes as bars sliding by, the player's presses painted over them. */
@Composable
private fun GuideGraph(p: TwoPlayer, now: Long, timing: MatchTiming, fogged: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val window = timing.cycleMs * 4f
        val nowX = size.width * 0.6f
        fun x(t: Long): Float = nowX + (t - now) / window * size.width
        drawRoundRect(p.color.copy(alpha = 0.08f), cornerRadius = CornerRadius(8.dp.toPx()))
        val top = size.height * 0.18f
        val bottom = size.height * 0.82f
        if (!fogged) {
            val from = (((now - timing.t0) - (window * 0.6f).toLong()) / timing.cycleMs).toInt() - 1
            val to = (((now - timing.t0) + (window * 0.4f).toLong()) / timing.cycleMs).toInt() + 1
            for (k in from.coerceAtLeast(0)..to.coerceAtMost(timing.total - 1)) {
                val s = timing.t0 + k * timing.cycleMs
                drawRect(
                    GuideGray.copy(alpha = 0.7f),
                    topLeft = Offset(x(s), top),
                    size = Size(timing.noteMs / window * size.width, bottom - top),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        val h = bottom - top
        p.marks.forEach { m ->
            val end = if (m.end == 0L) now else m.end
            val x1 = x(m.start)
            val x2 = x(end)
            if (x2 >= 0f && x1 <= size.width) {
                drawRect(p.color, topLeft = Offset(x1, top + h * 0.25f), size = Size(maxOf(3f, x2 - x1), h * 0.5f))
            }
        }
        drawLine(Ink, Offset(nowX, 0f), Offset(nowX, size.height), strokeWidth = 1.5.dp.toPx())
    }
}

@Composable
private fun SkillButton(skill: Skill, charge: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val ready = charge >= CHARGE_PERFECTS
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(skill.nameRes)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (ready) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(if (ready) 2.dp else 1.dp, if (ready) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .clickable(enabled = ready, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!ready) { // how far the skill has charged
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(charge / CHARGE_PERFECTS.toFloat())
                    .background(color.copy(alpha = 0.18f))
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(skill.icon, contentDescription = null, tint = if (ready) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (ready) stringResource(R.string.tp_ready, name) else "$name · $charge/$CHARGE_PERFECTS",
                fontSize = 14.sp,
                fontWeight = if (ready) FontWeight.Bold else FontWeight.Normal,
                color = if (ready) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Between the halves: the beat, Stop, the round, and who is ahead. */
@Composable
private fun CenterStrip(
    players: List<TwoPlayer>,
    now: Long,
    timing: MatchTiming,
    beat: Int,
    ts: TimeSignature,
    rounds: Int,
    onQuit: () -> Unit
) {
    val round = if (now < timing.t0) 1
    else (((now - timing.t0) / timing.cycleMs).toInt() / NOTES_PER_ROUND + 1).coerceAtMost(rounds)
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until ts.pulsesPerBar) {
                val lit = beat >= 0 && beat % ts.pulsesPerBar == i
                Box(
                    Modifier
                        .size(if (lit) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(if (lit) StarGold else GuideGray.copy(alpha = 0.6f))
                )
            }
        }
        FilledIconButton(onClick = onQuit, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.tp_stop))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("$round / $rounds", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val sum = players[0].score + players[1].score
            val share = if (sum == 0) 0.5f else (players[0].score.toFloat() / sum).coerceIn(0.02f, 0.98f)
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .width(110.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(Modifier.weight(share).fillMaxHeight().background(players[0].color))
                Box(Modifier.weight(1f - share).fillMaxHeight().background(players[1].color))
            }
        }
    }
}

// ------------------------------------------------------------------ Results

@Composable
private fun TwoPlayerResults(
    players: List<TwoPlayer>,
    onRematch: () -> Unit,
    onSetup: () -> Unit,
    onDone: () -> Unit
) {
    val (a, b) = players
    val winner = when {
        a.score > b.score -> a
        b.score > a.score -> b
        else -> null
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = StarGold, modifier = Modifier.size(64.dp))
            Text(
                if (winner == null) stringResource(R.string.tp_tie) else stringResource(R.string.tp_wins, winner.name),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = winner?.color ?: Ink,
                textAlign = TextAlign.Center
            )
            if (winner != null) {
                Text(
                    stringResource(R.string.tp_by_points, abs(a.score - b.score)),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                players.forEach { StatCard(it, Modifier.weight(1f)) }
            }
        }
        Button(
            onClick = onRematch,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = P1Blue, contentColor = Color(0xFF0D1A2E))
        ) { Text(stringResource(R.string.tp_rematch), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onSetup, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.tp_change)) }
            TextButton(onClick = onDone, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.tp_done)) }
        }
    }
}

@Composable
private fun StatCard(p: TwoPlayer, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(p.color))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.name, color = p.color, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("%,d".format(p.score), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            StatRow(stringResource(R.string.tp_perfect), p.perfect.toString())
            StatRow(stringResource(R.string.tp_good), p.good.toString())
            StatRow(stringResource(R.string.tp_stat_missed), p.missed.toString())
            StatRow(stringResource(R.string.tp_stat_combo), p.bestCombo.toString())
            StatRow(stringResource(R.string.tp_stat_skills), p.skillsUsed.toString())
            if (p.blocked > 0) StatRow(stringResource(R.string.tp_stat_blocked), p.blocked.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = Ink)
    }
}
