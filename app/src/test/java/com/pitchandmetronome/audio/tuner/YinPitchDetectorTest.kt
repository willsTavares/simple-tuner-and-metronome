package com.pitchandmetronome.audio.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
import kotlin.random.Random

class YinPitchDetectorTest {

    private val sampleRate = 48_000
    private val windowSize = 4_096
    private val detector = YinPitchDetector(windowSize, sampleRate)

    private fun centsError(detected: Float, expected: Double): Double =
        1200.0 * log2(detected / expected)

    private fun tone(freq: Double, phase: Double = 0.0, harmonics: Int = 1): FloatArray =
        FloatArray(windowSize) { i ->
            var s = 0.0
            for (h in 1..harmonics) {
                s += sin(2 * PI * freq * h * i / sampleRate + phase * h) / h
            }
            (0.3 * s).toFloat()
        }

    @Test
    fun `pure sine is detected within half a cent across the guitar range`() {
        // Frequências cujo período não é inteiro em amostras — exatamente onde a
        // interpolação parabólica decide a precisão.
        val frequencies = listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63, 440.0, 987.77)
        for (f in frequencies) {
            for (phase in listOf(0.0, 1.0, 2.5)) {
                val detected = detector.detect(tone(f, phase))
                val error = centsError(detected, f)
                assertTrue("f=$f phase=$phase erro=$error cents", abs(error) < 0.5)
            }
        }
    }

    @Test
    fun `harmonic-rich tone keeps fundamental without octave error`() {
        for (f in listOf(82.41, 196.0, 329.63)) {
            val detected = detector.detect(tone(f, harmonics = 6))
            val error = centsError(detected, f)
            assertTrue("f=$f erro=$error cents", abs(error) < 1.0)
        }
    }

    @Test
    fun `silence and low-level noise report no pitch`() {
        assertEquals(0f, detector.detect(FloatArray(windowSize)), 0f)

        val random = Random(42)
        val noise = FloatArray(windowSize) { (random.nextFloat() - 0.5f) * 0.002f }
        assertEquals(0f, detector.detect(noise), 0f)
    }

    @Test
    fun `confidence is high for a clean tone`() {
        detector.detect(tone(440.0))
        assertTrue(detector.confidence > 0.9f)
    }
}
