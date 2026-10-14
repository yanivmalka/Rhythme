package com.example.rhythme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.StringRes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * One long-lived streaming AudioTrack that mixes the hold tone and beat clicks.
 * (Creating a new AudioTrack per beep was too heavy and failed on the emulator.)
 */
/** Timbres for the held note, built from harmonics (amplitudes of the 1st, 2nd, 3rd... harmonic). */
enum class Instrument(@StringRes val nameRes: Int, harmonics: FloatArray, val pianoEnvelope: Boolean = false) {
    PURE(R.string.instrument_pure, floatArrayOf(1f)),
    ORGAN(R.string.instrument_organ, floatArrayOf(1f, 0.5f, 0.33f, 0.25f)),
    CLARINET(R.string.instrument_clarinet, floatArrayOf(1f, 0f, 0.45f, 0f, 0.25f, 0f, 0.12f)),
    RETRO(R.string.instrument_retro, floatArrayOf(1f, 0f, 1 / 3f, 0f, 1 / 5f, 0f, 1 / 7f, 0f, 1 / 9f)),
    PIANO(R.string.instrument_piano, floatArrayOf(1f, 0.6f, 0.3f, 0.15f), pianoEnvelope = true);

    /** Normalized so every instrument peaks at the same level. */
    val weights: FloatArray = harmonics.sum().let { total -> FloatArray(harmonics.size) { harmonics[it] / total } }
}

class AudioManager {
    private val sampleRate = 44100
    @Volatile var frequency = 440.0 // pitch of the hold tone (A4 by default)
    private val chunkSize = sampleRate / 100 // 10ms per write keeps latency low

    @Volatile var instrument = Instrument.PURE
    @Volatile private var toneOn = false
    @Volatile private var toneVolume = 0.3f
    private val clickRequested = AtomicBoolean(false)
    @Volatile private var clickVolume = 1f
    private val chimeRequested = AtomicReference<FloatArray?>(null)
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

    fun stopTone() {
        toneOn = false
    }

    /** [accent]: 0 normal, 1 medium, 2 strong (first beat of the bar). */
    fun playClick(volume: Float = 1f, accent: Int = 0) {
        clickVolume = volume.coerceIn(0f, 1f)
        clickAccent = accent.coerceIn(0, 2)
        clickRequested.set(true)
    }

    /**
     * Rising bell arpeggio for celebrations: notes start [gapMs] apart and ring out,
     * overlapping. Rendered here once, then mixed by the audio thread.
     */
    fun playChime(frequencies: DoubleArray, gapMs: Int = 110) {
        val ringMs = 600
        val total = ((frequencies.size - 1) * gapMs + ringMs) * sampleRate / 1000
        val out = FloatArray(total)
        frequencies.forEachIndexed { n, f ->
            val start = n * gapMs * sampleRate / 1000
            for (i in 0 until ringMs * sampleRate / 1000) {
                val t = i.toDouble() / sampleRate
                val bell = sin(2 * PI * f * t) + 0.35 * sin(2 * PI * f * 2 * t) // fundamental + octave shimmer
                out[start + i] += (bell * exp(-t * 6) * 0.22).toFloat()
            }
        }
        chimeRequested.set(out)
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
        var toneSamples = 0 // since the current note started (piano envelope)
        var click = clicks[0]
        var clickPos = click.size
        var chime: FloatArray? = null
        var chimePos = 0

        while (running) {
            if (clickRequested.getAndSet(false)) { click = clicks[clickAccent]; clickPos = 0 }
            chimeRequested.getAndSet(null)?.let { chime = it; chimePos = 0 }
            val phaseStep = 2.0 * PI * frequency / sampleRate
            val clickGain = clickVolume
            val on = toneOn
            if (on && !wasOn) toneSamples = 0
            wasOn = on
            val target = if (on) toneVolume else 0f
            val voice = instrument
            val weights = voice.weights
            for (i in 0 until chunkSize) {
                gain += (target - gain).coerceIn(-rampStep, rampStep)
                var wave = 0.0
                for (k in weights.indices) if (weights[k] != 0f) wave += weights[k] * sin((k + 1) * phase)
                if (voice.pianoEnvelope) { // strike, then settle to a gentle sustain
                    val t = toneSamples.toDouble() / sampleRate
                    wave *= 0.35 + 0.65 * exp(-t * 4)
                }
                toneSamples++
                var s = (wave * gain).toFloat()
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                if (clickPos < click.size) s += click[clickPos++] * clickGain
                chime?.let { c -> if (chimePos < c.size) s += c[chimePos++] else chime = null }
                buffer[i] = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
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
