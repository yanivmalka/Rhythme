package com.example.rhythme

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.StringRes
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * Instruments for the held note. Each is a different kind of sound, not only a different color.
 * Those with a [sampleName] play recordings of the real instrument (see assets/samples); the
 * harmonics (amplitudes of the 1st, 2nd, 3rd... harmonic) and the envelope describe the
 * synthesized version, used for the others and as a fallback until the recordings are loaded.
 */
enum class Instrument(
    @StringRes val nameRes: Int,
    harmonics: FloatArray,
    val envelope: Envelope = Envelope.SUSTAIN,
    val sampleName: String? = null
) {
    PIANO(R.string.instrument_piano, floatArrayOf(1f, 0.6f, 0.3f, 0.15f), Envelope.PIANO, "piano"),
    GUITAR(R.string.instrument_guitar, floatArrayOf(1f, 0.55f, 0.4f, 0.2f, 0.12f, 0.06f), Envelope.PLUCK, "guitar"),
    VIOLIN(R.string.instrument_violin, floatArrayOf(1f, 0.5f, 0.33f, 0.25f, 0.2f, 0.17f, 0.14f, 0.12f), Envelope.BOWED, "violin"),
    CLARINET(R.string.instrument_clarinet, floatArrayOf(1f, 0f, 0.45f, 0f, 0.25f, 0f, 0.12f), Envelope.SUSTAIN, "clarinet"),
    DRUM(R.string.instrument_drum, floatArrayOf(1f, 0.25f), Envelope.DRUM, "drum"),
    ORGAN(R.string.instrument_organ, floatArrayOf(1f, 0.5f, 0.33f, 0.25f)),
    PURE(R.string.instrument_pure, floatArrayOf(1f)),
    RETRO(R.string.instrument_retro, floatArrayOf(1f, 0f, 1 / 3f, 0f, 1 / 5f, 0f, 1 / 7f, 0f, 1 / 9f));

    /** Normalized so every instrument peaks at the same level. */
    val weights: FloatArray = harmonics.sum().let { total -> FloatArray(harmonics.size) { harmonics[it] / total } }

    /** Struck or plucked: the note is loud at its start and can't swell, so it plays at a fixed level. */
    val percussive: Boolean get() = envelope == Envelope.PLUCK || envelope == Envelope.DRUM || envelope == Envelope.PIANO
}

/**
 * One recorded note. [data] is mono PCM at [rate]; other pitches are made by playing it faster
 * or slower. A sustained note loops between [loopStart] and [loopEnd] for as long as it is
 * held; [loopEnd] == 0 means it plays once and dies away.
 */
class Sample(val hz: Double, val rate: Int, val data: FloatArray, val loopStart: Int, val loopEnd: Int)

/**
 * One long-lived streaming AudioTrack that mixes the hold tone and beat clicks.
 * (Creating a new AudioTrack per beep was too heavy and failed on the emulator.)
 */
class AudioManager {
    private val sampleRate = 44100
    @Volatile var frequency = 440.0 // pitch of the hold tone (A4 by default)
    private val chunkSize = sampleRate / 100 // 10ms per write keeps latency low

    @Volatile var instrument = Instrument.PURE
    @Volatile private var samples: Map<String, List<Sample>> = emptyMap() // recordings by Instrument.sampleName
    @Volatile private var toneOn = false
    @Volatile private var toneVolume = 0.3f
    /** The player's volume choice for the held note and the demo's cue tone; 1 = normal. */
    @Volatile var toneLevel = 1f
    // A second voice that can sound together with the first: the other player's held note in
    // two-player mode. It plays any instrument, recordings included, just like the first one.
    @Volatile var frequency2 = 330.0
    @Volatile var instrument2 = Instrument.ORGAN
    @Volatile private var tone2On = false
    private val strike2Requested = AtomicBoolean(false)
    @Volatile private var tone2Volume = 0.3f
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

    /**
     * Loads the instrument recordings from assets/samples in the background. Until they are
     * ready (or if they can't be read) the instruments play their synthesized version.
     */
    fun loadSamples(assets: AssetManager) {
        Thread({
            try {
                val loaded = HashMap<String, MutableList<Sample>>()
                assets.open("samples/index.txt").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val f = line.trim().split(' ')
                        if (f.size < 5) continue
                        val (rate, data) = readWav(assets.open("samples/${f[4]}").use { it.readBytes() })
                        if (data.isEmpty()) continue
                        val hz = 440.0 * Math.pow(2.0, (f[1].toInt() - 69) / 12.0) // MIDI note to pitch
                        val loopEnd = f[3].toInt().coerceAtMost(data.size)
                        loaded.getOrPut(f[0]) { ArrayList() }.add(Sample(hz, rate, data, f[2].toInt().coerceIn(0, loopEnd), loopEnd))
                    }
                }
                samples = loaded
            } catch (e: Exception) {
                Log.e(TAG, "Loading instrument samples failed", e)
            }
        }, "RhythmeSamples").start()
    }

    /** Reads a 16-bit mono WAV: its sample rate and the audio as floats in -1..1. */
    private fun readWav(bytes: ByteArray): Pair<Int, FloatArray> {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var rate = 22050
        var pos = 12 // after "RIFF", size, "WAVE"
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = b.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") rate = b.getInt(body + 4)
            if (id == "data") {
                val n = minOf(size, bytes.size - body) / 2
                return rate to FloatArray(n) { b.getShort(body + it * 2) / 32768f }
            }
            pos = body + size + (size and 1)
        }
        return rate to FloatArray(0)
    }

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

    /** The second voice (two-player mode): starts, swells and stops like the first one. */
    fun startTone2(volume: Float = 0.3f) {
        tone2Volume = volume
        tone2On = true
    }

    fun updateVolume2(volume: Float) {
        tone2Volume = volume
    }

    fun stopTone2() {
        tone2On = false
    }

    /** The second voice's [strike]: a new hit or pluck at each cycle of a long hold. */
    fun strike2() {
        strike2Requested.set(true)
    }

    /** [accent]: 0 normal, 1 medium, 2 strong (first beat of the bar). */
    fun playClick(volume: Float = 1f, accent: Int = 0) {
        clickVolume = volume.coerceIn(0f, 2f)
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
        stopTone2()
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
        val releaseStep = 1f / (sampleRate * 0.08f) // recordings let go more gently, like the instrument
        var sample: Sample? = null // the recording playing now, if the instrument has one
        var samplePos = 0.0
        var phase = 0.0
        var gain = 0f
        var phase2 = 0.0
        var gain2 = 0f
        var sample2: Sample? = null
        var samplePos2 = 0.0
        var wasOn2 = false
        var tone2Samples = 0
        var noise2 = 54321
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
            if (strikeRequested.getAndSet(false) || (on && !wasOn)) { // a note starts
                toneSamples = 0
                samplePos = 0.0
                // The recording nearest in pitch: it is shifted by two semitones at most
                val hz = frequency
                sample = voice.sampleName?.let { samples[it] }
                    ?.minByOrNull { kotlin.math.abs(kotlin.math.ln(hz / it.hz)) }
            }
            wasOn = on
            val playing = sample
            val sampleStep = if (playing != null) playing.rate.toDouble() / sampleRate * frequency / playing.hz else 0.0
            val fall = if (playing != null) releaseStep else rampStep
            val target = if (!on) 0f else (if (voice.percussive) 0.45f else toneVolume) * toneLevel
            val weights = voice.weights
            // The second voice, set up the same way as the first
            val on2 = tone2On
            val voice2 = instrument2
            if (strike2Requested.getAndSet(false) || (on2 && !wasOn2)) {
                tone2Samples = 0
                samplePos2 = 0.0
                val hz = frequency2
                sample2 = voice2.sampleName?.let { samples[it] }
                    ?.minByOrNull { kotlin.math.abs(kotlin.math.ln(hz / it.hz)) }
            }
            wasOn2 = on2
            val playing2 = sample2
            val sampleStep2 = if (playing2 != null) playing2.rate.toDouble() / sampleRate * frequency2 / playing2.hz else 0.0
            val fall2 = if (playing2 != null) releaseStep else rampStep
            val target2 = if (!on2) 0f else (if (voice2.percussive) 0.45f else tone2Volume) * toneLevel
            val weights2 = voice2.weights
            val phaseStep2 = 2.0 * PI * frequency2 / sampleRate
            for (i in 0 until chunkSize) {
                gain += (target - gain).coerceIn(-fall, rampStep)
                var wave = 0.0
                val t = toneSamples.toDouble() / sampleRate
                var bend = 1.0 // vibrato: the pitch wavers slightly
                if (playing != null) {
                    // The recording, read at the speed that gives the wanted pitch (linear interpolation)
                    val d = playing.data
                    val ip = samplePos.toInt()
                    if (ip < d.size) {
                        val next = if (ip + 1 < d.size) d[ip + 1] else if (playing.loopEnd > 0) d[playing.loopStart] else 0f
                        wave = d[ip] + (next - d[ip]) * (samplePos - ip)
                    }
                    samplePos += sampleStep
                    if (playing.loopEnd > 0 && samplePos >= playing.loopEnd) samplePos -= playing.loopEnd - playing.loopStart
                } else {
                for (k in weights.indices) if (weights[k] != 0f) wave += weights[k] * sin((k + 1) * phase)
                when (voice.envelope) {
                    Envelope.SUSTAIN -> wave *= minOf(1.0, 0.3 + t / 0.04) // a soft attack: each new cycle is heard
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
                }
                toneSamples++
                var s = (wave * gain).toFloat()
                phase += phaseStep * bend
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                if (gain2 > 0f || target2 > 0f) { // the second voice
                    gain2 += (target2 - gain2).coerceIn(-fall2, rampStep)
                    var wave2 = 0.0
                    val t2 = tone2Samples.toDouble() / sampleRate
                    var bend2 = 1.0
                    if (playing2 != null) {
                        val d = playing2.data
                        val ip = samplePos2.toInt()
                        if (ip < d.size) {
                            val next = if (ip + 1 < d.size) d[ip + 1] else if (playing2.loopEnd > 0) d[playing2.loopStart] else 0f
                            wave2 = d[ip] + (next - d[ip]) * (samplePos2 - ip)
                        }
                        samplePos2 += sampleStep2
                        if (playing2.loopEnd > 0 && samplePos2 >= playing2.loopEnd) samplePos2 -= playing2.loopEnd - playing2.loopStart
                    } else {
                        for (k in weights2.indices) if (weights2[k] != 0f) wave2 += weights2[k] * sin((k + 1) * phase2)
                        when (voice2.envelope) {
                            Envelope.SUSTAIN -> wave2 *= minOf(1.0, 0.3 + t2 / 0.04)
                            Envelope.PIANO -> wave2 *= 0.35 + 0.65 * exp(-t2 * 4)
                            Envelope.BOWED -> {
                                wave2 *= minOf(1.0, 0.2 + t2 / 0.09)
                                bend2 = 1.0 + 0.006 * minOf(1.0, t2 / 0.35) * sin(2 * PI * 5.5 * t2)
                            }
                            Envelope.PLUCK -> wave2 *= 0.08 + 0.92 * exp(-t2 * 5)
                            Envelope.DRUM -> {
                                noise2 = noise2 * 1103515245 + 12345
                                val hiss = (noise2 shr 16 and 0x7fff) / 16384.0 - 1.0
                                wave2 = wave2 * exp(-t2 * 13) + hiss * 0.55 * exp(-t2 * 55)
                                bend2 = 0.5 + 0.5 * exp(-t2 * 30)
                            }
                        }
                    }
                    tone2Samples++
                    s += (wave2 * gain2).toFloat()
                    phase2 += phaseStep2 * bend2
                    if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                }
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
