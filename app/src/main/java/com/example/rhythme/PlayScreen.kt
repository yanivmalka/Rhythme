package com.example.rhythme

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

// Pitches for the hold tone: C4 major scale up to C5 (names in R.array.pitch_names)
internal val PITCHES_HZ = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
internal const val DEFAULT_PITCH = 5 // A4

private const val GOOD_WINDOW = 0.13f      // +/-13% of target = the 80% accuracy bar in ProgressManager
private const val ONSET_TOLERANCE = 0.2f   // press within 20% of a beat counts as "on the beat"
private const val TEMPO_SETTLE_MS = 500L   // tempo controls apply once they rest this long
private const val IDLE_RESET_MS = 10_000L  // idle this long: hint and full beat come back
private const val HOLDS_TO_HIDE_HINT = 2
private const val MIN_HOLD_MS = 60L        // shorter presses are treated as accidental
private const val AUTO_STOP_MS = 60_000L   // playing stops by itself after this long without a press or a change
private const val PANEL_HIDE_MS = 4000L    // the practice panel leaves after this long without a press
private const val ONSET_OK_MS = 40L        // pressing this close to the guide's note counts as "on time"

private val PadBlue = Color(0.2f, 0.5f, 0.9f)
internal val FillBlue = Color(0xFF6FB1FF) // the pad "fills" with this from the bottom
private val RingBlue = Color(0xFF7FD4FF)
private val ReleaseGreen = Color(0xFF34C759)
private val OverRed = Color(0xFFFF6B6B)
private val RestGray = Color(0xFF9AA4B2) // the ring while it counts a rest down

/**
 * What successive notes play: the same note every time, or one step after another up and
 * down a scale that starts on the chosen note. [semitones] are counted from that note.
 */
enum class Scale(@StringRes val nameRes: Int, val semitones: IntArray) {
    NONE(R.string.scale_none, intArrayOf(0)),
    MAJOR(R.string.scale_major, intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)),
    MINOR(R.string.scale_minor, intArrayOf(0, 2, 3, 5, 7, 8, 10, 12)),
    PENTATONIC(R.string.scale_pentatonic, intArrayOf(0, 2, 4, 7, 9, 12)),
    BLUES(R.string.scale_blues, intArrayOf(0, 3, 5, 6, 7, 10, 12));

    /** Pitch of the [step]-th note: up the scale, back down, and around again. */
    fun hz(baseHz: Double, step: Int): Double {
        if (semitones.size < 2) return baseHz
        val period = 2 * (semitones.size - 1)
        val i = step % period
        val index = if (i < semitones.size) i else period - i
        return baseHz * Math.pow(2.0, semitones[index] / 12.0)
    }
}

/** Demo mode: the sound of the player's own press, to tell it from the guide's tone. */
enum class TouchSound(@StringRes val nameRes: Int) {
    BELL(R.string.touch_sound_bell),
    DOUBLE_BELL(R.string.touch_sound_double_bell),
    CLICK(R.string.touch_sound_click),
    OFF(R.string.touch_sound_off)
}

/**
 * Plays [sound] for a press ([release] = false) or, softer and lower, for letting go.
 * [pitchIndex] is the note chosen for it (see [PITCHES_HZ]); the bell rings an octave above.
 */
fun AudioManager.playTouchSound(sound: TouchSound, pitchIndex: Int, release: Boolean = false) {
    val level = if (release) 0.14 else 0.3
    val hz = PITCHES_HZ[pitchIndex.coerceIn(PITCHES_HZ.indices)] * if (release) 1.5 else 2.0
    when (sound) {
        TouchSound.BELL -> playChime(doubleArrayOf(hz), level = level)
        TouchSound.DOUBLE_BELL -> playChime(doubleArrayOf(hz, hz * 1.5), gapMs = 70, level = level)
        TouchSound.CLICK -> playClick(if (release) 0.5f else 1f, accent = if (release) 0 else 2)
        TouchSound.OFF -> Unit
    }
}

/** A low, soft "dong" where a note ends and its rest begins, an octave below the chosen note. */
fun AudioManager.playRestCue(pitchIndex: Int) =
    playChime(doubleArrayOf(PITCHES_HZ[pitchIndex.coerceIn(PITCHES_HZ.indices)] / 2), level = 0.3)

/** Player choices: shared by all screens and saved across app launches. */
class PlaySettings(private val prefs: SharedPreferences) {
    private val sound = mutableStateOf(prefs.getBoolean("sound", true))
    private val vibration = mutableStateOf(prefs.getBoolean("vibration", true))
    private val light = mutableStateOf(prefs.getBoolean("light", true))
    private val pitch = mutableIntStateOf(prefs.getInt("pitch", DEFAULT_PITCH).coerceIn(PITCHES_HZ.indices))
    private val note = mutableStateOf(enumOr(prefs.getString("note", null), NoteLength.QUARTER))
    private val voice = mutableStateOf(enumOr(prefs.getString("instrument", null), Instrument.PURE))
    private val name = mutableStateOf(prefs.getString("profile_name", "") ?: "")
    private val meter = mutableStateOf(enumOr(prefs.getString("time_signature", null), TimeSignature.FOUR_FOUR))
    private val click = mutableStateOf(prefs.getBoolean("beat_sound", true))
    private val demo = mutableStateOf(prefs.getBoolean("demo_mode", false))
    private val restCue = mutableStateOf(prefs.getBoolean("rest_sound", true))
    private val restPitchState = mutableIntStateOf(prefs.getInt("rest_pitch", DEFAULT_PITCH).coerceIn(PITCHES_HZ.indices))
    private val touchPitchState = mutableIntStateOf(prefs.getInt("touch_pitch", DEFAULT_PITCH).coerceIn(PITCHES_HZ.indices))
    private val vibStrength = mutableIntStateOf(prefs.getInt("vibration_strength", 100).coerceIn(10, 100))
    private val fade = mutableStateOf(prefs.getBoolean("beat_fade", false))
    private val scaleState = mutableStateOf(enumOr(prefs.getString("scale", null), Scale.NONE))
    private val touch = mutableStateOf(enumOr(prefs.getString("touch_sound", null), TouchSound.BELL))
    private val rest = mutableStateOf(NoteLength.entries.firstOrNull { it.name == prefs.getString("rest", null) })

    var soundEnabled: Boolean get() = sound.value; set(v) { sound.value = v; prefs.edit().putBoolean("sound", v).apply() }
    var vibrationEnabled: Boolean get() = vibration.value; set(v) { vibration.value = v; prefs.edit().putBoolean("vibration", v).apply() }
    var lightEnabled: Boolean get() = light.value; set(v) { light.value = v; prefs.edit().putBoolean("light", v).apply() }
    var pitchIndex: Int get() = pitch.intValue; set(v) { pitch.intValue = v; prefs.edit().putInt("pitch", v).apply() }
    var freeNote: NoteLength get() = note.value; set(v) { note.value = v; prefs.edit().putString("note", v.name).apply() }
    var instrument: Instrument get() = voice.value; set(v) { voice.value = v; prefs.edit().putString("instrument", v.name).apply() }
    var profileName: String get() = name.value; set(v) { name.value = v; prefs.edit().putString("profile_name", v).apply() }
    var timeSignature: TimeSignature get() = meter.value; set(v) { meter.value = v; prefs.edit().putString("time_signature", v.name).apply() }
    var beatSound: Boolean get() = click.value; set(v) { click.value = v; prefs.edit().putBoolean("beat_sound", v).apply() }
    /** Demo mode: the app plays the rhythm (one cue where each note starts) and the player imitates it. */
    var demoMode: Boolean get() = demo.value; set(v) { demo.value = v; prefs.edit().putBoolean("demo_mode", v).apply() }
    /** A sound where the note ends and the rest begins. */
    var restSound: Boolean get() = restCue.value; set(v) { restCue.value = v; prefs.edit().putBoolean("rest_sound", v).apply() }
    /** The note of the rest sound and of the press sound (indices into [PITCHES_HZ]). */
    var restPitch: Int get() = restPitchState.intValue; set(v) { restPitchState.intValue = v; prefs.edit().putInt("rest_pitch", v).apply() }
    var touchPitch: Int get() = touchPitchState.intValue; set(v) { touchPitchState.intValue = v; prefs.edit().putInt("touch_pitch", v).apply() }
    /** Vibration strength in percent, 10..100 (motors with amplitude control only). */
    var vibrationStrength: Int get() = vibStrength.intValue; set(v) { vibStrength.intValue = v; prefs.edit().putInt("vibration_strength", v).apply() }
    /** The beat grows quieter as the player locks in, down to silence. Off: the beat always plays. */
    var beatFade: Boolean get() = fade.value; set(v) { fade.value = v; prefs.edit().putBoolean("beat_fade", v).apply() }
    /** Successive notes walk up and down this scale ([Scale.NONE]: always the same note). */
    var scale: Scale get() = scaleState.value; set(v) { scaleState.value = v; prefs.edit().putString("scale", v.name).apply() }
    /** Demo mode: the sound of the player's own press. */
    var touchSound: TouchSound get() = touch.value; set(v) { touch.value = v; prefs.edit().putString("touch_sound", v.name).apply() }
    /** Rest between notes; null = as long as the note itself. */
    var restNote: NoteLength? get() = rest.value; set(v) { rest.value = v; prefs.edit().putString("rest", v?.name).apply() }

    private companion object {
        inline fun <reified T : Enum<T>> enumOr(saved: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == saved } ?: default
    }
}

/**
 * One counted hold: how long it was, against what target, and its score.
 * [onsetMs]: how far the press was from the start of the guide's note; + = late.
 */
data class HoldResult(val heldMs: Long, val targetMs: Long, val accuracy: Float, val onsetMs: Long? = null) {
    val ratio: Float get() = heldMs.toFloat() / targetMs // 1.0 = exactly on target
}

@Composable
fun RhythmPlayScreen(
    settings: PlaySettings,
    hapticManager: HapticManager,
    audioManager: AudioManager,
    progressManager: ProgressManager,
    currentLesson: Lesson?,
    onOpenLessons: () -> Unit,
    onOpenMenu: () -> Unit,
    onLessonChange: (Lesson) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Toggles and choices
    var soundEnabled by settings::soundEnabled
    var vibrationEnabled by settings::vibrationEnabled
    var lightEnabled by settings::lightEnabled
    var pitchIndex by settings::pitchIndex
    var freeNote by settings::freeNote
    val targetNote = currentLesson?.targetNoteLength ?: freeNote
    var demoMode by settings::demoMode
    var restChoice by settings::restNote
    val restNote = restChoice ?: targetNote // by default a rest lasts as long as the note

    // Transport and tempo: controls move pendingBpm; the beat follows playBpm once it settles
    var isPlaying by remember { mutableStateOf(false) }
    val initialBpm = currentLesson?.targetBpm ?: Tempo.DEFAULT
    var pendingBpm by remember { mutableIntStateOf(initialBpm) }
    var playBpm by remember { mutableIntStateOf(initialBpm) }

    // Beat
    var beatTick by remember { mutableIntStateOf(0) }
    var lastBeatAt by remember { mutableLongStateOf(0L) }
    var clickVolume by remember { mutableFloatStateOf(1f) } // fades as the player locks in
    var beatInBar by remember { mutableIntStateOf(-1) } // pulse index in the bar of the last beat (0 = beat 1)
    val timeSignature = settings.timeSignature
    val ripple = remember { Animatable(1f) }

    // Hold
    var isHolding by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var restStartedAt by remember { mutableLongStateOf(0L) } // when the last hold was released; 0 = no rest to count down
    var restEndsAt by remember { mutableLongStateOf(0L) }    // when the next note is due: the guide's next note while playing
    var pressOnsetError by remember { mutableFloatStateOf(1f) }
    var pressSolo by remember { mutableStateOf(false) } // no audible beat at press: counts toward ★★★
    var pressBarPulse by remember { mutableIntStateOf(0) } // pulse of the bar the hold started on (0 = beat 1)

    // "Press & Hold" hint
    var hintVisible by remember { mutableStateOf(true) }
    var holdsSinceHint by remember { mutableIntStateOf(0) }
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }

    // One clock for everything that moves (ring, pad fill, graph, panel). It is read only while
    // drawing, and runs from the last activity until a minute after the graph has emptied.
    val clock = remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val now = remember { { clock.longValue } }
    var lastActivityAt by remember { mutableLongStateOf(0L) }
    var stopAt by remember { mutableLongStateOf(0L) } // after Stop the graph keeps scrolling until here, when the drawn wave is gone

    // The rhythm as a timeline: the guide (note, rest, note...) and the player's presses
    val anchors = remember { mutableStateListOf<GuideAnchor>() }
    val presses = remember { mutableStateListOf<PressMark>() }
    var guideRunning by remember { mutableStateOf(false) } // the guide is moving: true while playing
    val flash = remember { Animatable(0f) } // demo mode: the pad flashes where a note starts
    val touchFlash = remember { Animatable(0f) } // demo mode: the pad's rim lights up at the player's own press

    // Practice panel (slides over the controls while practicing)
    var panelShown by remember { mutableStateOf(false) }
    var panelDismissed by remember { mutableStateOf(false) }

    // Results and lesson progress
    val lesson by rememberUpdatedState(currentLesson)
    var lastResult by remember { mutableStateOf<HoldResult?>(null) }
    val history = remember { mutableStateListOf<HoldResult>() } // this session's holds
    var lessonProgress by remember {
        mutableStateOf(currentLesson?.let { progressManager.getProgress(it.id) })
    }
    var celebrateStars by remember { mutableIntStateOf(0) } // > 0 shows the celebration

    // Swiping to another lesson keeps the beat going; only lesson-specific state resets
    LaunchedEffect(currentLesson?.id) {
        currentLesson?.timeSignature?.let { settings.timeSignature = it }
        lastResult = null
        history.clear()
        lessonProgress = currentLesson?.let { progressManager.getProgress(it.id) }
    }

    val targetMs = targetNote.durationMs(playBpm)
    val sliderSign by rememberUpdatedState(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f)
    val currentTargetMs by rememberUpdatedState(targetMs)
    val restMs = restNote.durationMs(playBpm)
    val currentRestMs by rememberUpdatedState(restMs)

    /** New stretch of the guide from [a].at on; anything planned after that moment is dropped. */
    fun setAnchor(a: GuideAnchor) {
        while (anchors.isNotEmpty() && anchors.last().at >= a.at) anchors.removeAt(anchors.lastIndex)
        anchors.add(a)
        while (anchors.size > 24) anchors.removeAt(0)
    }

    // The graph shows 8 beats (at least two note + rest cycles): a faster tempo zooms in, so the
    // spacing on screen looks alike at every tempo
    val windowMs = maxOf(8 * 60000L / playBpm, 2 * (targetMs + restMs))

    val currentWindowMs by rememberUpdatedState(windowMs)

    /** The stretch of the guide in force at [t] (a change may already be queued after it). */
    fun guideAt(t: Long): GuideAnchor? = anchors.lastOrNull { it.at <= t }?.takeIf { it.active && t < it.until }

    /** Play: the beat, the guide and the graph start together at [t], on beat 1. */
    fun startPlaying(t: Long = SystemClock.uptimeMillis()) {
        lastActivityAt = t
        stopAt = 0L
        setAnchor(GuideAnchor(t, currentTargetMs, currentRestMs))
        isPlaying = true
        hintVisible = true
        holdsSinceHint = 0
        clickVolume = 1f
    }

    /**
     * Stop: the beat, the ring around the pad and the demo's cues stop at once. The graph keeps
     * scrolling until the wave already drawn on it has left the screen, then it rests too.
     */
    fun stopPlaying() {
        if (!isPlaying) return
        isPlaying = false
        restStartedAt = 0L
        val a = anchors.lastOrNull()?.takeIf { it.active && it.until == Long.MAX_VALUE } ?: return
        val t = SystemClock.uptimeMillis()
        val drawnUntil = t + ((1f - GRAPH_NOW_AT) * currentWindowMs).toLong()
        var end = a.at + ((drawnUntil - a.at) / a.cycleMs) * a.cycleMs // last whole cycle on screen
        if (end <= t) end = drawnUntil
        anchors[anchors.lastIndex] = a.copy(until = end)
        stopAt = end + (GRAPH_NOW_AT * currentWindowMs).toLong()
    }

    // Stop the beat (and its vibrations) when the app leaves the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { // everything stops, the graph too
                isPlaying = false
                stopAt = 0L
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Each new note takes the next step of the chosen scale (or stays on the one note)
    val scaleStep = remember { intArrayOf(0) }
    LaunchedEffect(pitchIndex, settings.scale) {
        scaleStep[0] = 0
        audioManager.frequency = PITCHES_HZ[pitchIndex]
    }
    fun nextNoteHz(): Double = settings.scale.hz(PITCHES_HZ[pitchIndex], scaleStep[0]++)
    LaunchedEffect(settings.instrument) { audioManager.instrument = settings.instrument }
    LaunchedEffect(settings.vibrationStrength) { hapticManager.strength = settings.vibrationStrength / 100f }
    LaunchedEffect(settings.beatFade) { clickVolume = 1f }

    // Tempo applies once the slider / buttons rest; until then the old tempo keeps playing
    LaunchedEffect(pendingBpm) {
        if (pendingBpm != playBpm) {
            delay(TEMPO_SETTLE_MS)
            playBpm = pendingBpm
        }
    }
    LaunchedEffect(playBpm, targetNote) { clickVolume = 1f } // new tempo or note: full beat again

    // Beat clock: scheduled on absolute times so it never drifts
    LaunchedEffect(isPlaying) { if (!isPlaying) beatInBar = -1 } // next start begins on beat 1
    LaunchedEffect(timeSignature) { beatInBar = -1 }
    LaunchedEffect(isPlaying, playBpm, timeSignature) {
        if (!isPlaying) return@LaunchedEffect
        val interval = timeSignature.pulseMs(playBpm)
        val now = SystemClock.uptimeMillis()
        // Continue the pulse across tempo changes instead of restarting it
        val first = if (lastBeatAt > 0 && beatInBar >= 0 && lastBeatAt + interval > now) lastBeatAt + interval else now.toDouble()
        var n = 0
        while (true) {
            val at = (first + n * interval).toLong()
            delay(at - SystemClock.uptimeMillis())
            lastBeatAt = at
            beatInBar = (beatInBar + 1) % timeSignature.pulsesPerBar
            val accent = timeSignature.accents[beatInBar]
            beatTick++
            // The metronome button mutes the click; the speaker button mutes only the held note
            if (clickVolume > 0f && settings.beatSound) audioManager.playClick(clickVolume, accent)
            if (vibrationEnabled) hapticManager.tick(accent)
            n++
        }
    }

    // Ripple ring on each beat (the circle itself never moves)
    LaunchedEffect(beatTick) {
        if (beatTick > 0) {
            ripple.snapTo(0f)
            val rippleMs = (60000 * 0.8f / playBpm).toInt().coerceIn(150, 900) // follows the tempo
            ripple.animateTo(1f, animationSpec = tween(durationMillis = rippleMs, easing = LinearOutSlowInEasing))
        }
    }

    // Idle for a while: show the hint again and bring the full beat back
    LaunchedEffect(lastInteractionAt, isHolding) {
        if (isHolding) return@LaunchedEffect
        delay(IDLE_RESET_MS)
        hintVisible = true
        holdsSinceHint = 0
        clickVolume = 1f
    }

    // Vibration for a hold, from [from] on: each cycle of the note's length swells from weak to
    // strong and starts over, together with the ring, the fill and the tone (see startHold).
    // In demo mode the motor is left to the guide's cue.
    fun startHoldHaptics(from: Long) {
        if (!vibrationEnabled || demoMode) return
        hapticManager.startHold(startTime, currentTargetMs, playFrom = from)
    }

    // Tempo or note changed during a hold (e.g. by sliding on the pad): the vibration follows
    LaunchedEffect(playBpm, timeSignature, targetNote) {
        if (isHolding) startHoldHaptics(SystemClock.uptimeMillis())
    }

    // A note longer than the bar can't be picked: fall back to the longest one that fits
    LaunchedEffect(timeSignature) {
        if (!timeSignature.fits(freeNote)) freeNote = NoteLength.entries.last { timeSignature.fits(it) }
    }

    // Left playing with no press and no change for a while: stop by itself
    LaunchedEffect(lastActivityAt, isPlaying, isHolding) {
        if (!isPlaying || isHolding) return@LaunchedEffect
        delay(AUTO_STOP_MS)
        stopPlaying()
    }

    // The clock ticks once per frame while there is something to show
    LaunchedEffect(isPlaying, isHolding, stopAt) {
        while (isPlaying || isHolding || SystemClock.uptimeMillis() < stopAt) {
            withFrameMillis { }
            clock.longValue = SystemClock.uptimeMillis()
        }
    }

    // Hold feedback: within each cycle the tone starts soft and swells, then starts over
    LaunchedEffect(isHolding) {
        var lap = 0
        while (isHolding) {
            withFrameMillis { }
            val p = (SystemClock.uptimeMillis() - startTime).toFloat() / currentTargetMs
            if (p.toInt() != lap) { // a new cycle: struck and plucked instruments sound again
                lap = p.toInt()
                if (!demoMode) audioManager.strike()
            }
            if (!demoMode) audioManager.updateVolume(0.05f + 0.45f * (p - floor(p))) // tone + click stays within range
        }
    }

    // A rest that doesn't fit the bar can't be picked: back to "same as the note"
    LaunchedEffect(timeSignature) {
        restChoice?.let { if (!timeSignature.fits(it)) restChoice = null }
    }

    // While playing, a change to the rhythm (tempo, note, rest, meter, mode) never redraws the
    // wave already on the graph: it takes over at the first new cycle past the drawn part
    LaunchedEffect(demoMode, targetMs, restMs, timeSignature) {
        if (!isPlaying) return@LaunchedEffect
        val t = SystemClock.uptimeMillis()
        lastActivityAt = t
        val drawnUntil = t + ((1f - GRAPH_NOW_AT) * windowMs).toLong()
        val from = anchors.lastOrNull { it.at <= drawnUntil }?.takeIf { it.active }?.nextNoteStart(drawnUntil) ?: t
        setAnchor(GuideAnchor(from, targetMs, restMs))
    }
    LaunchedEffect(demoMode) { // each mode starts with a clean slate
        lastResult = null
        history.clear()
        restStartedAt = 0L
    }

    // The guide: note, rest, note... on its own clock while playing, drawn behind the graph. In
    // demo mode the app also plays it: the opposite of the hold feedback, one short cue
    // (vibration, tone, flash) exactly where each note starts, and nothing in between.
    val newestAnchor = anchors.lastOrNull()
    LaunchedEffect(newestAnchor, isPlaying) {
        if (newestAnchor == null || !isPlaying) { // Stop silences the guide at once
            guideRunning = false
            return@LaunchedEffect
        }
        guideRunning = true
        try {
            while (true) {
                // The next note start: in the stretch in force now, or the first note of the one queued after it
                val t = SystemClock.uptimeMillis()
                val current = guideAt(t)
                val queued = anchors.firstOrNull { it.at > t && it.active }
                var at = current?.nextNoteStart(t + 1) ?: Long.MAX_VALUE
                var g = current
                if (queued != null && queued.at <= at) {
                    at = queued.at
                    g = queued
                }
                if (g == null) break
                delay(at - SystemClock.uptimeMillis())
                val noteMs = g.noteMs
                // Optional, in both modes: a sound where the guide's note ends and its rest begins
                if (soundEnabled && settings.restSound) scope.launch {
                    delay(noteMs)
                    if (isPlaying) audioManager.playRestCue(settings.restPitch)
                }
                if (demoMode) {
                    if (vibrationEnabled) hapticManager.cue()
                    if (soundEnabled) {
                        audioManager.frequency = nextNoteHz()
                        audioManager.startTone(0.4f)
                        val blipMs = if (audioManager.instrument.percussive) noteMs else minOf(90L, noteMs)
                        scope.launch { delay(blipMs); audioManager.stopTone() }
                    }
                    if (lightEnabled) scope.launch {
                        flash.snapTo(1f)
                        flash.animateTo(0f, animationSpec = tween(durationMillis = 350))
                    }
                }
            }
        } finally {
            guideRunning = false
        }
    }

    /**
     * How far through a rest we are at [t], 0..1, or -1 when not resting. In demo mode it is the
     * guide's rest; otherwise the rest that starts when the player lets go.
     */
    fun restProgress(t: Long): Float {
        val g = if (demoMode && guideRunning) guideAt(t) else null
        return when {
            g != null -> {
                val pos = if (t >= g.at) (t - g.at) % g.cycleMs else 0L
                if (pos >= g.noteMs) (pos - g.noteMs).toFloat() / g.restMs else -1f
            }
            !isHolding && restStartedAt > 0 && t < restEndsAt -> (t - restStartedAt).toFloat() / (restEndsAt - restStartedAt)
            else -> -1f
        }
    }

    // The practice panel leaves after a few seconds without a press
    LaunchedEffect(lastInteractionAt, isHolding) {
        if (isHolding) return@LaunchedEffect
        delay(PANEL_HIDE_MS)
        panelShown = false
        panelDismissed = false
    }

    // What the panel's big counter shows (reads the clock: evaluated by the panel, not here)
    val readout = remember {
        {
            val t = clock.longValue
            val g = if (demoMode && guideRunning) anchors.lastOrNull { it.at <= t }?.takeIf { it.active } else null
            when {
                isHolding -> {
                    val held = (t - startTime).coerceAtLeast(0)
                    val p = held.toFloat() / currentTargetMs
                    when {
                        p > 1f + GOOD_WINDOW -> PanelReadout(held / 100, currentTargetMs, R.string.too_long, OverRed)
                        p >= 1f - GOOD_WINDOW -> PanelReadout(held / 100, currentTargetMs, R.string.release_now, ReleaseGreen)
                        else -> PanelReadout(held / 100, currentTargetMs, R.string.holding, RingBlue)
                    }
                }
                g != null && t >= g.at -> { // demo: where the guide is in its cycle
                    val pos = (t - g.at) % g.cycleMs
                    if (pos < g.noteMs) PanelReadout(pos / 100, g.noteMs, R.string.panel_note, RingBlue)
                    else PanelReadout((pos - g.noteMs) / 100, g.restMs, R.string.panel_rest, RestGray)
                }
                restStartedAt > 0 && t < restEndsAt ->
                    PanelReadout((t - restStartedAt).coerceAtLeast(0) / 100, restEndsAt - restStartedAt, R.string.panel_rest, RestGray)
                else -> PanelReadout(0, currentTargetMs, R.string.press_and_hold, RingBlue)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding() // keep clear of status and navigation bars
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: toggles, tone pitch, lessons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenMenu, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu), modifier = Modifier.size(24.dp))
            }

            IconButton(onClick = { soundEnabled = !soundEnabled }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Filled.VolumeMute else Icons.Filled.VolumeOff,
                        contentDescription = stringResource(R.string.cd_sound_toggle),
                        tint = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val vibrationLabel = stringResource(R.string.cd_vibration_toggle)
                IconButton(
                    onClick = { vibrationEnabled = !vibrationEnabled },
                    modifier = Modifier.size(40.dp).semantics { contentDescription = vibrationLabel }
                ) {
                    Text("⚡", fontSize = 20.sp, modifier = Modifier.alpha(if (vibrationEnabled) 1f else 0.3f))
                }

                IconButton(onClick = { lightEnabled = !lightEnabled }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = stringResource(R.string.cd_light_toggle),
                        tint = if (lightEnabled) Color.Yellow else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { settings.beatSound = !settings.beatSound }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = MetronomeIcon,
                        contentDescription = stringResource(R.string.cd_beat_sound),
                        tint = if (settings.beatSound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                ModeButton(demoMode = demoMode, onSelect = { demoMode = it })

                IconButton(onClick = onOpenLessons, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = stringResource(R.string.cd_lessons),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Tempo: value + mark name, beat fade level
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.bpm_label, pendingBpm) + "  ·  " + Tempo.nameFor(pendingBpm),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TimeSignaturePicker(timeSignature, onSelect = { settings.timeSignature = it })
                if (isPlaying && clickVolume < 1f) {
                    Text(
                        if (clickVolume <= 0f) stringResource(R.string.beat_off)
                        else stringResource(R.string.beat_level, (clickVolume * 100).roundToInt()),
                        fontSize = 13.sp,
                        color = ReleaseGreen
                    )
                }
            }
            Slider(
                value = pendingBpm.toFloat(),
                onValueChange = { pendingBpm = it.roundToInt() },
                onValueChangeFinished = { playBpm = pendingBpm }, // released: apply right away
                valueRange = Tempo.MIN.toFloat()..Tempo.MAX.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            // Transport: << - [play/stop] + >>
            TransportRow(
                isPlaying = isPlaying,
                onPlayStop = { if (isPlaying) stopPlaying() else startPlaying() },
                onPrevMark = { pendingBpm = Tempo.previousMark(pendingBpm) },
                onMinus = { pendingBpm = Tempo.clamp(pendingBpm - Tempo.STEP) },
                onPlus = { pendingBpm = Tempo.clamp(pendingBpm + Tempo.STEP) },
                onNextMark = { pendingBpm = Tempo.nextMark(pendingBpm) }
            )

            // Target: lesson banner, or note-value picker in free play (fixed height: layout never shifts)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (currentLesson != null) 92.dp else 60.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (currentLesson != null) {
                    LessonPager(
                        current = currentLesson,
                        currentProgress = lessonProgress,
                        progressManager = progressManager,
                        bpm = playBpm,
                        onChange = onLessonChange
                    )
                } else {
                    NoteValuePicker(selected = freeNote, timeSignature = timeSignature, onSelect = { freeNote = it; lastResult = null; history.clear() })
                }
            }

            // Rest between notes: "=" keeps it as long as the note, or pick a rest value
            RestPicker(
                selected = restChoice,
                timeSignature = timeSignature,
                onSelect = { restChoice = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Hold pad: beat ripple, progress ring, circle
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .pointerInput(Unit) {
                        // Times come from the touch events themselves (ms-exact), not from when this code
                        // runs, which can lag a frame: that matters for short notes like sixteenths.
                        awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pressedAt = down.uptimeMillis
                                isHolding = true
                                startTime = pressedAt
                                lastInteractionAt = pressedAt
                                panelShown = true
                                // Pressing while stopped starts playing, with this press as beat 1. While
                                // playing, the guide keeps its own time and the press shows against it.
                                val pressStartedGuide = !isPlaying
                                if (pressStartedGuide) startPlaying(pressedAt) else lastActivityAt = pressedAt
                                presses.add(PressMark(pressedAt, null, null))
                                while (presses.size > 40) presses.removeAt(0)
                                pressSolo = !isPlaying || clickVolume == 0f || !settings.beatSound // no audible beat
                                pressBarPulse = if (!pressStartedGuide && isPlaying && lastBeatAt > 0 && beatInBar >= 0) {
                                    val pulse = timeSignature.pulseMs(playBpm)
                                    val ahead = ((pressedAt - lastBeatAt) / pulse).roundToInt() // nearest pulse
                                    (beatInBar + ahead) % timeSignature.pulsesPerBar
                                } else 0 // without the beat, a hold starts a bar
                                pressOnsetError = if (pressStartedGuide) 0f else if (isPlaying && lastBeatAt > 0) {
                                    val interval = timeSignature.pulseMs(playBpm).toFloat()
                                    val phase = ((pressedAt - lastBeatAt) % interval) / interval
                                    min(phase, 1f - phase)
                                } else 1f
                                if (demoMode) {
                                    // The player's own press, told apart from the guide's cue by every
                                    // sense: a bell (not the tone), two light taps (not one firm one),
                                    // a gold rim (not the white flash)
                                    if (soundEnabled) audioManager.playTouchSound(settings.touchSound, settings.touchPitch)
                                    if (vibrationEnabled) hapticManager.touch()
                                    if (lightEnabled) scope.launch {
                                        touchFlash.snapTo(1f)
                                        touchFlash.animateTo(0f, animationSpec = tween(durationMillis = 400))
                                    }
                                } else if (soundEnabled) {
                                    audioManager.frequency = nextNoteHz()
                                    audioManager.startTone(0.05f) // swells from here
                                }
                                startHoldHaptics(pressedAt)

                                // Follow the finger until it lifts; leaving the pad doesn't cancel. The first
                                // direction of the slide picks what it changes: up/down = tempo (up = faster),
                                // sideways = the hold note in free play (toward the longer notes in the picker).
                                var releasedAt: Long? = null
                                var last = down.position
                                var moved = 0f
                                var sliding = false
                                var vertical = true // axis of this slide, fixed once it starts
                                var stepAcc = 0f
                                val stepPx = 40.dp.toPx() // one tempo mark per 40 dp, like the << >> buttons
                                val noteStepPx = 50.dp.toPx() // one note value per 50 dp
                                // Next/previous note value that fits the bar (free play only; a lesson fixes the note)
                                fun stepNote(dir: Int) {
                                    if (lesson != null) return
                                    val options = NoteLength.entries.filter { settings.timeSignature.fits(it) }
                                    val next = options.getOrNull(options.indexOf(freeNote) + dir) ?: return
                                    freeNote = next
                                    lastResult = null
                                    history.clear()
                                }
                                // Jump to the next/previous tempo mark and apply it at once (beat + vibration follow live)
                                fun jumpMark(dir: Int) {
                                    val next = if (dir > 0) Tempo.nextMark(pendingBpm) else Tempo.previousMark(pendingBpm)
                                    if (next != pendingBpm) {
                                        pendingBpm = next
                                        playBpm = next
                                    }
                                }
                                // Near the pad's border it keeps jumping on its own (faster the longer it stays)
                                val edgeRadius = 100.dp.toPx()
                                var edgeDir = 0
                                var edgeRepeats = 0
                                try {
                                    while (true) {
                                        val event = if (sliding && edgeDir != 0) {
                                            withTimeoutOrNull((500L - edgeRepeats * 60).coerceAtLeast(250)) { awaitPointerEvent() }
                                        } else awaitPointerEvent()
                                        if (event == null) { // finger resting at the border: auto-repeat
                                            jumpMark(edgeDir)
                                            edgeRepeats++
                                            continue
                                        }
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            releasedAt = change.uptimeMillis
                                            change.consume()
                                            break
                                        }
                                        val d = change.position - last
                                        last = change.position
                                        moved += d.getDistance()
                                        if (!sliding && moved > viewConfiguration.touchSlop) {
                                            sliding = true
                                            val total = change.position - down.position
                                            vertical = kotlin.math.abs(total.y) >= kotlin.math.abs(total.x)
                                        }
                                        if (sliding && vertical) {
                                            stepAcc -= d.y // up = faster
                                            val steps = (stepAcc / stepPx).toInt()
                                            if (steps != 0) {
                                                stepAcc -= steps * stepPx
                                                repeat(kotlin.math.abs(steps)) { jumpMark(if (steps > 0) 1 else -1) }
                                            }
                                            // Finger resting near the top/bottom border keeps jumping tempo marks
                                            val fromCenter = last - Offset(size.width / 2f, size.height / 2f)
                                            val dist = fromCenter.getDistance()
                                            val lean = if (dist > 0f) -fromCenter.y / dist else 0f
                                            val newDir = if (dist >= edgeRadius && kotlin.math.abs(lean) > 0.5f) (if (lean > 0) 1 else -1) else 0
                                            if (newDir != edgeDir) edgeRepeats = 0
                                            edgeDir = newDir
                                        } else if (sliding) {
                                            stepAcc += d.x * sliderSign // toward the longer notes, as laid out in the picker
                                            val steps = (stepAcc / noteStepPx).toInt()
                                            if (steps != 0) {
                                                stepAcc -= steps * noteStepPx
                                                repeat(kotlin.math.abs(steps)) { stepNote(if (steps > 0) 1 else -1) }
                                            }
                                        }
                                        change.consume()
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    isHolding = false // screen went away mid-hold: silence everything
                                    if (presses.isNotEmpty()) presses.removeAt(presses.lastIndex)
                                    audioManager.stopTone()
                                    hapticManager.stop()
                                    throw e
                                }
                                val releaseTime = releasedAt ?: SystemClock.uptimeMillis()
                                val heldMs = releaseTime - startTime
                                isHolding = false
                                audioManager.stopTone()
                                hapticManager.endHold()
                                lastInteractionAt = SystemClock.uptimeMillis()
                                lastActivityAt = lastInteractionAt
                                if (heldMs < MIN_HOLD_MS) {
                                    if (presses.isNotEmpty()) presses.removeAt(presses.lastIndex)
                                    return@awaitEachGesture
                                }
                                // The ring now counts the rest down. While playing it ends where the guide's
                                // next note starts, so it is empty exactly when the next press is due.
                                if (isPlaying) {
                                    restStartedAt = releaseTime
                                    restEndsAt = guideAt(releaseTime)
                                        ?.nextNoteStart(releaseTime + currentRestMs / 4)
                                        ?: (releaseTime + currentRestMs)
                                }
                                // Demo mode: a softer, lower version of the press sound marks the release
                                if (demoMode && soundEnabled) audioManager.playTouchSound(settings.touchSound, settings.touchPitch, release = true)

                                // The player gets it: let the hint go after a couple of holds
                                holdsSinceHint++
                                if (holdsSinceHint >= HOLDS_TO_HIDE_HINT) hintVisible = false

                                val accuracy = progressManager.calculateAccuracy(currentTargetMs, heldMs)
                                // Also how far the press was from where the guide's note started
                                // (nothing to compare with when this press itself started the guide)
                                val onsetMs = if (pressStartedGuide) null else {
                                    guideAt(startTime)?.let { startTime - it.nearestNoteStart(startTime) }
                                }
                                val result = HoldResult(heldMs, currentTargetMs, accuracy, onsetMs)
                                if (presses.isNotEmpty()) {
                                    presses[presses.lastIndex] = PressMark(startTime, releaseTime, result.ratio)
                                }
                                lastResult = result
                                history.add(result)
                                if (history.size > HISTORY_SIZE) history.removeAt(0)

                                // Optional: on the beat and the right length, fade the click so they carry the rhythm
                                if (isPlaying && settings.beatFade) {
                                    val good = accuracy >= 0.8f && pressOnsetError <= ONSET_TOLERANCE
                                    val next = if (good) clickVolume - 0.2f else clickVolume + 0.4f
                                    clickVolume = (next * 10).roundToInt().coerceIn(0, 10) / 10f // whole steps: reaches exactly 0
                                }

                                lesson?.let { l ->
                                    val starsBefore = lessonProgress?.stars ?: 0
                                    val updated = progressManager.recordAttempt(l.id, accuracy, solo = pressSolo)
                                    lessonProgress = updated
                                    if (updated.stars > starsBefore) {
                                        celebrateStars = updated.stars
                                        audioManager.playChime(doubleArrayOf(523.25, 659.25, 783.99, 1046.50)) // C E G C
                                        if (vibrationEnabled) hapticManager.celebrate()
                                    }
                                }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Beat ripple: expands and fades behind everything (draw-only, no layout change)
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(262.dp)
                            .graphicsLayer {
                                val p = ripple.value
                                val reach = if (beatInBar == 0) 0.2f else 0.12f // beat 1 ripples further
                                scaleX = 1f + reach * p
                                scaleY = 1f + reach * p
                                alpha = (1f - p) * 0.8f
                            }
                            .border(width = 3.dp, color = RingBlue, shape = CircleShape)
                    )
                }

                // Rest ring: during a rest a gray ring widens around the pad, reaching its full
                // size exactly when the rest is over and the next note is due
                Box(
                    modifier = Modifier
                        .size(262.dp)
                        .graphicsLayer {
                            val r = restProgress(now())
                            if (r < 0f) alpha = 0f else {
                                scaleX = 1f + 0.2f * r
                                scaleY = 1f + 0.2f * r
                                alpha = 0.9f - 0.6f * r
                            }
                        }
                        .border(width = 4.dp, color = RestGray, shape = CircleShape)
                )

                // Progress ring, drawn from the clock. While holding it fills toward the target (green =
                // release now, red = too long) and each further lap paints over it in a shade. After the
                // release it empties in gray over the rest. In demo mode it follows the app's guide instead.
                val pulses = targetNote.beats * timeSignature.pulsesPerQuarter
                Canvas(modifier = Modifier.size(262.dp)) {
                    val t = now()
                    val stroke = 9.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)

                    var sweep = 0f            // how much of the ring is painted, 0..1
                    var color = RingBlue
                    var under: Color? = null  // the full ring as the previous lap left it
                    var noteFraction = 0f     // progress through the note, for lighting the pulse marks
                    var cap = StrokeCap.Round // a rest's countdown has a flat end, so it visibly reaches zero
                    val g = if (demoMode && guideRunning) guideAt(t) else null
                    when {
                        g != null -> if (t >= g.at) {
                            val pos = (t - g.at) % g.cycleMs
                            if (pos < g.noteMs) {
                                sweep = pos.toFloat() / g.noteMs
                                noteFraction = sweep
                            } else {
                                sweep = 1f - (pos - g.noteMs).toFloat() / g.restMs
                                color = RestGray
                                cap = StrokeCap.Butt
                            }
                        }
                        isHolding -> {
                            val look = lapLook((t - startTime).toFloat() / targetMs, RingBlue)
                            under = look.under
                            color = look.fill
                            sweep = look.fraction
                            noteFraction = sweep
                        }
                        restStartedAt > 0 && t < restEndsAt -> {
                            sweep = 1f - (t - restStartedAt).toFloat() / (restEndsAt - restStartedAt)
                            color = RestGray
                            cap = StrokeCap.Butt
                        }
                    }

                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                    )
                    under?.let {
                        drawArc(
                            color = it,
                            startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                        )
                    }
                    if (sweep > 0f) drawArc(
                        color = color,
                        startAngle = -90f, sweepAngle = 360f * sweep.coerceIn(0f, 1f), useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(stroke, cap = cap)
                    )
                    // Pulse marks: one per click of the time signature (eighths in 6/8), so the fill moves
                    // with the beat; where a new bar starts, the mark is bigger and gold
                    val radius = (size.width - stroke) / 2
                    var k = 1
                    while (k < pulses) {
                        val angle = Math.toRadians((-90.0 + 360.0 * k / pulses))
                        val barStart = (pressBarPulse + k) % timeSignature.pulsesPerBar == 0
                        val lit = noteFraction * pulses >= k // lit as the fill passes (each lap)
                        drawCircle(
                            color = if (barStart) StarGold.copy(alpha = if (lit) 1f else 0.55f)
                                else Color.White.copy(alpha = if (lit) 0.9f else 0.3f),
                            radius = stroke * if (barStart) 0.5f else 0.32f,
                            center = Offset(center.x + radius * kotlin.math.cos(angle).toFloat(), center.y + radius * kotlin.math.sin(angle).toFloat())
                        )
                        k++
                    }
                }

                // Main circle: fixed size, never moves. With the light on, the hold fills it from the
                // bottom on the same cycle as the ring: it grows brighter as it rises, and once full it
                // starts over in the next lap's shade.
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(PadBlue)
                        .drawBehind {
                            if (lightEnabled && isHolding) {
                                val look = lapLook((now() - startTime).toFloat() / targetMs, FillBlue)
                                val below = look.under ?: PadBlue
                                if (look.under != null) drawRect(below)
                                val top = size.height * (1f - look.fraction)
                                val glow = if (demoMode) 1f else 0.35f + 0.65f * look.fraction // dim to bright, like the tone
                                drawRect(lerp(below, look.fill, glow), topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
                            }
                            val f = flash.value
                            if (f > 0f) drawRect(Color.White.copy(alpha = 0.55f * f)) // demo: a note starts
                            val tf = touchFlash.value
                            if (tf > 0f) drawCircle( // demo: the player's own press
                                StarGold.copy(alpha = tf),
                                radius = size.minDimension / 2 - 7.dp.toPx(),
                                style = Stroke(14.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) BarDots(timeSignature, beatInBar, Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
                    PadLabel(
                        holdPhase = {
                            if (!isHolding) 0 else {
                                val p = (clock.longValue - startTime).toFloat() / currentTargetMs
                                if (p > 1f + GOOD_WINDOW) 3 else if (p >= 1f - GOOD_WINDOW) 2 else 1
                            }
                        },
                        hintVisible = hintVisible,
                        demoMode = demoMode,
                        targetSeconds = targetMs / 1000.0
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom: the live graph, the last result and a gentle tip (fixed height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Beat ticks on the graph's time axis: the real beats when the beat plays, else counted from the guide
                val pulseMs = timeSignature.pulseMs(playBpm)
                val beatTicks = when {
                    isPlaying && lastBeatAt > 0 && beatInBar >= 0 ->
                        BeatTicks(lastBeatAt - beatInBar * pulseMs, pulseMs, timeSignature.pulsesPerBar)
                    else -> anchors.lastOrNull { it.active }
                        ?.let { BeatTicks(it.at.toDouble(), pulseMs, timeSignature.pulsesPerBar) }
                }
                LiveGraph(
                    now = now,
                    anchors = anchors,
                    presses = presses,
                    beats = beatTicks,
                    windowMs = windowMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp)
                )
                val result = lastResult
                val onsetText = result?.onsetMs?.let { ms ->
                    when {
                        abs(ms) <= ONSET_OK_MS -> stringResource(R.string.onset_on_time)
                        ms > 0 -> stringResource(R.string.onset_late, ms.toInt())
                        else -> stringResource(R.string.onset_early, (-ms).toInt())
                    }
                }
                Text(
                    text = when {
                        result != null -> stringResource(
                            R.string.lesson_result, result.heldMs / 1000.0, (result.ratio * 100).roundToInt()
                        ) + (onsetText?.let { "  ·  $it" } ?: "")
                        demoMode -> stringResource(R.string.hint_demo)
                        currentLesson != null -> stringResource(R.string.hint_lesson)
                        else -> stringResource(R.string.hint_free_play)
                    },
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = result?.let { scoreColor(it.ratio) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val tip = timingTip(history)
                Text(
                    text = tip?.let { stringResource(it) } ?: "",
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Practice panel: over the controls while practicing, so the time isn't under the finger
        PracticePanel(
            visible = panelShown && !panelDismissed,
            onDismiss = { panelDismissed = true },
            bpm = playBpm,
            timeSignature = timeSignature,
            note = targetNote,
            rest = restNote,
            noteMs = targetMs,
            restMs = restMs,
            demoMode = demoMode,
            isPlaying = isPlaying,
            onStop = {
                stopPlaying()
                panelShown = false
            },
            readout = readout,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Star earned: full-screen celebration on top
        if (celebrateStars > 0) {
            StarCelebration(stars = celebrateStars, onDone = { celebrateStars = 0 })
        }
    }
}

/**
 * Label inside the pad: a fading hint, and the target time at rest. The running time is not shown
 * here (it would be under the finger): it is on the practice panel at the top.
 * [holdPhase]: 0 = not holding, 1 = holding, 2 = in the release window, 3 = held too long.
 */
@Composable
private fun PadLabel(holdPhase: () -> Int, hintVisible: Boolean, demoMode: Boolean, targetSeconds: Double) {
    val phase by remember { derivedStateOf(holdPhase) } // follows the clock, recomposes only on a change
    val text = when {
        phase == 2 -> stringResource(R.string.release_now)
        phase == 1 && hintVisible -> stringResource(R.string.holding)
        phase == 0 && hintVisible -> stringResource(if (demoMode) R.string.imitate_hint else R.string.press_and_hold)
        else -> null
    }
    var shownText by remember { mutableStateOf(text ?: "") }
    if (text != null) shownText = text
    val textAlpha by animateFloatAsState(if (text != null) 1f else 0f, tween(600), label = "labelAlpha")
    val targetAlpha by animateFloatAsState(if (phase == 0) 0.75f else 0f, tween(300), label = "targetAlpha")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(shownText, color = Color.White, fontSize = 22.sp, modifier = Modifier.alpha(textAlpha))
        Text(
            stringResource(R.string.hold_for, targetSeconds),
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 8.dp).alpha(targetAlpha)
        )
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    onPlayStop: () -> Unit,
    onPrevMark: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onNextMark: () -> Unit
) {
    // Rewind/forward icons follow the slider direction, which flips in RTL
    val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val tint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevMark) {
            Icon(Icons.Filled.FastRewind, stringResource(R.string.cd_tempo_prev), tint = tint,
                modifier = Modifier.graphicsLayer { scaleX = mirror })
        }
        IconButton(onClick = onMinus) {
            Icon(Icons.Filled.Remove, stringResource(R.string.cd_tempo_minus), tint = tint)
        }
        FilledIconButton(onClick = onPlayStop, modifier = Modifier.size(56.dp)) {
            Icon(
                if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                stringResource(if (isPlaying) R.string.cd_stop else R.string.cd_play),
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(onClick = onPlus) {
            Icon(Icons.Filled.Add, stringResource(R.string.cd_tempo_plus), tint = tint)
        }
        IconButton(onClick = onNextMark) {
            Icon(Icons.Filled.FastForward, stringResource(R.string.cd_tempo_next), tint = tint,
                modifier = Modifier.graphicsLayer { scaleX = mirror })
        }
    }
}

/** Color of the next lap of the ring: a lighter shade of a dark color, a darker shade of a light one. */
private fun lapShade(c: Color): Color =
    if (c.luminance() > 0.5f) lerp(c, Color.Black, 0.35f) else lerp(c, Color.White, 0.45f)

/**
 * Mode button: the finger (you lead) or the eye (imitate the app). The small arrow under the
 * icon shows that it opens a choice; the menu names both options.
 */
@Composable
private fun ModeButton(demoMode: Boolean, onSelect: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.cd_mode_toggle)
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (demoMode) Icons.Filled.Visibility else Icons.Filled.TouchApp,
                contentDescription = null,
                tint = if (demoMode) StarGold else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).align(Alignment.TopCenter).padding(top = 2.dp)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).align(Alignment.BottomCenter)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(false to R.string.mode_follow_long, true to R.string.mode_demo_long).forEach { (demo, nameRes) ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            if (demo) Icons.Filled.Visibility else Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = if (demo == demoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    text = { Text(stringResource(nameRes), color = if (demo == demoMode) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                    onClick = { open = false; onSelect(demo) }
                )
            }
        }
    }
}

/** How a hold looks at [progress] (held / target): what to paint over what, and how far. */
private class LapLook(val under: Color?, val fill: Color, val fraction: Float)

/**
 * First lap: [neutral] filling up, green in the release window. Past the target every lap
 * starts over, painting a shade of the lap before (green just after the target, then red).
 */
private fun lapLook(progress: Float, neutral: Color): LapLook {
    val state = when {
        progress > 1f + GOOD_WINDOW -> OverRed
        progress >= 1f - GOOD_WINDOW -> ReleaseGreen
        else -> neutral
    }
    val laps = if (progress > 1f) progress.toInt() else 0 // completed laps beyond the first fill
    if (laps == 0) return LapLook(null, state, progress.coerceIn(0f, 1f))
    var under = state
    repeat(laps - 1) { under = lapShade(under) }
    return LapLook(under, lapShade(under), progress - laps)
}

/** "Rest:" row: "=" (as long as the note) and the rests that fit the bar. Scrolls sideways on narrow screens. */
@Composable
private fun RestPicker(
    selected: NoteLength?,
    timeSignature: TimeSignature,
    onSelect: (NoteLength?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.rest_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        PickerChip(selected == null, stringResource(R.string.rest_equal), onClick = { onSelect(null) }) { color ->
            Text("=", color = color, fontSize = 22.sp)
        }
        NoteLength.entries.filter { timeSignature.fits(it) }.forEach { note ->
            PickerChip(note == selected, stringResource(R.string.rest_of, stringResource(note.nameRes)), onClick = { onSelect(note) }) { color ->
                RestGlyph(note, color, height = 26.dp)
            }
        }
    }
}

/** Round chip for the rest picker. */
@Composable
private fun PickerChip(isSelected: Boolean, name: String, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                CircleShape
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        content(if (isSelected) Color.White else Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun NoteValuePicker(selected: NoteLength, timeSignature: TimeSignature, onSelect: (NoteLength) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.hold_note_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        NoteLength.entries.filter { timeSignature.fits(it) }.forEach { note ->
            val isSelected = note == selected
            val name = stringResource(note.nameRes)
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(42.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(note) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center
            ) {
                NoteGlyph(note, if (isSelected) Color.White else Color.White.copy(alpha = 0.7f), height = 28.dp)
            }
        }
    }
}

/**
 * Lesson banner as a pager: swipe to the neighbouring lesson, which slides in live.
 * The lesson switches as soon as the swipe passes halfway, without stopping the beat.
 */
@Composable
private fun LessonPager(
    current: Lesson,
    currentProgress: LessonProgress?,
    progressManager: ProgressManager,
    bpm: Int,
    onChange: (Lesson) -> Unit
) {
    val lessons = LessonCurriculum.lessons
    val pagerState = rememberPagerState(
        initialPage = lessons.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    ) { lessons.size }
    val currentId by rememberUpdatedState(current.id)
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (lessons[page].id != currentId) change(lessons[page])
        }
    }

    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val lesson = lessons[page]
            val progress = if (lesson.id == current.id && currentProgress != null) currentProgress
                else remember(lesson.id) { progressManager.getProgress(lesson.id) }
            LessonBanner(lesson, progress, lesson.targetNoteLength.durationMs(bpm))
        }
        // Page dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            lessons.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                        .background(
                            Color.White.copy(alpha = if (i == pagerState.currentPage) 0.9f else 0.3f),
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun LessonBanner(lesson: Lesson, progress: LessonProgress, targetMs: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
        NoteGlyph(lesson.targetNoteLength, MaterialTheme.colorScheme.onSurface, height = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(lesson.titleRes), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                lesson.timeSignature?.let { MeterBadge(it) }
                Spacer(Modifier.width(8.dp))
                StarsRow(progress.stars, size = 16.dp)
            }
            Text(
                stringResource(R.string.lesson_target, targetMs / 1000.0),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Live progress toward the next star: rule + dots for the current streak
            val streak = progress.nextStarStreak
            Text(
                if (streak == null) stringResource(R.string.stars_all)
                else stringResource(
                    when (progress.stars) {
                        0 -> R.string.star_next_1
                        1 -> R.string.star_next_2
                        else -> R.string.star_next_3
                    }
                ) + "  " + "●".repeat(streak.coerceAtMost(LessonProgress.STREAK_FOR_STAR)) +
                    "○".repeat((LessonProgress.STREAK_FOR_STAR - streak).coerceAtLeast(0)),
                fontSize = 12.sp,
                color = StarGold,
                maxLines = 1
            )
        }
    }
}

private const val HISTORY_SIZE = 15

/** Same color for equally early and late holds: 93% and 107% of the target look the same. */
internal fun scoreColor(ratio: Float): Color {
    val off = kotlin.math.abs(ratio - 1f)
    return when {
        off <= GOOD_WINDOW -> Color(0xFF66DD77)
        off <= 0.3f -> Color(0xFFFFC857)
        else -> OverRed
    }
}

/**
 * A tip only when the last holds clearly lean one way; otherwise nothing (no nagging).
 * Pressing early or late against the guide comes first, then holding too long or too short.
 */
private fun timingTip(history: List<HoldResult>): Int? {
    val recent = history.takeLast(6)
    if (recent.size < 5) return null
    val onsets = recent.mapNotNull { it.onsetMs }
    if (onsets.size >= 5) {
        val meanOnset = onsets.average()
        if (meanOnset > 60 && onsets.count { it > ONSET_OK_MS } >= 4) return R.string.tip_late
        if (meanOnset < -60 && onsets.count { it < -ONSET_OK_MS } >= 4) return R.string.tip_early
    }
    val mean = recent.map { it.ratio - 1f }.average()
    val late = recent.count { it.ratio > 1f + 0.03f }
    val early = recent.count { it.ratio < 1f - 0.03f }
    return when {
        mean > 0.05 && late >= 4 -> R.string.tip_long
        mean < -0.05 && early >= 4 -> R.string.tip_short
        else -> null
    }
}

/** "4/4"-style chip; tap to choose the time signature. */
@Composable
private fun TimeSignaturePicker(selected: TimeSignature, onSelect: (TimeSignature) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.cd_time_signature)
    Box(Modifier.padding(horizontal = 8.dp)) {
        Text(
            selected.label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { open = true }
                .semantics { contentDescription = label }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TimeSignature.entries.forEach { ts ->
                DropdownMenuItem(
                    text = { Text(ts.label, fontSize = 18.sp, color = if (ts == selected) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                    onClick = { open = false; onSelect(ts) }
                )
            }
        }
    }
}

/** One dot per pulse of the bar; the current one lights up, beat 1 is bigger. */
@Composable
private fun BarDots(timeSignature: TimeSignature, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(timeSignature.pulsesPerBar) { i ->
            val accent = timeSignature.accents[i]
            Box(
                Modifier
                    .size(if (accent == 2) 12.dp else if (accent == 1) 10.dp else 8.dp)
                    .background(Color.White.copy(alpha = if (i == current) 1f else 0.3f), CircleShape)
            )
        }
    }
}

/** Small "3/4" badge for lessons tied to a time signature. */
@Composable
fun MeterBadge(timeSignature: TimeSignature) {
    Text(
        timeSignature.label,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .padding(start = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}
