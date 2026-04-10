package com.pitchandmetronome.domain.model.tuner

/**
 * Nota musical com suas propriedades de referência e desvio medido.
 *
 * @param name Nome da nota em notação ocidental (ex: "A", "C#", "Bb").
 * @param octave Oitava científica (A4 = oitava 4, C4 = Middle C).
 * @param referenceFrequency Frequência exata de referência desta nota (Hz).
 * @param centsDeviation Desvio em cents em relação à nota de referência.
 *   Faixa: -50 (meio semitom abaixo) a +50 (meio semitom acima).
 *   Zero indica afinação perfeita.
 */
data class Note(
    val name: String,
    val octave: Int,
    val referenceFrequency: Float,
    val centsDeviation: Float
) {
    /** Representação completa: "A4", "C#3", etc. Pré-computado para evitar concatenação no hot path. */
    val fullName: String = "$name$octave"
}
