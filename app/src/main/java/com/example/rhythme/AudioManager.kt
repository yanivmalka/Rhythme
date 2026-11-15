package com.example.rhythme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.StringRes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** How a note of an instrument evolves while it is held. */
enum class Envelope {
    SUSTAIN, // steady for as long as it is held (organ, clarinet)
    PIANO,   // a strike that settles to a gentle sustain
    BOWED,   // swells in, then sings with vibrato (violin)
    PLUCK,   // a pluck that dies away to a faint ring (guitar)
    DRUM     // a hit: a short thump with a burst of noise, then silence
}

/**
 * Instruments for the held note. Each is a different kind of sound, not only a different color:
 * a set of harmonics (amplitudes of the 1st, 2nd, 3rd... harmonic) plus how the note evolves.
 */
enum class Instrument(@StringRes val nameRes: Int, harmonics: FloatArray, val envelope: Envelope = Envelope.SUSTAIN) {
    PURE(R.string.instrument_pure, floatArrayOf(1f)),
    ORGAN(R.string.instrument_organ, floatArrayOf(1f, 0.5f, 0.33f, 0.25f)),
    CLARINET(R.string.instrument_clarinet, floatArrayOf(1f, 0f, 0.45f, 0f, 0.25f, 0f, 0.12f)),
    VIOLIN(R.string.instrument_violin, floatArrayOf(1f, 0.5f, 0.33f, 0.25f, 0.2f, 0.17f, 0.14f, 0.12f), Envelope.BOWED),
    GUITAR(R.string.instrument_guitar, floatArrayOf(1f, 0.55f, 0.4f, 0.2f, 0.12f, 0.06f), Envelope.PLUCK),
    PIANO(R.string.instrument_piano, floatArrayOf(1f, 0.6f, 0.3f, 0.15f), Envelope.PIANO),
    DRUM(R.string.instrument_drum, floatArrayOf(1f, 0.25f), Envelope.DRUM),
    RETRO(R.string.instrument_retro, floatArrayOf(1f, 0f, 1 / 3f, 0f, 1 / 5f, 0f, 1 / 7f, 0f, 1 / 9f));

    /** Normalized so every instrument peaks at the same level. */
    val weights: FloatArray = harmonics.sum().let { total -> FloatArray(harmonics.size) { harmonics[it] / total } }

    /** Struck or plucked: the note is loud at its start and can't swell, so it plays at a fixed level. */
    val percussive: Boolean get() = envelope == Envelope.PLUCK || envelope == Envelope.DRUM
}

/**
 * One long-lived streaming AudioTrack that mixes the hold tone and beat clicks.
 * (Creating a new AudioTrack per beep was too heavy and failed on the emulator.)
 */
class AudioManager {
    private val sampleRate = 44100
    @Volatile var frequency = 440.0 // pitch of the hold tone (A4 by default)
    private val chunkSize = sampleRate / 100 // 10ms per write keeps latency low

    @Volatile var instrument = Instrument.PURE
    @Volatile private var toneOn = false
    @Volatile private var toneVolume = 0.3f
    /** The player's volume choice for the held note and the demo's cue tone; 1 = normal. */
    @Volatile var toneLevel = 1f
    private val clickRequested = AtomicBoolean(false)
    private val strikeRequested = AtomicBoolean(false)
    @Volatile private var clickVolume = 1f
    private val chimeRequests = ConcurrentLinkedQueue<FloatArray>() // each one rings out in full, mixed with the others
    @Volatile private var running = false
    private var thread: Thread? = null

    // 40 ms clicks with fast exponential decay: normal, medium accent, strong accent (beat 1)
    private fun makeClick(hz: Double, level: Double) = FloatArray(sampleRate * 40 / 1000) { i ->
        val t = i.toDouble() / sampleRate
        (sin(2 * PI * hz * t) * exp(-t * 90) * level).toFloat()
    }
    private val clicks = arrayOf(makeClick(1000.0, 0.45), makeClick(1250.0, 0.5), makeClick(1600.0, 0.6))
    @Volatile private var clickAccent = 0

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ renderLoop() }, "RhythmeAudio").apply { start() }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    fun startTone(volume: Float = 0.3f) {
        toneVolume = volume
        toneOn = true
    }

    fun updateVolume(volume: Float) {
        toneVolume = volume
    }

    /** Starts the held note over (a new hit or pluck) without releasing it: each cycle of a long hold. */
    fun strike() {
        strikeRequested.set(true)
    }

    fun stopTone() {
        toneOn = false
    }

    /** [accent]: 0 normal, 1 medium, 2 strong (first beat of the bar). */
    fun playClick(volume: Float = 1f, accent: Int = 0) {
        clickVolume = volume.coerceIn(0f, 1.5f)
        clickAccent = accent.coerceIn(0, 2)
        clickRequested.set(true)
    }

    /**
     * Bell notes (a rising arpeggio for celebrations, single notes for cues): they start [gapMs]
     * apart and ring out, overlapping. Rendered here once, then mixed by the audio thread.
     * A new chime never cuts off one that is still ringing.
     */
    fun playChime(frequencies: DoubleArray, gapMs: Int = 110, level: Double = 0.22) {
        val ringMs = 600
        val total = ((frequencies.size - 1) * gapMs + ringMs) * sampleRate / 1000
        val out = FloatArray(total)
        frequencies.forEachIndexed { n, f ->
            val start = n * gapMs * sampleRate / 1000
            for (i in 0 until ringMs * sampleRate / 1000) {
                val t = i.toDouble() / sampleRate
                val bell = sin(2 * PI * f * t) + 0.35 * sin(2 * PI * f * 2 * t) // fundamental + octave shimmer
                out[start + i] += (bell * exp(-t * 6) * level).toFloat()
            }
        }
        chimeRequests.add(out)
    }

    fun release() {
        stopTone()
        stop()
    }

    private fun renderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track = try {
            buildTrack()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
            running = false
            return
        }
        track.play()

        val buffer = ShortArray(chunkSize)
        val rampStep = 1f / (sampleRate * 0.005f) // 5ms fade avoids clicks on start/stop
        var phase = 0.0
        var gain = 0f
        var wasOn = false
        var toneSamples = 0 // since the current note started (for the envelopes)
        var noise = 12345
        var click = clicks[0]
        var clickPos = click.size
        val chimes = ArrayList<FloatArray>() // bells ringing now, each with its own position
        val chimePos = ArrayList<Int>()

        while (running) {
            if (clickRequested.getAndSet(false)) { click = clicks[clickAccent]; clickPos = 0 }
            while (true) {
                val request = chimeRequests.poll() ?: break
                if (chimes.size >= 8) { chimes.removeAt(0); chimePos.removeAt(0) } // far more than ever overlap
                chimes.add(request)
                chimePos.add(0)
            }
            val phaseStep = 2.0 * PI * frequency / sampleRate
            val clickGain = clickVolume
            val on = toneOn
            val voice = instrument
            if (strikeRequested.getAndSet(false) || (on && !wasOn)) toneSamples = 0
            wasOn = on
            val target = if (!on) 0f else (if (voice.percussive) 0.45f else toneVolume) * toneLevel
            val weights = voice.weights
            for (i in 0 until chunkSize) {
                gain += (target - gain).coerceIn(-rampStep, rampStep)
                var wave = 0.0
                for (k in weights.indices) if (weights[k] != 0f) wave += weights[k] * sin((k + 1) * phase)
                val t = toneSamples.toDouble() / sampleRate
                var bend = 1.0 // vibrato: the pitch wavers slightly
                when (voice.envelope) {
                    Envelope.SUSTAIN -> Unit
                    Envelope.PIANO -> wave *= 0.35 + 0.65 * exp(-t * 4)
                    Envelope.BOWED -> {
                        wave *= minOf(1.0, 0.2 + t / 0.09)
                        bend = 1.0 + 0.006 * minOf(1.0, t / 0.35) * sin(2 * PI * 5.5 * t)
                    }
                    Envelope.PLUCK -> wave *= 0.08 + 0.92 * exp(-t * 5)
                    Envelope.DRUM -> {
                        noise = noise * 1103515245 + 12345 // cheap white noise for the stick's attack
                        val hiss = (noise shr 16 and 0x7fff) / 16384.0 - 1.0
                        wave = wave * exp(-t * 13) + hiss * 0.55 * exp(-t * 55)
                        bend = 0.5 + 0.5 * exp(-t * 30) // the drum's pitch drops right after the hit
                    }
                }
                toneSamples++
                var s = (wave * gain).toFloat()
                phase += phaseStep * bend
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                if (clickPos < click.size) s += click[clickPos++] * clickGain
                for (c in chimes.indices) {
                    val pos = chimePos[c]
                    if (pos < chimes[c].size) {
                        s += chimes[c][pos]
                        chimePos[c] = pos + 1
                    }
                }
                buffer[i] = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            for (c in chimes.indices.reversed()) { // drop the bells that have rung out
                if (chimePos[c] >= chimes[c].size) { chimes.removeAt(c); chimePos.removeAt(c) }
            }
            track.write(buffer, 0, chunkSize) // blocks, pacing the loop
        }

        track.stop()
        track.release()
    }

    private fun buildTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, chunkSize * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "RhythmeAudio"
    }
}
