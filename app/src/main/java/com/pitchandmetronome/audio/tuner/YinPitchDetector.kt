package com.pitchandmetronome.audio.tuner

import com.pitchandmetronome.core.audio.AudioEngineConfig
import kotlin.math.sqrt

/**
 * Estimativa de f₀ por frame via YIN (de Cheveigné & Kawahara, 2002).
 *
 * Passos: gate de nível → função de diferença → CMND → limiar absoluto com
 * ajuste ao mínimo local → interpolação parabólica sobre d(τ).
 *
 * Classe pura, sem Android — testável em JVM. Buffers alocados no construtor,
 * zero alocação por frame. **Não é thread-safe**: uma instância por thread.
 *
 * @param windowSize Amostras por janela de análise.
 * @param sampleRate Taxa de amostragem em Hz.
 */
class YinPitchDetector(
    private val windowSize: Int,
    private val sampleRate: Int
) {
    private val halfLen = windowSize / 2
    private val diff = FloatArray(halfLen)
    private val cmnd = FloatArray(halfLen)

    // sampleRate / 4200 Hz ≈ 11 @ 48 kHz — impede que vales espúrios em lags
    // minúsculos (ruído de alta frequência) sejam aceitos como pitch.
    private val tauMin = (sampleRate / MAX_DETECTABLE_FREQUENCY_HZ).toInt().coerceAtLeast(2)

    /** Confiança (1 − d'(τ)) do último frame com pitch; 0 quando não houve. */
    var confidence = 0f
        private set

    /**
     * @param samples Janela normalizada em [-1, 1], tamanho ≥ [windowSize].
     * @return Frequência em Hz, ou 0f se o frame não tem pitch confiável.
     */
    fun detect(samples: FloatArray): Float {
        confidence = 0f

        // ── Gate de nível ─────────────────────────────────────────────────
        // Ruído ambiente baixo às vezes forma vales periódicos por acaso.
        var sumSq = 0f
        for (i in 0 until windowSize) {
            val s = samples[i]
            sumSq += s * s
        }
        if (sqrt(sumSq / windowSize) < MIN_RMS) return 0f

        // ── Passo 1 — Função de diferença ─────────────────────────────────
        // d(τ) = Σ(j=0..W-1) [x(j) − x(j+τ)]²
        diff[0] = 0f
        for (tau in 1 until halfLen) {
            var sum = 0f
            for (j in 0 until halfLen) {
                val delta = samples[j] - samples[j + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        // ── Passo 2 — CMND: d'(τ) = d(τ) × τ / Σ(j=1..τ) d(j) ──────────────
        // Torna o limiar independente da amplitude do sinal.
        cmnd[0] = 1f
        var runningSum = 0f
        for (tau in 1 until halfLen) {
            runningSum += diff[tau]
            cmnd[tau] = if (runningSum > 0f) diff[tau] * tau / runningSum else 1f
        }

        // ── Passo 3 — Primeiro vale abaixo do limiar, ajustado ao mínimo local ─
        var tauEstimate = -1
        var tau = tauMin
        while (tau < halfLen) {
            if (cmnd[tau] < AudioEngineConfig.YIN_CONFIDENCE_THRESHOLD) {
                while (tau + 1 < halfLen && cmnd[tau + 1] < cmnd[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate <= 0) return 0f

        // ── Passo 4 — Interpolação parabólica sobre d(τ) ─────────────────
        //
        // Vértice da parábola por (τ−1, s₀), (τ, s₁), (τ+1, s₂):
        //
        //   offset = (s₀ − s₂) / (2 × (s₀ − 2s₁ + s₂))
        //
        // Sobre a diferença bruta d(τ), localmente quadrática em torno do
        // período — a normalização cumulativa da CMND distorce a curvatura.
        //
        // Atenção ao sinal: com s₁ mínimo o denominador é positivo e o offset
        // aponta para o vizinho MENOR. Inverter o sinal leva a estimativa para
        // o lado errado e dobra o erro (vários cents, alternando frame a frame).
        var betterTau = tauEstimate.toFloat()
        if (tauEstimate < halfLen - 1) {
            val s0 = diff[tauEstimate - 1]
            val s1 = diff[tauEstimate]
            val s2 = diff[tauEstimate + 1]
            val denom = s0 - 2f * s1 + s2
            if (denom > 0f) {
                betterTau += ((s0 - s2) / (2f * denom)).coerceIn(-0.5f, 0.5f)
            }
        }

        confidence = (1f - cmnd[tauEstimate]).coerceIn(0f, 1f)
        return sampleRate / betterTau
    }

    companion object {
        /** Frequência máxima detectável (Hz). C8 do piano ≈ 4186 Hz. */
        private const val MAX_DETECTABLE_FREQUENCY_HZ = 4200f

        /**
         * Nível RMS mínimo do frame (≈ −56 dBFS). Abaixo disso é ruído de fundo —
         * qualquer instrumento tocado perto do telefone fica bem acima.
         */
        const val MIN_RMS = 0.0015f
    }
}
