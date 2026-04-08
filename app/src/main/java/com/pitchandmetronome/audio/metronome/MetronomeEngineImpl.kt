package com.pitchandmetronome.audio.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.domain.model.metronome.BeatConfig
import com.pitchandmetronome.domain.model.metronome.BeatEvent
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure-Kotlin implementation of [IMetronomeEngine] using [AudioTrack].
 *
 * Generates click sounds via synthesized sine-wave bursts written to an
 * [AudioTrack] in MODE_STREAM on a dedicated playback thread. Beat events
 * are emitted through a [MutableSharedFlow].
 */
@Singleton
class MetronomeEngineImpl @Inject constructor() : IMetronomeEngine {

    private companion object {
        const val SAMPLE_RATE = AudioEngineConfig.PREFERRED_SAMPLE_RATE
        const val CLICK_DURATION_MS = 15
        const val CLICK_SAMPLES = SAMPLE_RATE * CLICK_DURATION_MS / 1000
        const val ACCENT_FREQ = 1500.0
        const val NORMAL_FREQ = 1000.0
    }

    private val _beatFlow = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 8)
    override val beatFlow: Flow<BeatEvent> = _beatFlow.asSharedFlow()

    private val playing = AtomicBoolean(false)
    @Volatile private var bpm = 120
    @Volatile private var beatsPerMeasure = 4
    @Volatile private var accentFirst = true
    private val beatCounter = AtomicInteger(1)

    private var playbackThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val accentClick: ShortArray = generateClick(ACCENT_FREQ, 1.0f)
    private val normalClick: ShortArray = generateClick(NORMAL_FREQ, 0.75f)

    private fun generateClick(freq: Double, amplitude: Float): ShortArray {
        val samples = ShortArray(CLICK_SAMPLES)
        for (i in 0 until CLICK_SAMPLES) {
            val envelope = 1.0f - i.toFloat() / CLICK_SAMPLES // linear decay
            val value = (amplitude * envelope * sin(2.0 * PI * freq * i / SAMPLE_RATE))
            samples[i] = (value * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    override suspend fun start(config: BeatConfig) {
        if (playing.get()) return
        bpm = config.bpm
        beatsPerMeasure = config.timeSignature.beatsPerMeasure
        accentFirst = config.accentFirstBeat
        beatCounter.set(1)

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CLICK_SAMPLES * 2 * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        playing.set(true)
        track.play()

        playbackThread = Thread({
            val silence = ShortArray(SAMPLE_RATE / 100) // 10ms silence chunk
            while (playing.get()) {
                val currentBeat = beatCounter.get()
                val isAccent = accentFirst && currentBeat == 1
                val click = if (isAccent) accentClick else normalClick

                track.write(click, 0, click.size)
                _beatFlow.tryEmit(BeatEvent(currentBeat, isAccent, System.nanoTime()))

                // Fill remaining interval with silence
                val intervalSamples = SAMPLE_RATE * 60 / bpm - click.size
                var remaining = intervalSamples
                while (remaining > 0 && playing.get()) {
                    val chunk = minOf(remaining, silence.size)
                    track.write(silence, 0, chunk)
                    remaining -= chunk
                }

                val next = if (currentBeat >= beatsPerMeasure) 1 else currentBeat + 1
                beatCounter.set(next)
            }
        }, "MetronomePlayback").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    override suspend fun stop() {
        playing.set(false)
        playbackThread?.join(500)
        playbackThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun updateBpm(bpm: Int) {
        this.bpm = bpm
    }

    override fun updateTimeSignature(timeSignature: TimeSignature) {
        beatsPerMeasure = timeSignature.beatsPerMeasure
        beatCounter.set(1)
    }

    override fun isPlaying(): Boolean = playing.get()

    override fun release() {
        playing.set(false)
        playbackThread?.join(500)
        playbackThread = null
        audioTrack?.release()
        audioTrack = null
    }
}
