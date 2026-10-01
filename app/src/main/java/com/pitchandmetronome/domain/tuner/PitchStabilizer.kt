package com.pitchandmetronome.domain.tuner

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * Transforma a sequência bruta de estimativas do YIN (uma por frame) em uma
 * frequência estável para exibição — sem parâmetro de usuário: a suavização
 * se adapta sozinha ao que o sinal está fazendo.
 *
 * Pipeline por frame (tudo em cents relativos a 440 Hz, escala onde "1 cent"
 * tem o mesmo peso em qualquer oitava):
 *
 * 1. **Mediana das últimas [MEDIAN_SIZE] leituras** — descarta outliers
 *    isolados (erro de oitava pontual, ruído de ataque) sem atrasar a resposta
 *    como uma média faria.
 * 2. **Confirmação de salto** — uma leitura a mais de [JUMP_CENTS] do valor
 *    atual só é aceita depois de [JUMP_CONFIRM_FRAMES] leituras consecutivas
 *    coerentes entre si. Troca de nota real passa em ~130 ms; erro de oitava
 *    de um frame nunca aparece.
 * 3. **EMA adaptativa** — α cresce com a distância entre a mediana e o valor
 *    exibido: parado na nota → α baixo (agulha firme); girando a tarraxa → α
 *    alto (acompanha sem atraso). É o que dispensa um seletor de "modo".
 * 4. **Hold de silêncio** — até [holdFrames] frames sem pitch mantêm o último
 *    valor (decaimento da corda, respiração) em vez de piscar "sem sinal".
 *
 * Classe pura, sem Android — testável em JVM. **Não é thread-safe**: use uma
 * instância por thread de captura.
 *
 * @param holdFrames Frames sem pitch tolerados antes de reportar silêncio.
 */
class PitchStabilizer(private val holdFrames: Int) {

    private val window = FloatArray(MEDIAN_SIZE)
    private var windowCount = 0
    private var windowNext = 0
    private val sortScratch = FloatArray(MEDIAN_SIZE)

    private val jumpCandidates = FloatArray(JUMP_CONFIRM_FRAMES)
    private var jumpCount = 0

    private var smoothedCents = Float.NaN
    private var unvoicedRun = 0

    fun reset() {
        windowCount = 0
        windowNext = 0
        jumpCount = 0
        smoothedCents = Float.NaN
        unvoicedRun = 0
    }

    /**
     * @param rawHz Estimativa bruta do frame; ≤ 0 indica frame sem pitch.
     * @return Frequência estável em Hz, ou [UNVOICED] se não há o que exibir.
     */
    fun process(rawHz: Float): Float {
        if (rawHz <= 0f) {
            unvoicedRun++
            if (unvoicedRun > holdFrames) {
                reset()
                return UNVOICED
            }
            return if (smoothedCents.isNaN()) UNVOICED else toHz(smoothedCents)
        }
        unvoicedRun = 0
        val cents = toCents(rawHz)

        // Aquisição: só exibe depois de leituras suficientes para uma mediana.
        if (smoothedCents.isNaN()) {
            push(cents)
            if (windowCount < ACQUIRE_FRAMES) return UNVOICED
            smoothedCents = median()
            return toHz(smoothedCents)
        }

        if (abs(cents - smoothedCents) > JUMP_CENTS) {
            // Candidatos precisam concordar entre si; um candidato discordante
            // reinicia a contagem (ruído não "acumula" até virar salto).
            if (jumpCount > 0 && abs(cents - jumpCandidates[jumpCount - 1]) > JUMP_CENTS) {
                jumpCount = 0
            }
            jumpCandidates[jumpCount++] = cents
            if (jumpCount < JUMP_CONFIRM_FRAMES) return toHz(smoothedCents)

            // Salto confirmado: recomeça a janela a partir dos candidatos.
            windowCount = 0
            windowNext = 0
            for (i in 0 until jumpCount) push(jumpCandidates[i])
            jumpCount = 0
            smoothedCents = median()
            return toHz(smoothedCents)
        }

        jumpCount = 0
        push(cents)
        val diff = median() - smoothedCents
        val alpha = (ALPHA_MIN + abs(diff) * ALPHA_PER_CENT).coerceAtMost(ALPHA_MAX)
        smoothedCents += alpha * diff
        return toHz(smoothedCents)
    }

    private fun push(cents: Float) {
        window[windowNext] = cents
        windowNext = (windowNext + 1) % MEDIAN_SIZE
        if (windowCount < MEDIAN_SIZE) windowCount++
    }

    /** Mediana das leituras na janela — insertion sort em ≤ 5 elementos, zero alocação. */
    private fun median(): Float {
        val n = windowCount
        for (i in 0 until n) {
            val v = window[i]
            var j = i - 1
            while (j >= 0 && sortScratch[j] > v) {
                sortScratch[j + 1] = sortScratch[j]
                j--
            }
            sortScratch[j + 1] = v
        }
        return if (n % 2 == 1) sortScratch[n / 2]
        else (sortScratch[n / 2 - 1] + sortScratch[n / 2]) * 0.5f
    }

    private fun toCents(hz: Float): Float = (1200.0 * log2(hz / REFERENCE_HZ)).toFloat()

    private fun toHz(cents: Float): Float = (REFERENCE_HZ * 2.0.pow(cents / 1200.0)).toFloat()

    companion object {
        /** Retorno de [process] quando não há pitch para exibir. */
        const val UNVOICED = -1f

        /** Leituras na mediana (~215 ms a 23 frames/s). */
        private const val MEDIAN_SIZE = 5

        /** Leituras mínimas antes da primeira exibição (~130 ms). */
        private const val ACQUIRE_FRAMES = 3

        /** Distância (cents) a partir da qual uma leitura é tratada como possível salto. */
        private const val JUMP_CENTS = 50f

        /** Leituras coerentes exigidas para aceitar um salto. */
        private const val JUMP_CONFIRM_FRAMES = 3

        // α = ALPHA_MIN + |Δ| × ALPHA_PER_CENT, limitado a ALPHA_MAX.
        // Δ 0,5 cent → α ≈ 0,19 (firme); Δ 2 → 0,31; Δ ≥ 8 → 0,8 (segue rápido).
        private const val ALPHA_MIN = 0.15f
        private const val ALPHA_PER_CENT = 0.08f
        private const val ALPHA_MAX = 0.8f

        /** Referência arbitrária da escala interna de cents (não depende do A4 do usuário). */
        private const val REFERENCE_HZ = 440.0
    }
}
