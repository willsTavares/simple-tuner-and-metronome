package com.pitchandmetronome.domain.model.metronome

/**
 * Fórmula de compasso editável.
 *
 * @param beatsPerMeasure Número de beats por compasso (numerador). Faixa: 1..32.
 * @param beatUnit Unidade de tempo (denominador): valores válidos: 1, 2, 4, 8, 16.
 */
data class TimeSignature(
    val beatsPerMeasure: Int,
    val beatUnit: Int
) {
    init {
        require(beatsPerMeasure in BEATS_RANGE) {
            "beatsPerMeasure deve estar entre ${BEATS_RANGE.first} e ${BEATS_RANGE.last}"
        }
        require(beatUnit in VALID_BEAT_UNITS) {
            "beatUnit deve ser um de: $VALID_BEAT_UNITS"
        }
    }

    /** Representação textual legível para a UI (ex: "4/4", "6/8"). */
    val displayName: String get() = "$beatsPerMeasure/$beatUnit"

    override fun toString(): String = displayName

    companion object {
        /** Faixa válida para o numerador. */
        val BEATS_RANGE = 1..32

        /** Denominadores válidos (figuras musicais padrão). */
        val VALID_BEAT_UNITS = listOf(1, 2, 4, 8, 16)

        /** Presets comuns para acesso rápido na UI. */
        val COMMON_PRESETS = listOf(
            TimeSignature(2, 4),
            TimeSignature(3, 4),
            TimeSignature(4, 4),
            TimeSignature(5, 4),
            TimeSignature(6, 8),
            TimeSignature(7, 8)
        )

        val FOUR_FOUR = TimeSignature(4, 4)

        /**
         * Deserializa a partir de uma string "numerador/denominador".
         * Retorna [FOUR_FOUR] se o formato for inválido.
         */
        fun fromString(value: String): TimeSignature {
            return runCatching {
                val parts = value.split("/")
                TimeSignature(parts[0].trim().toInt(), parts[1].trim().toInt())
            }.getOrDefault(FOUR_FOUR)
        }
    }
}
