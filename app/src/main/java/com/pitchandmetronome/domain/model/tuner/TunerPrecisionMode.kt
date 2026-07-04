package com.pitchandmetronome.domain.model.tuner

/**
 * Modo de precisão do afinador — controla o trade-off entre resposta e estabilidade.
 *
 * Cada modo define os parâmetros de suavização e histerese usados pela engine
 * de detecção ([frequencyEmaAlpha], [emissionThresholdCents]) e pelo ViewModel
 * ([centsEmaAlpha], [noteConfirmationFrames]).
 *
 * @param frequencyEmaAlpha Peso da amostra nova na EMA de frequência da engine
 *   (0 < α ≤ 1). Valores maiores = resposta mais rápida, menos supressão de jitter.
 * @param centsEmaAlpha Peso da amostra nova na EMA de cents da agulha (UI).
 * @param noteConfirmationFrames Frames consecutivos com a mesma nota exigidos
 *   para confirmar uma troca de nota exibida (histérese no limite de semitom).
 * @param emissionThresholdCents Variação mínima em cents para a engine emitir um
 *   novo resultado. Em cents (não Hz) para que a sensibilidade seja uniforme em
 *   toda a faixa — um limiar em Hz fixo seria ~6× mais grosso no E2 do que no A4.
 */
enum class TunerPrecisionMode(
    val frequencyEmaAlpha: Float,
    val centsEmaAlpha: Float,
    val noteConfirmationFrames: Int,
    val emissionThresholdCents: Float
) {
    /** Máxima responsividade — mostra cada variação real do sinal. */
    PRECISE(
        frequencyEmaAlpha = 0.6f,
        centsEmaAlpha = 0.6f,
        noteConfirmationFrames = 1,
        emissionThresholdCents = 0.5f
    ),

    /** Equilíbrio entre resposta e estabilidade (padrão). */
    BALANCED(
        frequencyEmaAlpha = 0.35f,
        centsEmaAlpha = 0.45f,
        noteConfirmationFrames = 2,
        emissionThresholdCents = 1.0f
    ),

    /** Máxima estabilidade — agulha mais parada, resposta mais lenta. */
    STABLE(
        frequencyEmaAlpha = 0.2f,
        centsEmaAlpha = 0.25f,
        noteConfirmationFrames = 3,
        emissionThresholdCents = 2.0f
    );

    companion object {
        val DEFAULT = BALANCED

        /** Parse tolerante para valores persistidos — retorna [DEFAULT] se desconhecido. */
        fun fromName(name: String?): TunerPrecisionMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
