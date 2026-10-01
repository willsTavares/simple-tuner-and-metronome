package com.pitchandmetronome.domain.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

class PitchStabilizerTest {

    private val holdFrames = 8

    private fun cents(hz: Float, reference: Double): Double = 1200.0 * log2(hz / reference)

    private fun offset(reference: Double, cents: Double): Float =
        (reference * 2.0.pow(cents / 1200.0)).toFloat()

    @Test
    fun `waits for enough readings before showing anything`() {
        val s = PitchStabilizer(holdFrames)
        assertEquals(PitchStabilizer.UNVOICED, s.process(440f), 0f)
        assertEquals(PitchStabilizer.UNVOICED, s.process(440f), 0f)
        assertEquals(440f, s.process(440f), 0.01f)
    }

    @Test
    fun `jitter of a steady note is reduced well below its amplitude`() {
        val s = PitchStabilizer(holdFrames)
        // ±3 cents alternando — pior caso de jitter frame a frame.
        var worst = 0.0
        repeat(60) { i ->
            val out = s.process(offset(440.0, if (i % 2 == 0) 3.0 else -3.0))
            if (i > 10) worst = maxOf(worst, abs(cents(out, 440.0)))
        }
        assertTrue("pior desvio exibido = $worst cents", worst < 1.0)
    }

    @Test
    fun `isolated octave error never reaches the output`() {
        val s = PitchStabilizer(holdFrames)
        repeat(10) { s.process(440f) }
        val out = s.process(220f)
        assertEquals(0.0, cents(out, 440.0), 0.1)
        assertEquals(0.0, cents(s.process(440f), 440.0), 0.1)
    }

    @Test
    fun `real note change is followed after confirmation`() {
        val s = PitchStabilizer(holdFrames)
        repeat(10) { s.process(440f) }
        val aSharp = 466.16f
        s.process(aSharp)
        s.process(aSharp)
        val out = s.process(aSharp)
        assertEquals(0.0, cents(out, aSharp.toDouble()), 0.5)
    }

    @Test
    fun `slow tuning movement is tracked closely`() {
        val s = PitchStabilizer(holdFrames)
        repeat(10) { s.process(offset(440.0, -20.0)) }
        // Tarraxa subindo 1 cent por frame até o alvo.
        var out = 0f
        for (c in -20..0) out = s.process(offset(440.0, c.toDouble()))
        repeat(10) { out = s.process(440f) }
        assertEquals(0.0, cents(out, 440.0), 0.5)
    }

    @Test
    fun `short silence holds the last value and long silence clears it`() {
        val s = PitchStabilizer(holdFrames)
        repeat(10) { s.process(440f) }
        repeat(holdFrames) {
            assertEquals(440f, s.process(0f), 0.01f)
        }
        assertEquals(PitchStabilizer.UNVOICED, s.process(0f), 0f)
    }
}
