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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythme.ui.theme.Ink
import com.example.rhythme.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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
private const val COUNT_IN_BARS = 2
private const val VERDICT_MS = 900L         // how long "Perfect", "+150" etc. stay on screen
private const val TEMPO_UP_SHARE = 0.75f    // both players together this good in a round: the next one is faster
private const val TEMPO_DOWN_SHARE = 0.4f   // ...this weak: slower again (never below the starting tempo)
private const val P2_SEMITONES = -5         // the second player's notes sound a fourth lower
private const val SURPRISE_CHANCE = 0.2f    // share of the guide's notes that carry a surprise
private const val COIN_POINTS = 50          // each coin is worth more than the one before: 50, 100, 150...
private const val BOLT_CHARGE = 3           // a bolt charges the skills by this many perfect notes
private const val STAR_COMBO = 4            // a star adds this much to the combo

/**
 * Slack for the first rounds, while the players find the beat: every timing and length window
 * is twice as wide in the first round and half again as wide in the second. A miss in the
 * first round doesn't break the combo.
 */
private fun grace(k: Int): Float = when (k.coerceAtLeast(0) / NOTES_PER_ROUND) {
    0 -> 2f
    1 -> 1.5f
    else -> 1f
}

// The running man on the wave
private const val JUMP_MS = 280L
private const val HOP_MS = 360L
private const val LEAN_MS = 480L
private const val SLIP_MS = 380L
private const val RUN_MS = 420L
private const val CLIMB_MS = 300L
private const val COLLECT_FX_MS = 700L

private val P1Blue = Color(0xFF9CC2FF)
private val P2Orange = Color(0xFFF2A65A)
private val GuideGray = Color(0xFF8A8F99)
private val HoldGreen: Color get() = Palette.pick(Color(0xFF34C759), Color(0xFF1E9E48))
private val HoldRed: Color get() = Palette.pick(Color(0xFFFF6B6B), Color(0xFFD93025))
private val ScoreGreen: Color get() = Palette.pick(Color(0xFF66DD77), Color(0xFF1E9E48))
private val ScoreAmber: Color get() = Palette.pick(Color(0xFFFFC857), Color(0xFFB7791F))
private val BoltYellow = Color(0xFFFFE066)
private val StarViolet = Color(0xFFC3A6FF)

/** Moves a player can fire at the other one once enough perfect notes have charged it. */
enum class Skill(@StringRes val nameRes: Int, @StringRes val descRes: Int, val icon: ImageVector) {
    FOG(R.string.tp_skill_fog, R.string.tp_skill_fog_desc, Icons.Filled.Cloud),
    FREEZE(R.string.tp_skill_freeze, R.string.tp_skill_freeze_desc, Icons.Filled.AcUnit),
    SHIELD(R.string.tp_skill_shield, R.string.tp_skill_shield_desc, Icons.Filled.Shield)
}

/** Prizes that sit on some of the waves, for the running man to collect. */
internal enum class Surprise { COIN, BOLT, STAR }

/** One press on the pad, for the graph. [end] = 0 while it is still held; [counted]: it played a note of the guide. */
internal data class Mark(val start: Long, val end: Long, val noteMs: Long, val counted: Boolean, val grace: Float)

/** How the running man moves onto a press: drops in (first press), jumps across, or jumps too soon and slips. */
internal enum class Jump { ENTER, JUMP, SLIP }

/** What the running man does when the press is let go. */
internal enum class ManMove { NONE, HOP, LEAN_BACK }

/** One player of a match: name, color, sound, scale, skills, and everything the match counts for them. */
class TwoPlayer(
    val index: Int,
    name: String,
    val color: Color,
    skills: List<Skill>,
    instrument: Instrument,
    scale: Scale
) {
    var name by mutableStateOf(name)
    /** Played by the computer (solo game). */
    var isBot by mutableStateOf(false)
    var instrument by mutableStateOf(instrument)
    var scale by mutableStateOf(scale)
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
    var coins by mutableIntStateOf(0)
    var bolts by mutableIntStateOf(0)
    var stars by mutableIntStateOf(0)
    val surprises: Int get() = coins + bolts + stars

    // The last match, to show what got better in this one
    var hasPrev = false
    var prevScore = 0
    var prevPerfect = 0
    var prevMissed = 0
    var prevCombo = 0

    // The press in progress (pressAt = 0: not pressing)
    var pressAt by mutableLongStateOf(0L)
    internal var pressNote = -1
    internal var pressNoteMs = 0L
    internal var pressOnsetMs = 0L
    internal var pressBlind = false
    internal var pressGrace = 1f
    internal var scaleStep = 0
    internal val hit = HashSet<Int>()            // guide notes this player has already played
    internal val goodNotes = HashSet<Int>()      // ...and those played well (they speed the tempo up)
    internal val collectedNotes = HashSet<Int>() // surprises taken
    internal val marks = mutableStateListOf<Mark>()

    // How this player has played lately (the computer plays about as well)
    internal val recentOnsets = ArrayDeque<Long>()
    internal val recentLengths = ArrayDeque<Float>() // |held / note - 1|
    internal val recentHits = ArrayDeque<Boolean>()  // played the note, or missed it

    internal fun track(onsetMs: Long?, lengthOff: Float?) {
        recentHits.addLast(onsetMs != null)
        if (onsetMs != null) recentOnsets.addLast(onsetMs)
        if (lengthOff != null) recentLengths.addLast(lengthOff)
        while (recentHits.size > 12) recentHits.removeFirst()
        while (recentOnsets.size > 10) recentOnsets.removeFirst()
        while (recentLengths.size > 10) recentLengths.removeFirst()
    }

    // The running man: times are on the timeline (where on the wave), level 0 = on a wave, 1 = down
    internal var jumpAt by mutableLongStateOf(0L) // 0 = not on the graph yet
    internal var jump by mutableStateOf(Jump.ENTER)
    internal var fromAt = 0.0
    internal var fromLevel = 0f
    internal var landAt = 0L
    internal var landLow = false
    internal var runTo = 0L    // after a slip: the foot of the wave he runs to and climbs; 0 = none
    internal var standAt = 0L  // ...and where he stands on top of it
    internal var move by mutableStateOf(ManMove.NONE)
    internal var moveAt by mutableLongStateOf(0L)
    internal var pendingCollect = -1 // the surprise he picks up once he has climbed
    internal var pendingCollectAt = 0L
    internal var fxType by mutableStateOf(Surprise.COIN)
    internal var fxAt by mutableLongStateOf(0L)
    internal var fxNote = -1

    // What was said about the last note, and about the last surprise
    var verdictRes by mutableIntStateOf(0)
    var popup by mutableStateOf("")
    var shownAt by mutableLongStateOf(0L)
    var bonusText by mutableStateOf("")
    var bonusAt by mutableLongStateOf(0L)

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

    /** Good notes this player played in round [r]. */
    fun goodInRound(r: Int): Int = goodNotes.count { it / NOTES_PER_ROUND == r }

    fun rememberAsPrevious() {
        hasPrev = true
        prevScore = score
        prevPerfect = perfect
        prevMissed = missed
        prevCombo = bestCombo
    }

    fun resetForMatch() {
        score = 0; combo = 0; bestCombo = 0; perfect = 0; good = 0; ok = 0; missed = 0; charge = 0
        fogUntil = 0L; freezeUntil = 0L; shieldUp = false; skillsUsed = 0; blocked = 0
        coins = 0; bolts = 0; stars = 0
        pressAt = 0L; pressNote = -1; scaleStep = 0
        hit.clear(); goodNotes.clear(); collectedNotes.clear(); marks.clear()
        recentOnsets.clear(); recentLengths.clear(); recentHits.clear()
        jumpAt = 0L; runTo = 0L; move = ManMove.NONE; moveAt = 0L; pendingCollect = -1; fxAt = 0L
        verdictRes = 0; popup = ""; shownAt = 0L; bonusText = ""; bonusAt = 0L
    }
}

/** One round of the match: 8 notes, each followed by an equal rest, at one tempo. */
private class RoundTiming(val start: Long, val bpm: Int, val noteMs: Long, val pulseMs: Double) {
    val cycleMs: Long get() = noteMs * 2
    val end: Long get() = start + NOTES_PER_ROUND * cycleMs
}

/**
 * When the guide's notes fall. Each round may have its own tempo, decided near the end of the
 * round before it; until then the rounds ahead are drawn at the tempo of the last one known.
 */
private class Schedule(val total: Int, val countInStart: Long, first: RoundTiming) {
    val rounds = mutableStateListOf(first)
    val t0: Long get() = rounds[0].start

    fun round(k: Int): RoundTiming = rounds[minOf(k.coerceAtLeast(0) / NOTES_PER_ROUND, rounds.lastIndex)]

    fun noteStart(k: Int): Long {
        val r = k / NOTES_PER_ROUND
        if (r < rounds.size) return rounds[r].start + (k % NOTES_PER_ROUND) * rounds[r].cycleMs
        val last = rounds.last()
        return last.end + (k - rounds.size * NOTES_PER_ROUND) * last.cycleMs
    }

    fun noteMs(k: Int): Long = round(k).noteMs

    /** The end of the last note's rest. */
    val endAt: Long get() = noteStart(total)

    /** The round in force at [t] (the first one before the match starts). */
    private fun roundIndexAt(t: Long): Int = rounds.indexOfLast { it.start <= t }.coerceAtLeast(0)

    /** The note whose cycle (note + rest) contains [t]; negative before the first note. */
    fun indexAt(t: Long): Int {
        val r = roundIndexAt(t)
        val rt = rounds[r]
        return r * NOTES_PER_ROUND + floor((t - rt.start).toDouble() / rt.cycleMs).toInt()
    }

    /** The guide note nearest to [t]. */
    fun nearest(t: Long): Int {
        val r = roundIndexAt(t)
        val rt = rounds[r]
        return r * NOTES_PER_ROUND + ((t - rt.start).toDouble() / rt.cycleMs).roundToInt().coerceAtLeast(0)
    }

    /** The first guide note that starts after [t], or -1 when none is left. */
    fun nextAfter(t: Long): Int {
        var k = (indexAt(t) + 1).coerceAtLeast(0)
        if (k > 0 && noteStart(k - 1) > t) k-- // before the first note
        return if (k < total) k else -1
    }
}

private enum class Stage { SETUP, GAME, RESULTS }

/**
 * Two players on one phone, face to face: the phone lies flat between them and the screen is
 * split in two, the far half turned around. Both play the same guide (note, rest, note...) on a
 * shared beat, for points, combos, surprises and skills they fire at each other.
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
        val first = settings.instrument
        val second = if (first == Instrument.GUITAR) Instrument.PIANO else Instrument.GUITAR
        listOf(
            TwoPlayer(0, name1, P1Blue, listOf(Skill.FOG, Skill.SHIELD), first,
                if (settings.scale == Scale.NONE) Scale.MAJOR else settings.scale),
            TwoPlayer(1, name2, P2Orange, listOf(Skill.FOG, Skill.FREEZE), second, Scale.PENTATONIC)
        )
    }
    var stage by remember { mutableStateOf(Stage.SETUP) }
    var bpm by remember { mutableIntStateOf(80) }
    var note by remember { mutableStateOf(NoteLength.QUARTER) }
    var rounds by remember { mutableIntStateOf(5) }
    var startPitch by remember { mutableIntStateOf(settings.pitchIndex.coerceIn(PITCHES_HZ.indices)) }
    var tempoRises by remember { mutableStateOf(true) }
    var matchId by remember { mutableIntStateOf(0) }
    var lastMatchDone by remember { mutableStateOf(false) }
    val computerName = stringResource(R.string.tp_computer)
    var vsComputer by remember { mutableStateOf(false) }
    fun setOpponent(computer: Boolean) {
        vsComputer = computer
        val p2 = players[1]
        p2.isBot = computer
        if (computer && (p2.name.isBlank() || p2.name == name2)) p2.name = computerName
        if (!computer && p2.name == computerName) p2.name = name2
    }

    DisposableEffect(Unit) {
        audioManager.stopTone()
        hapticManager.endHold()
        onDispose {
            audioManager.stopTone()
            audioManager.stopTone2()
            audioManager.instrument = settings.instrument // the main screen's sound again
            hapticManager.stop()
        }
    }

    fun startMatch() {
        players.forEach {
            if (lastMatchDone) it.rememberAsPrevious()
            it.resetForMatch()
        }
        lastMatchDone = false
        matchId++
        stage = Stage.GAME
    }

    BackHandler(enabled = stage != Stage.SETUP) { stage = Stage.SETUP }

    when (stage) {
        Stage.SETUP -> TwoPlayerSetup(
            players, vsComputer, ::setOpponent, bpm, note, rounds, startPitch, tempoRises,
            onBpm = { bpm = Tempo.clamp(it) },
            onNote = { note = it },
            onRounds = { rounds = it },
            onStartPitch = { startPitch = it },
            onTempoRises = { tempoRises = it },
            onStart = { startMatch() },
            onBack = onBack
        )
        Stage.GAME -> key(matchId) {
            TwoPlayerGame(
                players, bpm, note, rounds, startPitch, tempoRises, settings, audioManager, hapticManager,
                onFinish = {
                    lastMatchDone = true
                    stage = Stage.RESULTS
                },
                onQuit = { stage = Stage.SETUP }
            )
        }
        Stage.RESULTS -> TwoPlayerResults(
            players, rounds,
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
    vsComputer: Boolean,
    onOpponent: (Boolean) -> Unit,
    bpm: Int,
    note: NoteLength,
    rounds: Int,
    startPitch: Int,
    tempoRises: Boolean,
    onBpm: (Int) -> Unit,
    onNote: (NoteLength) -> Unit,
    onRounds: (Int) -> Unit,
    onStartPitch: (Int) -> Unit,
    onTempoRises: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val pitchNames = stringArrayResource(R.array.pitch_names)
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
            // Against a friend on the same phone, or alone against the computer
            SetupLabel(R.string.tp_vs)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !vsComputer, onClick = { onOpponent(false) }, label = { Text(stringResource(R.string.tp_vs_friend)) })
                FilterChip(selected = vsComputer, onClick = { onOpponent(true) }, label = { Text(stringResource(R.string.tp_vs_computer)) })
            }
            Text(
                stringResource(if (vsComputer) R.string.tp_vs_computer_hint else R.string.tp_how),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            players.forEach { p ->
                val other = players[1 - p.index]
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
                    // Each player has their own sound: picking the other one's swaps the two
                    SetupLabel(R.string.tp_player_sound)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Instrument.entries.forEach { inst ->
                            FilterChip(
                                selected = inst == p.instrument,
                                onClick = {
                                    if (inst == other.instrument) other.instrument = p.instrument
                                    p.instrument = inst
                                },
                                label = { Text(stringResource(inst.nameRes)) }
                            )
                        }
                    }
                    SetupLabel(R.string.tp_player_scale)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Scale.entries.forEach { s ->
                            FilterChip(selected = s == p.scale, onClick = { p.scale = s }, label = { Text(stringResource(s.nameRes)) })
                        }
                    }
                    SetupLabel(R.string.tp_skills_pick)
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

            SetupLabel(R.string.tp_rhythm)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tp_tempo_rises), fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = tempoRises, onCheckedChange = onTempoRises)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 7).forEach { r ->
                    FilterChip(selected = r == rounds, onClick = { onRounds(r) }, label = { Text(stringResource(R.string.tp_rounds, r)) })
                }
            }
            SetupLabel(R.string.tp_start_note)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pitchNames.forEachIndexed { i, label ->
                    FilterChip(selected = i == startPitch, onClick = { onStartPitch(i) }, label = { Text(label) })
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

@Composable
private fun SetupLabel(@StringRes res: Int) {
    Text(stringResource(res), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------------------------------------------ The match

@Composable
private fun TwoPlayerGame(
    players: List<TwoPlayer>,
    startBpm: Int,
    note: NoteLength,
    roundCount: Int,
    startPitch: Int,
    tempoRises: Boolean,
    settings: PlaySettings,
    audio: AudioManager,
    haptics: HapticManager,
    onFinish: () -> Unit,
    onQuit: () -> Unit
) {
    val ts = settings.timeSignature
    val total = roundCount * NOTES_PER_ROUND
    val startAt = remember { SystemClock.uptimeMillis() + 400 }
    val schedule = remember {
        val pulse = ts.pulseMs(startBpm)
        // A count-in, then the first note
        Schedule(
            total, startAt,
            RoundTiming(startAt + (COUNT_IN_BARS * ts.pulsesPerBar * pulse).toLong(), startBpm, note.durationMs(startBpm), pulse)
        )
    }
    // Surprises on some of the waves, the same for both players (none on the very first notes)
    val surprises = remember {
        val rnd = Random(startAt)
        Array(total) { k -> if (k >= 2 && rnd.nextFloat() < SURPRISE_CHANCE) Surprise.entries[rnd.nextInt(Surprise.entries.size)] else null }
    }

    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var beat by remember { mutableIntStateOf(-1) }
    var tempoChangeAt by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinish)

    // Each player sounds their own held note on their own voice, the second one a fourth lower
    val base1 = PITCHES_HZ[startPitch.coerceIn(PITCHES_HZ.indices)]
    val base2 = base1 * Math.pow(2.0, P2_SEMITONES / 12.0)
    LaunchedEffect(Unit) {
        audio.instrument = players[0].instrument
        audio.instrument2 = players[1].instrument
    }

    fun holdSoundOn(p: TwoPlayer) {
        if (!settings.soundEnabled) return
        if (p.index == 0) {
            audio.frequency = p.scale.hz(base1, p.scaleStep++)
            audio.startTone(0.05f)
        } else {
            audio.frequency2 = p.scale.hz(base2, p.scaleStep++)
            audio.startTone2(0.05f)
        }
    }

    fun holdSoundOff(p: TwoPlayer) {
        if (p.index == 0) audio.stopTone() else audio.stopTone2()
    }

    /** The motor is shared: it plays the hold of whoever pressed last, then the other one's if still held. */
    fun holdVibration(t: Long) {
        if (!settings.vibrationEnabled) return
        val holder = players.filter { it.pressAt > 0L && !it.isBot }.maxByOrNull { it.pressAt }
        if (holder == null) haptics.endHold()
        else haptics.startHold(holder.pressAt, holder.pressNoteMs, playFrom = t)
    }

    fun collect(p: TwoPlayer, k: Int, t: Long) {
        val s = surprises.getOrNull(k) ?: return
        if (!p.collectedNotes.add(k)) return
        p.bonusText = when (s) {
            Surprise.COIN -> {
                p.coins++
                val points = COIN_POINTS * p.coins // each coin is worth more than the last
                p.score += points
                "+$points"
            }
            Surprise.BOLT -> {
                p.bolts++
                p.charge = minOf(CHARGE_PERFECTS, p.charge + BOLT_CHARGE)
                "⚡+$BOLT_CHARGE"
            }
            Surprise.STAR -> {
                p.stars++
                p.combo += STAR_COMBO
                if (p.combo > p.bestCombo) p.bestCombo = p.combo
                "★ +$STAR_COMBO"
            }
        }
        p.bonusAt = t
        p.fxType = s
        p.fxAt = t
        p.fxNote = k
        if (settings.soundEnabled) audio.playChime(doubleArrayOf(1318.5, 1760.0), gapMs = 50, level = 0.14)
    }

    /** Where the running man is right now, so a new jump starts from there. */
    fun manNow(p: TwoPlayer, t: Long): Pair<Double, Float> {
        val pose = manPose(p, t) ?: return Pair(t.toDouble(), 0f)
        return Pair(pose.at, pose.level)
    }

    fun press(p: TwoPlayer, t: Long) {
        if (p.pressAt > 0L || finished) return
        p.pressAt = t
        p.pressNote = -1
        val k = schedule.nearest(t)
        val counted = t >= schedule.t0 - schedule.round(0).cycleMs / 2 && k in 0 until total && k !in p.hit
        p.pressNoteMs = schedule.noteMs(k.coerceIn(0, total - 1))
        p.pressGrace = grace(k)
        if (k in p.hit) p.combo = 0 // a second press for the same note: no points, and the combo breaks
        if (counted) {
            p.hit.add(k)
            p.pressNote = k
            p.pressOnsetMs = t - schedule.noteStart(k)
            p.pressBlind = t < p.fogUntil
        }
        p.marks.add(Mark(t, 0L, p.pressNoteMs, counted, p.pressGrace))
        while (p.marks.size > 16) p.marks.removeAt(0)

        // The running man jumps onto this press: onto its wave when it is on time; short of it
        // when it came too early or played no note, and then he runs on and climbs the next wave
        val early = !counted || p.pressOnsetMs < -GOOD_MS * p.pressGrace
        val (fromAt, fromLevel) = manNow(p, t)
        p.jump = when {
            p.jumpAt == 0L -> Jump.ENTER
            early -> Jump.SLIP
            else -> Jump.JUMP
        }
        if (p.jump == Jump.ENTER) {
            // The first time he drops from a high step above, onto the first wave pressed
            p.fromAt = t - p.pressNoteMs * 0.4
            p.fromLevel = -0.9f
        } else {
            p.fromAt = fromAt
            p.fromLevel = fromLevel
        }
        p.pendingCollect = -1
        if (early) {
            p.landAt = t
            p.landLow = true
            val next = if (counted) k else schedule.nextAfter(t)
            if (next >= 0) {
                p.runTo = schedule.noteStart(next)
                p.standAt = p.runTo + schedule.noteMs(next) / 4
                p.pendingCollect = next
                p.pendingCollectAt = t + JUMP_MS + SLIP_MS + RUN_MS + CLIMB_MS
            } else {
                p.runTo = 0L
            }
            if (p.jump == Jump.ENTER) p.jump = Jump.SLIP
        } else {
            p.landAt = maxOf(t, schedule.noteStart(k)) + p.pressNoteMs / 3
            p.landLow = false
            p.runTo = 0L
            collect(p, k, t)
        }
        p.jumpAt = t
        p.move = ManMove.NONE

        holdSoundOn(p)
        holdVibration(t)
    }

    fun release(p: TwoPlayer, t: Long) {
        if (p.pressAt == 0L) return
        val held = t - p.pressAt
        p.pressAt = 0L
        holdSoundOff(p)
        holdVibration(t)
        if (p.marks.isNotEmpty()) p.marks[p.marks.lastIndex] = p.marks.last().copy(end = t)
        if (p.pressNote < 0) return
        val k = p.pressNote
        p.pressNote = -1

        val g = p.pressGrace
        val onset = abs(p.pressOnsetMs)
        val base = when {
            onset <= PERFECT_MS * g -> POINTS_PERFECT
            onset <= GOOD_MS * g -> POINTS_GOOD
            else -> POINTS_OK
        }
        val ratio = held.toFloat() / p.pressNoteMs
        val off = abs(ratio - 1f)
        val lengthBonus = when {
            off <= LENGTH_GOOD * g -> BONUS_LENGTH
            off <= LENGTH_OK * g -> BONUS_LENGTH_OK
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
        if (base >= POINTS_GOOD && off <= LENGTH_OK * g) p.goodNotes.add(k)
        p.track(p.pressOnsetMs, off)
        // Played in the Fog, by feel alone: double points
        val mult = p.multiplier(t) * (if (p.pressBlind) 2 else 1)
        val points = (base + lengthBonus) * mult
        p.score += points
        val tooShort = ratio < 1f - LENGTH_GOOD * g
        val verdict = when {
            ratio < 1f - LENGTH_OK * g -> R.string.tp_short
            base == POINTS_PERFECT -> R.string.tp_perfect
            base == POINTS_GOOD -> R.string.tp_good
            p.pressOnsetMs < 0 -> R.string.tp_early
            else -> R.string.tp_late
        }
        p.show(verdict, "+$points" + if (mult > 1) " ×$mult" else "", t)

        // The man: a happy hop for a good note; let go too soon, he is jerked back like by a
        // sudden start
        p.move = when {
            tooShort -> ManMove.LEAN_BACK
            base >= POINTS_GOOD && off <= LENGTH_OK * g -> ManMove.HOP
            else -> ManMove.NONE
        }
        p.moveAt = t
    }

    fun fire(p: TwoPlayer, skill: Skill, t: Long) {
        if (p.charge < CHARGE_PERFECTS || finished) return
        p.charge = 0
        p.skillsUsed++
        val other = players[1 - p.index]
        val until = t + (SKILL_BEATS * schedule.round(schedule.indexAt(t)).pulseMs).toLong()
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

    /** Both players' good notes in round [r] so far set the tempo of the next round. */
    fun decideNextRound(r: Int) {
        val rt = schedule.rounds[r]
        var next = rt.bpm
        if (tempoRises) {
            val notes = r * NOTES_PER_ROUND until r * NOTES_PER_ROUND + NOTES_PER_ROUND - 1
            val good = players.sumOf { p -> notes.count { it in p.goodNotes } }
            val share = good / (2f * (NOTES_PER_ROUND - 1))
            next = when {
                share >= TEMPO_UP_SHARE -> Tempo.clamp(rt.bpm + Tempo.STEP)
                share < TEMPO_DOWN_SHARE && rt.bpm > startBpm -> rt.bpm - Tempo.STEP
                else -> rt.bpm
            }
        }
        if (next != rt.bpm) tempoChangeAt = SystemClock.uptimeMillis()
        schedule.rounds.add(RoundTiming(rt.end, next, note.durationMs(next), ts.pulseMs(next)))
    }

    // The clock: one value per frame for everything drawn. Held notes swell within each cycle
    // and sound again at each new cycle, like on the main play screen.
    LaunchedEffect(Unit) {
        val lap = IntArray(2)
        while (!finished) {
            withFrameMillis { }
            now = SystemClock.uptimeMillis()
            players.forEach { p ->
                if (p.pressAt > 0L && settings.soundEnabled) {
                    val phase = (now - p.pressAt).toFloat() / p.pressNoteMs
                    val volume = 0.05f + 0.4f * (phase - floor(phase))
                    if (phase.toInt() != lap[p.index]) {
                        lap[p.index] = phase.toInt()
                        if (p.index == 0) audio.strike() else audio.strike2()
                    }
                    if (p.index == 0) audio.updateVolume(volume) else audio.updateVolume2(volume)
                } else {
                    lap[p.index] = 0
                }
                // After a slip, the surprise on the wave he climbs is his once he is up
                if (p.pendingCollect >= 0 && now >= p.pendingCollectAt) {
                    val k = p.pendingCollect
                    p.pendingCollect = -1
                    collect(p, k, now)
                }
            }
        }
        players.forEach { release(it, SystemClock.uptimeMillis()) }
        finish()
    }

    // The conductor: count-in, then round after round the beat, the misses and the next tempo
    LaunchedEffect(Unit) {
        fun beats(from: Long, pulseMs: Double, count: Int) = launch {
            for (n in 0 until count) {
                val at = from + (n * pulseMs).toLong()
                delay(at - SystemClock.uptimeMillis())
                beat = n % ts.pulsesPerBar
                val accent = ts.accents[n % ts.pulsesPerBar]
                if (settings.beatSound) audio.playClick(volumeGain(settings.clickVolume), accent)
                if (settings.vibrationEnabled) haptics.tick(accent)
            }
        }
        val first = schedule.rounds[0]
        beats(startAt, first.pulseMs, COUNT_IN_BARS * ts.pulsesPerBar)
        for (r in 0 until roundCount) {
            val rt = schedule.rounds[r]
            beats(rt.start, rt.pulseMs, (NOTES_PER_ROUND * rt.cycleMs / rt.pulseMs).roundToInt())
            for (i in 0 until NOTES_PER_ROUND) {
                val k = r * NOTES_PER_ROUND + i
                if (i == NOTES_PER_ROUND - 1 && r + 1 < roundCount) {
                    delay(schedule.noteStart(k) - SystemClock.uptimeMillis())
                    decideNextRound(r)
                }
                // A note nobody pressed for, half a cycle after it started, is a miss (in the
                // first round it doesn't break the combo)
                delay(schedule.noteStart(k) + rt.cycleMs / 2 - SystemClock.uptimeMillis())
                val t = SystemClock.uptimeMillis()
                players.forEach { p ->
                    if (k !in p.hit) {
                        p.missed++
                        p.track(null, null)
                        if (k >= NOTES_PER_ROUND) p.combo = 0
                        p.show(R.string.tp_miss, "", t)
                    }
                }
            }
            // The round is over: a short rising chime; after the last one, a fanfare
            delay(rt.end - 60 - SystemClock.uptimeMillis())
            if (settings.soundEnabled) {
                if (r + 1 < roundCount) audio.playChime(doubleArrayOf(523.25, 659.25, 783.99), gapMs = 90, level = 0.22)
                else audio.playChime(doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5), gapMs = 110, level = 0.26)
            }
            if (r + 1 == roundCount && settings.vibrationEnabled) haptics.celebrate()
        }
        delay(schedule.endAt + 900 - SystemClock.uptimeMillis())
        finished = true
    }

    // The computer: plays each note about as well as the person has lately (start timing, length,
    // misses), a little better when behind in points and a little worse when ahead
    LaunchedEffect(Unit) {
        val bot = players.firstOrNull { it.isBot } ?: return@LaunchedEffect
        val human = players.first { !it.isBot }
        val rnd = java.util.Random(startAt)
        for (k in 0 until total) {
            val onsets = human.recentOnsets
            val avgOnset = if (onsets.size >= 3) onsets.map { abs(it).toDouble() }.average() else 45.0
            val avgLength = if (human.recentLengths.size >= 3) human.recentLengths.average() else 0.12
            val missRate = if (human.recentHits.size >= 4) human.recentHits.count { !it }.toDouble() / human.recentHits.size else 0.06
            val lead = bot.score - human.score
            val ease = when {
                lead > 400 -> 1.3
                lead < -400 -> 0.8
                else -> 1.0
            }
            if (rnd.nextDouble() < (missRate * ease).coerceIn(0.02, 0.5)) continue
            val cycle = schedule.round(k).cycleMs
            val noteMs = schedule.noteMs(k)
            val onset = (rnd.nextGaussian() * (avgOnset * ease).coerceIn(10.0, 180.0))
                .toLong().coerceIn(-cycle * 2 / 5, cycle * 2 / 5)
            val hold = (noteMs * (1.0 + rnd.nextGaussian() * (avgLength * ease).coerceIn(0.03, 0.4)))
                .toLong().coerceIn(noteMs / 3, cycle * 4 / 5)
            delay(schedule.noteStart(k) + onset - SystemClock.uptimeMillis())
            if (finished) break
            press(bot, SystemClock.uptimeMillis())
            delay(hold)
            release(bot, SystemClock.uptimeMillis())
            // A charged skill goes out now and then
            if (bot.charge >= CHARGE_PERFECTS && bot.skills.isNotEmpty() && rnd.nextDouble() < 0.4) {
                fire(bot, bot.skills[rnd.nextInt(bot.skills.size)], SystemClock.uptimeMillis())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audio.stopTone()
            audio.stopTone2()
            haptics.endHold()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        // The far player's half faces them (the computer's half faces the player)
        val bot = players[1].isBot
        PlayerHalf(
            players[1], now, schedule, surprises, settings.lightEnabled,
            onPress = { press(players[1], it) },
            onRelease = { release(players[1], it) },
            onFire = { fire(players[1], it, SystemClock.uptimeMillis()) },
            interactive = !bot,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(if (bot) 0f else 180f)
        )
        CenterStrip(players, now, schedule, beat, ts, roundCount, tempoChangeAt, onQuit)
        PlayerHalf(
            players[0], now, schedule, surprises, settings.lightEnabled,
            onPress = { press(players[0], it) },
            onRelease = { release(players[0], it) },
            onFire = { fire(players[0], it, SystemClock.uptimeMillis()) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

/** The color of a hold while it lasts: the player's own, green when it is time to let go, red when too long. */
private fun holdColor(p: TwoPlayer, heldMs: Long, noteMs: Long, grace: Float): Color {
    val r = heldMs.toFloat() / noteMs
    return when {
        r > 1f + LENGTH_GOOD * grace -> HoldRed
        r >= 1f - LENGTH_GOOD * grace -> HoldGreen
        else -> p.color
    }
}

/** The color a hold keeps once let go, as on the main play screen (with the first rounds' slack). */
private fun resultColor(ratio: Float, grace: Float): Color {
    val off = abs(ratio - 1f)
    return when {
        off <= LENGTH_GOOD * grace -> ScoreGreen
        off <= 0.3f * grace -> ScoreAmber
        else -> HoldRed
    }
}

@Composable
private fun PlayerHalf(
    p: TwoPlayer,
    now: Long,
    schedule: Schedule,
    surprises: Array<Surprise?>,
    lightEnabled: Boolean,
    onPress: (Long) -> Unit,
    onRelease: (Long) -> Unit,
    onFire: (Skill) -> Unit,
    modifier: Modifier,
    interactive: Boolean = true
) {
    val fogged = now < p.fogUntil
    val frozen = now < p.freezeUntil
    val started = now >= schedule.t0
    val index = schedule.indexAt(now)
    val noteMs = schedule.noteMs(index.coerceAtLeast(0))
    val pos = if (started) now - schedule.noteStart(index) else 0L
    val inNote = started && index < schedule.total && pos < noteMs
    val holding = p.pressAt > 0L
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val padLabel = stringResource(R.string.tp_cd_pad, p.name)

    Column(
        modifier
            .background(p.color.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Name, shield, surprises, score, combo
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
            if (p.surprises > 0) {
                Text(
                    "✦ ${p.surprises}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarGold,
                    modifier = Modifier.padding(end = 10.dp)
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
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .semantics { contentDescription = padLabel }
                    .pointerInput(interactive) {
                        if (!interactive) return@pointerInput
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
                val holdFill = if (holding) holdColor(p, now - p.pressAt, p.pressNoteMs, p.pressGrace) else p.color
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
                                p.color, -90f, 360f * pos / noteMs, useCenter = false,
                                topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r),
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    val padR = r - stroke * 1.6f
                    val flash = lightEnabled && !fogged && inNote && pos < 140
                    val fill = when {
                        holding -> holdFill
                        fogged -> GuideGray.copy(alpha = 0.4f)
                        flash -> p.color.copy(alpha = 0.6f)
                        else -> p.color.copy(alpha = 0.3f)
                    }
                    drawCircle(fill, padR)
                    if (flash) drawCircle(Color.White.copy(alpha = 0.7f * (1f - pos / 140f)), padR, style = Stroke(stroke * 0.6f))
                }
                val label = when {
                    !started -> ceil((schedule.t0 - now) / schedule.round(0).pulseMs).toInt().coerceAtLeast(1).toString()
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
            if (p.bonusText.isNotEmpty() && now - p.bonusAt < VERDICT_MS) {
                Text(
                    p.bonusText,
                    color = StarGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }

        WaveGraph(p, now, schedule, surprises, fogged, Modifier.fillMaxWidth().height(76.dp))
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.skills.forEach { skill ->
                SkillButton(skill, p.charge, p.color, Modifier.weight(1f), enabled = interactive) { onFire(skill) }
            }
        }
    }
}

/**
 * The guide as a square wave sliding by (up = note, down = rest), each press filling the wave in
 * the color of how it went, the surprises on some waves, the marker that slides down the "now"
 * line as the next wave comes, and the running man hopping from press to press on top of it all.
 */
@Composable
private fun WaveGraph(
    p: TwoPlayer,
    now: Long,
    schedule: Schedule,
    surprises: Array<Surprise?>,
    fogged: Boolean,
    modifier: Modifier
) {
    Canvas(modifier) {
        val k = schedule.indexAt(now)
        val cycle = schedule.round(k.coerceAtLeast(0)).cycleMs
        val window = cycle * 4f
        val nowX = size.width * 0.6f
        fun xAt(t: Double): Float = nowX + ((t - now) / window * size.width).toFloat()
        fun x(t: Long): Float = xAt(t.toDouble())
        val top = 10.dp.toPx()
        val high = size.height * 0.45f
        val low = size.height * 0.9f
        drawRoundRect(p.color.copy(alpha = 0.08f), cornerRadius = CornerRadius(8.dp.toPx()))

        // Presses: the wave filled from the bottom, colored as it went
        p.marks.forEach { m ->
            val end = if (m.end == 0L) now else m.end
            val x1 = x(m.start)
            val x2 = x(end)
            if (x2 >= 0f && x1 <= size.width) {
                val color = when {
                    !m.counted -> GuideGray.copy(alpha = 0.7f)
                    m.end == 0L -> holdColor(p, end - m.start, m.noteMs, m.grace)
                    else -> resultColor((end - m.start).toFloat() / m.noteMs, m.grace)
                }
                drawRect(color.copy(alpha = 0.85f), topLeft = Offset(x1, high), size = Size(maxOf(3f, x2 - x1), low - high))
            }
        }

        val from = (k - 4).coerceAtLeast(0)
        val to = (k + 4).coerceAtMost(schedule.total - 1)
        if (!fogged) {
            // The guide
            val path = Path()
            path.moveTo(0f, low)
            for (n in from..to) {
                val s = schedule.noteStart(n)
                val e = s + schedule.noteMs(n)
                if (x(e) < 0f || x(s) > size.width) continue
                path.lineTo(x(s), low)
                path.lineTo(x(s), high)
                path.lineTo(x(e), high)
                path.lineTo(x(e), low)
            }
            path.lineTo(size.width, low)
            drawPath(path, GuideGray, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

            // Surprises still to take, bobbing above their waves
            for (n in from..to) {
                val s = surprises[n] ?: continue
                if (n in p.collectedNotes) continue
                val c = Offset(x(schedule.noteStart(n) + schedule.noteMs(n) / 2), high - 11.dp.toPx() + 2.dp.toPx() * sin(now / 220f + n))
                if (c.x < -20f || c.x > size.width + 20f) continue
                drawSurprise(s, c, 1f, 1f)
            }
        }
        // The one just taken flies up and fades
        val fx = now - p.fxAt
        if (p.fxAt > 0L && fx < COLLECT_FX_MS && p.fxNote >= 0) {
            val f = fx.toFloat() / COLLECT_FX_MS
            val c = Offset(x(schedule.noteStart(p.fxNote) + schedule.noteMs(p.fxNote) / 2), high - 11.dp.toPx() - 26.dp.toPx() * f)
            drawSurprise(p.fxType, c, 1f + 0.6f * f, 1f - f)
        }

        // The "now" line, and a marker on it that slides down as the next wave comes: it reaches
        // the top of the wave just as the note starts (hidden in the Fog)
        drawLine(Ink.copy(alpha = 0.7f), Offset(nowX, 0f), Offset(nowX, size.height), strokeWidth = 1.5.dp.toPx())
        if (!fogged) {
            val inNoteNow = now >= schedule.t0 && k < schedule.total && now - schedule.noteStart(k) < schedule.noteMs(k)
            val markerY: Float? = if (inNoteNow) high else {
                val next = if (now < schedule.t0) 0 else k + 1
                if (next >= schedule.total) null else {
                    val start = if (now < schedule.t0) schedule.countInStart else schedule.noteStart(k) + schedule.noteMs(k)
                    val end = schedule.noteStart(next)
                    val f = ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                    top + (high - top) * f
                }
            }
            if (markerY != null) {
                val w = 6.dp.toPx()
                val tri = Path().apply {
                    moveTo(nowX - w, markerY - 1.6f * w)
                    lineTo(nowX + w, markerY - 1.6f * w)
                    lineTo(nowX, markerY)
                    close()
                }
                drawPath(tri, if (inNoteNow) p.color else StarGold)
            }
        }

        drawRunningMan(p, now, ::xAt, high, low)
    }
}

/** A coin, a bolt or a star at [c]. */
private fun DrawScope.drawSurprise(s: Surprise, c: Offset, scale: Float, alpha: Float) {
    val r = 6.dp.toPx() * scale
    val a = alpha.coerceIn(0f, 1f)
    when (s) {
        Surprise.COIN -> {
            drawCircle(StarGold.copy(alpha = a), r, c)
            drawCircle(Color(0xFF8A5A00).copy(alpha = a), r * 0.6f, c, style = Stroke(r * 0.22f))
        }
        Surprise.BOLT -> {
            val bolt = Path().apply {
                moveTo(c.x + r * 0.3f, c.y - r * 1.2f)
                lineTo(c.x - r * 0.6f, c.y + r * 0.1f)
                lineTo(c.x, c.y + r * 0.1f)
                lineTo(c.x - r * 0.3f, c.y + r * 1.2f)
                lineTo(c.x + r * 0.6f, c.y - r * 0.15f)
                lineTo(c.x, c.y - r * 0.15f)
                close()
            }
            drawPath(bolt, BoltYellow.copy(alpha = a))
        }
        Surprise.STAR -> {
            val star = Path()
            for (i in 0 until 10) {
                val rr = if (i % 2 == 0) r * 1.15f else r * 0.48f
                val ang = -PI / 2 + i * PI / 5
                val pt = Offset(c.x + rr * cos(ang).toFloat(), c.y + rr * sin(ang).toFloat())
                if (i == 0) star.moveTo(pt.x, pt.y) else star.lineTo(pt.x, pt.y)
            }
            star.close()
            drawPath(star, StarViolet.copy(alpha = a))
        }
    }
}

/** What the running man looks like at one moment. */
private enum class ManShape { STAND, AIR, RUN, CLIMB, LEAN_BACK, SLIP }

/**
 * Where the man is ([at]: a time on the wave's timeline; [level]: 0 on top of a wave, 1 down
 * below, negative above), how high off the ground ([lift], dp), his lean and his shape.
 */
private class ManPose(val at: Double, val level: Float, val lift: Float, val tilt: Float, val shape: ManShape, val phase: Float)

private fun manPose(p: TwoPlayer, now: Long): ManPose? {
    if (p.jumpAt == 0L) return null
    var e = now - p.jumpAt
    val landLevel = if (p.landLow) 1f else 0f
    if (e < JUMP_MS) {
        // In the air: an arc from where he stood (the first time, from a high step above)
        val f = e.toFloat() / JUMP_MS
        val lift = (if (p.fromLevel < 0f) 6f else 16f) * 4f * f * (1f - f)
        return ManPose(p.fromAt + (p.landAt - p.fromAt) * f, p.fromLevel + (landLevel - p.fromLevel) * f, lift, 0f, ManShape.AIR, 0f)
    }
    e -= JUMP_MS
    var base = ManPose(p.landAt.toDouble(), landLevel, 0f, 0f, ManShape.STAND, 0f)
    if (p.jump == Jump.SLIP) {
        // Landed short of the wave: slips and leans back, then runs to the next wave and climbs it
        if (e < SLIP_MS) return ManPose(p.landAt.toDouble(), 1f, 0f, -30f * sin(PI.toFloat() * e / SLIP_MS), ManShape.SLIP, 0f)
        e -= SLIP_MS
        if (p.runTo > 0L) {
            if (e < RUN_MS) {
                val f = e.toDouble() / RUN_MS
                return ManPose(p.landAt + (p.runTo - p.landAt) * f, 1f, 0f, 8f, ManShape.RUN, e / 75f)
            }
            e -= RUN_MS
            if (e < CLIMB_MS) {
                val f = e.toFloat() / CLIMB_MS
                return ManPose(p.runTo.toDouble(), 1f - f, 0f, 6f, ManShape.CLIMB, e / 80f)
            }
            base = ManPose(p.standAt.toDouble(), 0f, 0f, 0f, ManShape.STAND, 0f)
        }
    }
    val mt = now - p.moveAt
    return when {
        p.move == ManMove.HOP && mt < HOP_MS ->
            ManPose(base.at, base.level, 12f * sin(PI.toFloat() * mt / HOP_MS), 0f, ManShape.AIR, 0f)
        p.move == ManMove.LEAN_BACK && mt < LEAN_MS ->
            ManPose(base.at, base.level, 0f, -28f * sin(PI.toFloat() * mt / LEAN_MS), ManShape.LEAN_BACK, 0f)
        else -> base
    }
}

/**
 * The running man stands on the last press and is carried away with it as the wave slides on;
 * he leaves the graph with it when the player stops. A new press makes him jump onto it.
 */
private fun DrawScope.drawRunningMan(p: TwoPlayer, now: Long, x: (Double) -> Float, high: Float, low: Float) {
    val pose = manPose(p, now) ?: return
    val h = 22.dp.toPx()
    val foot = Offset(x(pose.at), high + (low - high) * pose.level - pose.lift.dp.toPx())
    if (foot.x < -h || foot.x > size.width + h) return
    val s = sin(pose.phase * 2f * PI.toFloat())
    // Hands and feet, in units of his height, from his feet (x forward, y down)
    val (arms, legs) = when (pose.shape) {
        ManShape.STAND -> floatArrayOf(-0.22f, -0.52f, 0.22f, -0.52f) to floatArrayOf(-0.17f, 0f, 0.17f, 0f)
        ManShape.AIR -> floatArrayOf(-0.24f, -0.98f, 0.24f, -0.98f) to floatArrayOf(-0.1f, -0.12f, 0.1f, -0.12f)
        ManShape.RUN -> floatArrayOf(-0.22f * s, -0.56f, 0.22f * s, -0.56f) to
            floatArrayOf(0.22f * s, -0.06f * maxOf(0f, s), -0.22f * s, -0.06f * maxOf(0f, -s))
        ManShape.CLIMB -> floatArrayOf(0.2f, -1.0f + 0.08f * s, 0.28f, -0.92f - 0.08f * s) to
            floatArrayOf(0.12f, -0.14f * abs(s), -0.04f, 0f)
        ManShape.LEAN_BACK -> floatArrayOf(0.32f, -0.64f, 0.28f, -0.58f) to floatArrayOf(-0.12f, 0f, 0.22f, 0f)
        ManShape.SLIP -> floatArrayOf(-0.34f, -0.74f, 0.34f, -0.8f) to floatArrayOf(0.24f, 0f, -0.06f, 0f)
    }
    drawFigure(foot, h, Ink, pose.tilt, arms, legs)
}

/**
 * A little stick figure standing at [foot], [h] tall, leaning by [tilt] degrees (+ = forward).
 * [arms] and [legs] are the two hands and the two feet as x, y pairs in units of [h] from his
 * feet; [headDrop] lowers the head (a sad figure).
 */
private fun DrawScope.drawFigure(
    foot: Offset, h: Float, color: Color, tilt: Float,
    arms: FloatArray, legs: FloatArray, headDrop: Float = 0f
) {
    drawRotate(tilt, pivot = foot) {
        val w = h * 0.1f
        val hip = foot + Offset(0f, -h * 0.42f)
        val neck = foot + Offset(h * headDrop * 0.4f, -h * (0.76f - headDrop * 0.3f))
        val head = neck + Offset(h * headDrop * 0.6f, -h * (0.14f - headDrop))
        drawLine(color, hip, foot + Offset(legs[0] * h, legs[1] * h), w, StrokeCap.Round)
        drawLine(color, hip, foot + Offset(legs[2] * h, legs[3] * h), w, StrokeCap.Round)
        drawLine(color, hip, neck, w, StrokeCap.Round)
        drawLine(color, neck, foot + Offset(arms[0] * h, arms[1] * h), w, StrokeCap.Round)
        drawLine(color, neck, foot + Offset(arms[2] * h, arms[3] * h), w, StrokeCap.Round)
        drawCircle(color, h * 0.12f, head)
    }
}

@Composable
private fun SkillButton(skill: Skill, charge: Int, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val ready = charge >= CHARGE_PERFECTS
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(skill.nameRes)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (ready) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(if (ready) 2.dp else 1.dp, if (ready) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .clickable(enabled = ready && enabled, role = Role.Button, onClick = onClick),
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

/** Between the halves: the beat, Stop, the round, the tempo, and who is ahead. */
@Composable
private fun CenterStrip(
    players: List<TwoPlayer>,
    now: Long,
    schedule: Schedule,
    beat: Int,
    ts: TimeSignature,
    rounds: Int,
    tempoChangeAt: Long,
    onQuit: () -> Unit
) {
    val index = schedule.indexAt(now).coerceIn(0, schedule.total - 1)
    val round = (index / NOTES_PER_ROUND + 1).coerceAtMost(rounds)
    val bpm = schedule.round(index).bpm
    val tempoNews = tempoChangeAt > 0L && now - tempoChangeAt < 2500L
    val warmUp = grace(index) > 1f
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until ts.pulsesPerBar) {
                    val lit = beat >= 0 && beat == i
                    Box(
                        Modifier
                            .size(if (lit) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(if (lit) StarGold else GuideGray.copy(alpha = 0.6f))
                    )
                }
            }
            Text(
                if (warmUp) "$bpm BPM · " + stringResource(R.string.tp_warmup) else "$bpm BPM",
                fontSize = 13.sp,
                fontWeight = if (tempoNews) FontWeight.Bold else FontWeight.Normal,
                color = if (tempoNews) StarGold else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
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

/** How a player feels about the result, shown by their little man. */
private enum class Feeling { WIN, PROUD, SAD, SHRUG }

/** A line of the results that got better: against the last match, or from the first round to the last. */
private class Improvement(@StringRes val label: Int, val value: String)

private fun improvementOf(p: TwoPlayer, rounds: Int): Improvement? {
    if (p.hasPrev) {
        if (p.score > p.prevScore) return Improvement(R.string.tp_imp_points, "▲ +%,d".format(p.score - p.prevScore))
        if (p.perfect > p.prevPerfect) return Improvement(R.string.tp_perfect, "${p.perfect}  ▲ +${p.perfect - p.prevPerfect}")
        if (p.missed < p.prevMissed) return Improvement(R.string.tp_stat_missed, "${p.missed}  ▼ ${p.prevMissed - p.missed}")
        if (p.bestCombo > p.prevCombo) return Improvement(R.string.tp_stat_combo, "${p.bestCombo}  ▲ +${p.bestCombo - p.prevCombo}")
    }
    if (rounds > 1) {
        val first = p.goodInRound(0)
        val last = p.goodInRound(rounds - 1)
        if (last > first) return Improvement(R.string.tp_imp_last_round, "$last/$NOTES_PER_ROUND  ▲ +${last - first}")
    }
    return null
}

@Composable
private fun TwoPlayerResults(
    players: List<TwoPlayer>,
    rounds: Int,
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
    // A clock for the little men's moves
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { }
            t = SystemClock.uptimeMillis() - start
        }
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
                players.forEach { p ->
                    val improvement = improvementOf(p, rounds)
                    val feeling = when {
                        winner == p -> Feeling.WIN
                        improvement != null -> Feeling.PROUD
                        winner == null -> Feeling.SHRUG
                        else -> Feeling.SAD
                    }
                    StatCard(p, feeling, improvement, t, Modifier.weight(1f))
                }
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
private fun StatCard(p: TwoPlayer, feeling: Feeling, improvement: Improvement?, t: Long, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(p.color))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = p.color, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("%,d".format(p.score), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                // Without a line that got better, the man stands by the name
                if (improvement == null) FeelingMan(feeling, p.color, t, Modifier.size(44.dp))
            }
            StatRow(stringResource(R.string.tp_perfect), p.perfect.toString())
            StatRow(stringResource(R.string.tp_good), p.good.toString())
            StatRow(stringResource(R.string.tp_stat_missed), p.missed.toString())
            StatRow(stringResource(R.string.tp_stat_combo), p.bestCombo.toString())
            StatRow(stringResource(R.string.tp_stat_surprises), p.surprises.toString())
            StatRow(stringResource(R.string.tp_stat_skills), p.skillsUsed.toString())
            if (p.blocked > 0) StatRow(stringResource(R.string.tp_stat_blocked), p.blocked.toString())
            if (improvement != null) {
                // What got better, lit up, with the man pointing at it
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StarGold.copy(alpha = 0.18f))
                        .border(1.dp, StarGold, RoundedCornerShape(10.dp))
                        .padding(start = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(stringResource(improvement.label), fontSize = 13.sp, color = Ink)
                        Text(improvement.value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StarGold)
                    }
                    FeelingMan(feeling, p.color, t, Modifier.size(44.dp))
                }
            }
        }
    }
}

/** A little man who shows how the player feels: cheering, proud and pointing, shrugging or sad. */
@Composable
private fun FeelingMan(feeling: Feeling, color: Color, t: Long, modifier: Modifier) {
    // He points toward the start of the line, which is on the right in Hebrew
    val toStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    Canvas(modifier) {
        val h = size.height * 0.72f
        val beatPhase = (t % 900L) / 900f
        val s = sin(beatPhase * 2f * PI.toFloat())
        var foot = Offset(size.width / 2, size.height * 0.94f)
        when (feeling) {
            Feeling.WIN -> { // jumps for joy, arms high
                foot += Offset(0f, -h * 0.25f * abs(s))
                drawFigure(foot, h, color, 0f,
                    floatArrayOf(-0.32f, -1.08f, 0.32f, -1.08f),
                    if (abs(s) > 0.2f) floatArrayOf(-0.1f, -0.1f, 0.1f, -0.1f) else floatArrayOf(-0.17f, 0f, 0.17f, 0f))
            }
            Feeling.PROUD -> { // bounces a little and points at what got better
                foot += Offset(0f, -h * 0.06f * maxOf(0f, s))
                drawFigure(foot, h, color, 0f,
                    floatArrayOf(toStart * 0.48f, -0.7f + 0.05f * s, -toStart * 0.16f, -0.42f),
                    floatArrayOf(-0.17f, 0f, 0.17f, 0f))
            }
            Feeling.SHRUG -> { // shoulders up and down
                val up = 0.08f * maxOf(0f, s)
                drawFigure(foot, h, color, 0f,
                    floatArrayOf(-0.3f, -0.74f - up, 0.3f, -0.74f - up),
                    floatArrayOf(-0.17f, 0f, 0.17f, 0f))
            }
            Feeling.SAD -> { // head down, arms hanging, a slow sway
                drawFigure(foot, h, color.copy(alpha = 0.8f), 6f + 3f * s,
                    floatArrayOf(-0.08f, -0.32f, 0.1f, -0.32f),
                    floatArrayOf(-0.12f, 0f, 0.12f, 0f), headDrop = 0.12f)
            }
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
