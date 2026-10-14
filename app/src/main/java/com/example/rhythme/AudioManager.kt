package com.example.rhythme

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * One long-lived streaming AudioTrack that mixes the hold tone and beat clicks.
 * (Creating a new AudioTrack per beep was too heavy and failed on the emulator.)
 */
class AudioManager {
    private val sampleRate = 44100
    @Volatile var frequency = 440.0 // pitch of the hold tone (A4 by default)
    private val chunkSize = sampleRate / 100 // 10ms per write keeps latency low

    @Volatile private var toneOn = false
    @Volatile private var toneVolume = 0.3f
    private val clickRequested = AtomicBoolean(false)
    @Volatile private var clickVolume = 1f
    @Volatile private var running = false
    private var thread: Thread? = null

    // 40ms 1kHz click with fast exponential decay
    private val click = FloatArray(sampleRate * 40 / 1000) { i ->
        val t = i.toDouble() / sampleRate
        (sin(2 * PI * 1000.0 * t) * exp(-t * 90) * 0.45).toFloat()
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

    fun stopTone() {
        toneOn = false
    }

    fun playClick(volume: Float = 1f) {
        clickVolume = volume.coerceIn(0f, 1f)
        clickRequested.set(true)
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
        var clickPos = click.size

        while (running) {
            if (clickRequested.getAndSet(false)) clickPos = 0
            val phaseStep = 2.0 * PI * frequency / sampleRate
            val clickGain = clickVolume
            val target = if (toneOn) toneVolume else 0f
            for (i in 0 until chunkSize) {
                gain += (target - gain).coerceIn(-rampStep, rampStep)
                var s = (sin(phase) * gain).toFloat()
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                if (clickPos < click.size) s += click[clickPos++] * clickGain
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
