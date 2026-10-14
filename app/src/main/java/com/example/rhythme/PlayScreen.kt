package com.example.rhythme

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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.res.stringArrayResource
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
private val PITCHES_HZ = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25)
private const val DEFAULT_PITCH = 5 // A4

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

/** Player choices that should survive moving between free play and lessons. */
class PlaySettings {
    var soundEnabled by mutableStateOf(true)
    var vibrationEnabled by mutableStateOf(true)
    var lightEnabled by mutableStateOf(true)
    var pitchIndex by mutableIntStateOf(DEFAULT_PITCH)
    var freeNote by mutableStateOf(NoteLength.QUARTER)
}

@Composable
fun RhythmPlayScreen(
    settings: PlaySettings,
    hapticManager: HapticManager,
    audioManager: AudioManager,
    progressManager: ProgressManager,
    currentLesson: Lesson?,
    onOpenLessons: () -> Unit,
    onLessonComplete: (Lesson, Float) -> Unit
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
    val ripple = remember { Animatable(1f) }

    // Hold
    var isHolding by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var holdDurationMs by remember { mutableLongStateOf(0L) }
    var lastProgress by remember { mutableFloatStateOf(0f) }
    var pressOnsetError by remember { mutableFloatStateOf(1f) }

    // "Press & Hold" hint
    var hintVisible by remember { mutableStateOf(true) }
    var holdsSinceHint by remember { mutableIntStateOf(0) }
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }

    // Results and lesson progress
    val lesson by rememberUpdatedState(currentLesson)
    val onComplete by rememberUpdatedState(onLessonComplete)
    var lastResult by remember { mutableStateOf<Pair<Long, Float>?>(null) } // held ms, accuracy
    var lessonProgress by remember {
        mutableStateOf(currentLesson?.let { progressManager.getProgress(it.id) })
    }

    val targetMs = targetNote.durationMs(playBpm)
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

    // Tempo applies once the slider / buttons rest; until then the old tempo keeps playing
    LaunchedEffect(pendingBpm) {
        if (pendingBpm != playBpm) {
            delay(TEMPO_SETTLE_MS)
            playBpm = pendingBpm
        }
    }
    LaunchedEffect(playBpm, targetNote) { clickVolume = 1f } // new tempo or note: full beat again

    // Beat clock: scheduled on absolute times so it never drifts
    LaunchedEffect(isPlaying, playBpm) {
        if (!isPlaying) return@LaunchedEffect
        val interval = 60000.0 / playBpm
        val now = SystemClock.uptimeMillis()
        // Continue the pulse across tempo changes instead of restarting it
        val first = if (lastBeatAt > 0 && lastBeatAt + interval > now) lastBeatAt + interval else now.toDouble()
        var n = 0
        while (true) {
            val at = (first + n * interval).toLong()
            delay(at - SystemClock.uptimeMillis())
            lastBeatAt = at
            beatTick++
            if (clickVolume > 0f) audioManager.playClick(clickVolume) // the speaker toggle mutes only the held note
            if (vibrationEnabled) hapticManager.tick()
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

            PitchPicker(
                selected = pitchIndex,
                muted = !soundEnabled,
                onSelect = { i ->
                    pitchIndex = i
                    audioManager.frequency = PITCHES_HZ[i]
                    if (soundEnabled && !isHolding) {
                        scope.launch { // short preview of the new pitch
                            audioManager.startTone(0.3f)
                            delay(250)
                            if (!isHolding) audioManager.stopTone()
                        }
                    }
                }
            )

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
                .height(60.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (currentLesson != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NoteGlyph(currentLesson.targetNoteLength, MaterialTheme.colorScheme.onSurface, height = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(currentLesson.titleRes), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        val status = if (lessonProgress?.isMastered == true) stringResource(R.string.lesson_mastered)
                            else stringResource(R.string.lesson_streak, lessonProgress?.streak ?: 0)
                        Text(
                            stringResource(R.string.lesson_target, targetMs / 1000.0) + "  ·  " + status,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                NoteValuePicker(selected = freeNote, onSelect = { freeNote = it; lastResult = null })
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
                            pressOnsetError = if (isPlaying && lastBeatAt > 0) {
                                val interval = 60000f / playBpm
                                val phase = ((pressedAt - lastBeatAt) % interval) / interval
                                min(phase, 1f - phase)
                            } else 1f
                            if (soundEnabled) audioManager.startTone(0.3f)
                            if (vibrationEnabled) {
                                // Whole-hold vibration: buzz, a gap + tick at each upcoming beat, release pulse
                                val beats = if (isPlaying && lastBeatAt > 0) {
                                    val interval = 60000.0 / playBpm
                                    val horizon = pressedAt + currentTargetMs * 2 + 1500
                                    generateSequence(lastBeatAt + interval) { it + interval }
                                        .map { it.toLong() }
                                        .dropWhile { it < pressedAt - 70 }
                                        .takeWhile { it < horizon }
                                        .toList()
                                } else emptyList()
                                hapticManager.startHold(pressedAt, currentTargetMs, beats)
                            }

                            val up = waitForUpOrCancellation()
                            up?.consume()
                            val heldMs = (up?.uptimeMillis ?: SystemClock.uptimeMillis()) - startTime
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
                            lastResult = heldMs to accuracy

                            // On the beat and the right length: fade the click so they carry the rhythm
                            if (isPlaying) {
                                val good = accuracy >= 0.8f && pressOnsetError <= ONSET_TOLERANCE
                                val next = if (good) clickVolume - 0.2f else clickVolume + 0.4f
                                clickVolume = (next * 10).roundToInt().coerceIn(0, 10) / 10f // whole steps: reaches exactly 0
                            }

                            lesson?.let { l ->
                                onComplete(l, accuracy)
                                lessonProgress = progressManager.getProgress(l.id)
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
                            scaleX = 1f + 0.12f * p
                            scaleY = 1f + 0.12f * p
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
                // Beat marks: the ring is divided into the note's beats, so the fill moves with the beat
                val radius = (size.width - stroke) / 2
                val beats = targetNote.beats
                var k = 1
                while (k < beats) {
                    val angle = Math.toRadians((-90.0 + 360.0 * k / beats))
                    drawCircle(
                        color = Color.White.copy(alpha = if (isHolding && progress * beats >= k) 0.9f else 0.3f), // lit as the fill passes
                        radius = stroke * 0.32f,
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
                PadLabel(
                    text = when {
                        isHolding && inWindow -> stringResource(R.string.release_now)
                        isHolding && !overshoot && hintVisible -> stringResource(R.string.holding)
                        !isHolding && hintVisible -> stringResource(R.string.press_and_hold)
                        else -> null
                    },
                    seconds = if (isHolding) holdDurationMs / 1000.0 else null
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom: hint, or the last attempt's result
        val result = lastResult
        Text(
            text = when {
                result != null -> stringResource(R.string.lesson_result, result.first / 1000.0, (result.second * 100).toInt())
                currentLesson != null -> stringResource(R.string.hint_lesson)
                else -> stringResource(R.string.hint_free_play)
            },
            fontSize = 14.sp,
            color = when {
                result == null -> MaterialTheme.colorScheme.onSurfaceVariant
                result.second >= 0.8f -> Color(0xFF66DD77)
                result.second >= 0.5f -> Color(0xFFFFC857)
                else -> OverRed
            }
        )
    }
}

/** Label inside the pad that fades in and out smoothly instead of popping. */
@Composable
private fun PadLabel(text: String?, seconds: Double?) {
    var shownText by remember { mutableStateOf(text ?: "") }
    if (text != null) shownText = text
    val textAlpha by animateFloatAsState(if (text != null) 1f else 0f, tween(600), label = "labelAlpha")
    val secondsAlpha by animateFloatAsState(if (seconds != null) 1f else 0f, tween(400), label = "secondsAlpha")
    var shownSeconds by remember { mutableStateOf(0.0) }
    if (seconds != null) shownSeconds = seconds

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(shownText, color = Color.White, fontSize = 22.sp, modifier = Modifier.alpha(textAlpha))
        Text(
            stringResource(R.string.seconds_1dp, shownSeconds),
            color = Color.White,
            fontSize = 28.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(secondsAlpha)
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
private fun PitchPicker(selected: Int, muted: Boolean, onSelect: (Int) -> Unit) {
    val names = stringArrayResource(R.array.pitch_names)
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.cd_pitch)
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(48.dp).semantics { contentDescription = label }
        ) {
            Text("♪" + names[selected], fontSize = 16.sp, color = Color.Green.copy(alpha = if (muted) 0.35f else 1f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name, color = if (i == selected) Color.Green else Color.Unspecified) },
                    onClick = {
                        open = false
                        onSelect(i)
                    }
                )
            }
        }
    }
}

@Composable
private fun NoteValuePicker(selected: NoteLength, onSelect: (NoteLength) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.hold_note_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        NoteLength.entries.forEach { note ->
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
