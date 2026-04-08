package com.pitchandmetronome.domain.model.tuner

/**
 * Resultado de uma iteração de detecção de pitch.
 *
 * Emitido pelo [IPitchDetector] a cada frame de análise bem-sucedido.
 * Resultado nulo indica silêncio ou sinal insuficiente.
 *
 * @param detectedFrequency Frequência fundamental detectada em Hz.
 * @param note Nota musical mais próxima, com desvio calculado em cents.
 * @param confidence Índice de confiança do algoritmo YIN (0.0–1.0).
 *   Valores abaixo de [AudioEngineConfig.YIN_CONFIDENCE_THRESHOLD] são descartados.
 */
data class PitchResult(
    val detectedFrequency: Float,
    val note: Note,
    val confidence: Float
)
