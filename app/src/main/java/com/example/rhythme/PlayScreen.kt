package com.example.rhythme

import android.content.SharedPreferences
import android.os.SystemClock
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
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

private val PadBlue = Color(0.2f, 0.5f, 0.9f)
private val FillBlue = Color(0xFF6FB1FF) // the pad "fills" with this from the bottom
private val RingBlue = Color(0xFF7FD4FF)
private val ReleaseGreen = Color(0xFF34C759)
private val OverRed = Color(0xFFFF6B6B)

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

    var soundEnabled: Boolean get() = sound.value; set(v) { sound.value = v; prefs.edit().putBoolean("sound", v).apply() }
    var vibrationEnabled: Boolean get() = vibration.value; set(v) { vibration.value = v; prefs.edit().putBoolean("vibration", v).apply() }
    var lightEnabled: Boolean get() = light.value; set(v) { light.value = v; prefs.edit().putBoolean("light", v).apply() }
    var pitchIndex: Int get() = pitch.intValue; set(v) { pitch.intValue = v; prefs.edit().putInt("pitch", v).apply() }
    var freeNote: NoteLength get() = note.value; set(v) { note.value = v; prefs.edit().putString("note", v.name).apply() }
    var instrument: Instrument get() = voice.value; set(v) { voice.value = v; prefs.edit().putString("instrument", v.name).apply() }
    var profileName: String get() = name.value; set(v) { name.value = v; prefs.edit().putString("profile_name", v).apply() }
    var timeSignature: TimeSignature get() = meter.value; set(v) { meter.value = v; prefs.edit().putString("time_signature", v.name).apply() }
    var beatSound: Boolean get() = click.value; set(v) { click.value = v; prefs.edit().putBoolean("beat_sound", v).apply() }

    private companion object {
        inline fun <reified T : Enum<T>> enumOr(saved: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == saved } ?: default
    }
}

/** One counted hold: how long it was, against what target, and its score. */
data class HoldResult(val heldMs: Long, val targetMs: Long, val accuracy: Float) {
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
    var holdDurationMs by remember { mutableLongStateOf(0L) }
    var lastProgress by remember { mutableFloatStateOf(0f) }
    var pressOnsetError by remember { mutableFloatStateOf(1f) }
    var pressSolo by remember { mutableStateOf(false) } // no audible beat at press: counts toward ★★★
    var pressBarPulse by remember { mutableIntStateOf(0) } // pulse of the bar the hold started on (0 = beat 1)

    // "Press & Hold" hint
    var hintVisible by remember { mutableStateOf(true) }
    var holdsSinceHint by remember { mutableIntStateOf(0) }
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }

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

    fun startPlaying() {
        isPlaying = true
        hintVisible = true
        holdsSinceHint = 0
        clickVolume = 1f
    }

    // Stop the beat (and its vibrations) when the app leaves the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) isPlaying = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pitchIndex) { audioManager.frequency = PITCHES_HZ[pitchIndex] }
    LaunchedEffect(settings.instrument) { audioManager.instrument = settings.instrument }

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

    // Vibration for a hold, from [from] on. Ticks fall on the real beats when the beat plays;
    // otherwise on the note's own beats counted from the press (matching the ring's beat marks).
    fun startHoldHaptics(from: Long) {
        if (!vibrationEnabled) return
        val interval = timeSignature.pulseMs(playBpm)
        // Downbeat to count bars from: the current bar when the beat plays, else the press itself
        val origin = if (isPlaying && lastBeatAt > 0 && beatInBar >= 0) lastBeatAt - beatInBar * interval
            else startTime.toDouble()
        val grid = BeatGrid(origin, interval, timeSignature.pulsesPerBar, timeSignature.accents)
        hapticManager.startHold(startTime, currentTargetMs, grid, playFrom = from)
    }

    // Tempo changed during a hold (e.g. by sliding on the pad): the vibration follows the new tempo
    LaunchedEffect(playBpm, timeSignature) {
        if (isHolding) startHoldHaptics(SystemClock.uptimeMillis())
    }

    // A note longer than the bar can't be picked: fall back to the longest one that fits
    LaunchedEffect(timeSignature) {
        if (!timeSignature.fits(freeNote)) freeNote = NoteLength.entries.last { timeSignature.fits(it) }
    }

    // Hold display: per frame so the ring fills smoothly
    LaunchedEffect(isHolding) {
        while (isHolding) {
            withFrameMillis { }
            holdDurationMs = SystemClock.uptimeMillis() - startTime
        }
    }

    // Hold feedback: the tone swells as you hold (vibration is one pattern, see startHold)
    LaunchedEffect(isHolding) {
        while (isHolding) {
            val p = (SystemClock.uptimeMillis() - startTime).toFloat() / currentTargetMs
            audioManager.updateVolume(0.3f + 0.2f * min(1f, p)) // tone + click stays < 1.0
            delay(50)
        }
    }

    val progress = if (isHolding) holdDurationMs.toFloat() / targetMs else lastProgress
    val inWindow = progress >= 1f - GOOD_WINDOW && progress <= 1f + GOOD_WINDOW
    val overshoot = progress > 1f + GOOD_WINDOW
    val stateColor = when {
        overshoot -> OverRed
        inWindow -> ReleaseGreen
        else -> RingBlue
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
                onPlayStop = { if (isPlaying) isPlaying = false else startPlaying() },
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

            Spacer(modifier = Modifier.weight(1f))

            // Hold pad: beat ripple, progress ring, circle
            val padColor = when {
                !lightEnabled || !isHolding -> PadBlue
                overshoot -> OverRed
                inWindow -> ReleaseGreen
                else -> PadBlue
            }
            // Liquid-style fill level while holding (light on, before the release window)
            val fillLevel = if (lightEnabled && isHolding && !inWindow && !overshoot) progress.coerceIn(0f, 1f) else 0f
            val ringAlpha by animateFloatAsState(
                targetValue = if (isHolding) 1f else 0f,
                animationSpec = tween(durationMillis = if (isHolding) 120 else 900),
                label = "ringAlpha"
            )
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
                                holdDurationMs = 0
                                lastInteractionAt = pressedAt
                                pressSolo = !isPlaying || clickVolume == 0f || !settings.beatSound // no audible beat
                                pressBarPulse = if (isPlaying && lastBeatAt > 0 && beatInBar >= 0) {
                                    val pulse = timeSignature.pulseMs(playBpm)
                                    val ahead = ((pressedAt - lastBeatAt) / pulse).roundToInt() // nearest pulse
                                    (beatInBar + ahead) % timeSignature.pulsesPerBar
                                } else 0 // without the beat, a hold starts a bar
                                pressOnsetError = if (isPlaying && lastBeatAt > 0) {
                                    val interval = timeSignature.pulseMs(playBpm).toFloat()
                                    val phase = ((pressedAt - lastBeatAt) % interval) / interval
                                    min(phase, 1f - phase)
                                } else 1f
                                if (soundEnabled) audioManager.startTone(0.3f)
                                startHoldHaptics(pressedAt)

                                // Follow the finger until it lifts. Sliding changes the tempo: up = faster,
                                // sideways along the slider's direction; leaving the pad doesn't cancel.
                                var releasedAt: Long? = null
                                var last = down.position
                                var moved = 0f
                                var sliding = false
                                var stepAcc = 0f
                                val stepPx = 40.dp.toPx() // one tempo mark per 40 dp, like the << >> buttons
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
                                        if (!sliding && moved > viewConfiguration.touchSlop) sliding = true
                                        if (sliding) {
                                            stepAcc += d.x * sliderSign - d.y
                                            val steps = (stepAcc / stepPx).toInt()
                                            if (steps != 0) {
                                                stepAcc -= steps * stepPx
                                                repeat(kotlin.math.abs(steps)) { jumpMark(if (steps > 0) 1 else -1) }
                                            }
                                            // Direction of the border zone the finger is in: up / slider-forward = faster
                                            val fromCenter = last - Offset(size.width / 2f, size.height / 2f)
                                            val dist = fromCenter.getDistance()
                                            val lean = if (dist > 0f) (fromCenter.x * sliderSign - fromCenter.y) / dist else 0f
                                            val newDir = if (dist >= edgeRadius && kotlin.math.abs(lean) > 0.3f) (if (lean > 0) 1 else -1) else 0
                                            if (newDir != edgeDir) edgeRepeats = 0
                                            edgeDir = newDir
                                        }
                                        change.consume()
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    isHolding = false // screen went away mid-hold: silence everything
                                    audioManager.stopTone()
                                    hapticManager.stop()
                                    throw e
                                }
                                val heldMs = (releasedAt ?: SystemClock.uptimeMillis()) - startTime
                                isHolding = false
                                audioManager.stopTone()
                                val (stopIn, holdId) = hapticManager.endHold(SystemClock.uptimeMillis())
                                if (stopIn > 0) scope.launch { delay(stopIn); hapticManager.stopHold(holdId) } // let the pulse finish
                                lastInteractionAt = SystemClock.uptimeMillis()
                                lastProgress = heldMs.toFloat() / currentTargetMs
                                if (heldMs < MIN_HOLD_MS) return@awaitEachGesture

                                // The player gets it: let the hint go after a couple of holds
                                holdsSinceHint++
                                if (holdsSinceHint >= HOLDS_TO_HIDE_HINT) hintVisible = false

                                val accuracy = progressManager.calculateAccuracy(currentTargetMs, heldMs)
                                val result = HoldResult(heldMs, currentTargetMs, accuracy)
                                lastResult = result
                                history.add(result)
                                if (history.size > HISTORY_SIZE) history.removeAt(0)

                                // On the beat and the right length: fade the click so they carry the rhythm
                                if (isPlaying) {
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

                // Progress ring: fills toward the target; green = release now, red = too long
                Canvas(modifier = Modifier.size(262.dp)) {
                    val stroke = 9.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                    )
                    drawArc(
                        color = stateColor.copy(alpha = ringAlpha),
                        startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    // Pulse marks: one per click of the time signature (eighths in 6/8), so the fill moves
                    // with the beat; where a new bar starts, the mark is bigger and gold
                    val radius = (size.width - stroke) / 2
                    val pulses = targetNote.beats * timeSignature.pulsesPerQuarter
                    var k = 1
                    while (k < pulses) {
                        val angle = Math.toRadians((-90.0 + 360.0 * k / pulses))
                        val barStart = (pressBarPulse + k) % timeSignature.pulsesPerBar == 0
                        val lit = isHolding && progress * pulses >= k // lit as the fill passes
                        drawCircle(
                            color = if (barStart) StarGold.copy(alpha = if (lit) 1f else 0.55f)
                                else Color.White.copy(alpha = if (lit) 0.9f else 0.3f),
                            radius = stroke * if (barStart) 0.5f else 0.32f,
                            center = Offset(center.x + radius * kotlin.math.cos(angle).toFloat(), center.y + radius * kotlin.math.sin(angle).toFloat())
                        )
                        k++
                    }
                }

                // Main circle: fixed size, never moves
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(padColor)
                        .drawBehind {
                            if (fillLevel > 0f) {
                                val top = size.height * (1f - fillLevel)
                                drawRect(FillBlue, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) BarDots(timeSignature, beatInBar, Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
                    PadLabel(
                        text = when {
                            isHolding && inWindow -> stringResource(R.string.release_now)
                            isHolding && !overshoot && hintVisible -> stringResource(R.string.holding)
                            !isHolding && hintVisible -> stringResource(R.string.press_and_hold)
                            else -> null
                        },
                        seconds = if (isHolding) holdDurationMs / 1000.0 else null,
                        targetSeconds = targetMs / 1000.0
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom: this session's timing history, the last result and a gentle tip (fixed height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                TimingHistory(history, modifier = Modifier.fillMaxWidth().height(44.dp))
                val result = lastResult
                Text(
                    text = when {
                        result != null -> stringResource(
                            R.string.lesson_result, result.heldMs / 1000.0, (result.ratio * 100).roundToInt()
                        )
                        currentLesson != null -> stringResource(R.string.hint_lesson)
                        else -> stringResource(R.string.hint_free_play)
                    },
                    fontSize = 14.sp,
                    color = result?.let { scoreColor(it.ratio) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val tip = timingTip(history)
                Text(
                    text = tip?.let { stringResource(it) } ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Star earned: full-screen celebration on top
        if (celebrateStars > 0) {
            StarCelebration(stars = celebrateStars, onDone = { celebrateStars = 0 })
        }
    }
}

/** Label inside the pad: fading hint, and the elapsed time while holding (the target at rest). */
@Composable
private fun PadLabel(text: String?, seconds: Double?, targetSeconds: Double) {
    var shownText by remember { mutableStateOf(text ?: "") }
    if (text != null) shownText = text
    val textAlpha by animateFloatAsState(if (text != null) 1f else 0f, tween(600), label = "labelAlpha")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(shownText, color = Color.White, fontSize = 22.sp, modifier = Modifier.alpha(textAlpha))
        Text(
            if (seconds != null) stringResource(R.string.seconds_1dp, seconds)
            else stringResource(R.string.hold_for, targetSeconds),
            color = Color.White.copy(alpha = if (seconds != null) 1f else 0.75f),
            fontSize = if (seconds != null) 28.sp else 20.sp,
            modifier = Modifier.padding(top = 8.dp)
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
private fun scoreColor(ratio: Float): Color {
    val off = kotlin.math.abs(ratio - 1f)
    return when {
        off <= GOOD_WINDOW -> Color(0xFF66DD77)
        off <= 0.3f -> Color(0xFFFFC857)
        else -> OverRed
    }
}

/** A tip only when the last holds clearly lean one way; otherwise nothing (no nagging). */
private fun timingTip(history: List<HoldResult>): Int? {
    val recent = history.takeLast(6)
    if (recent.size < 5) return null
    val mean = recent.map { it.ratio - 1f }.average()
    val late = recent.count { it.ratio > 1f + 0.03f }
    val early = recent.count { it.ratio < 1f - 0.03f }
    return when {
        mean > 0.05 && late >= 4 -> R.string.tip_long
        mean < -0.05 && early >= 4 -> R.string.tip_short
        else -> null
    }
}

/**
 * This session's holds as dots around the target line: above = held too long, below = too short.
 * Soft green band = the good zone; older dots fade. Newest at the reading end (right; left in RTL).
 */
@Composable
private fun TimingHistory(history: List<HoldResult>, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val longLabel = stringResource(R.string.history_long)
    val shortLabel = stringResource(R.string.history_short)
    Box(modifier.padding(horizontal = 24.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.height / 2
            val range = 0.4f // +/-40% fills the strip
            val band = mid * GOOD_WINDOW / range
            drawRect(Color(0xFF66DD77).copy(alpha = 0.10f), Offset(0f, mid - band), Size(size.width, band * 2))
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
            if (history.isEmpty()) return@Canvas
            val slot = size.width / HISTORY_SIZE
            val n = history.size
            var prev: Offset? = null
            history.forEachIndexed { i, r ->
                val fromEnd = n - 1 - i
                val xLtr = size.width - slot / 2 - fromEnd * slot
                val x = if (rtl) size.width - xLtr else xLtr
                val dev = (r.ratio - 1f).coerceIn(-range, range)
                val p = Offset(x, mid - dev / range * mid)
                val age = 0.35f + 0.65f * (i + 1f) / n
                prev?.let { drawLine(Color.White.copy(alpha = 0.12f * age), it, p, strokeWidth = 1.5.dp.toPx()) }
                drawCircle(scoreColor(r.ratio).copy(alpha = age), radius = if (fromEnd == 0) 5.dp.toPx() else 3.5.dp.toPx(), center = p)
                prev = p
            }
        }
        // Tiny axis hints at the start edge
        Text(longLabel, fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopStart))
        Text(shortLabel, fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.BottomStart))
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
