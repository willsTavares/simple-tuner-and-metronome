package com.pitchandmetronome.domain.model.metronome

import com.pitchandmetronome.core.audio.AudioEngineConfig

/**
 * Configuração imutável de um ciclo de metrônomo.
 *
 * Passada para [IMetronomeEngine.start] a cada vez que o usuário inicia
 * ou altera os parâmetros. Imutável por design — mudanças geram um novo
 * objeto, garantindo thread-safety no repasse ao engine nativo.
 *
 * @param bpm Batidas por minuto. Faixa: [AudioEngineConfig.METRONOME_BPM_MIN]..[AudioEngineConfig.METRONOME_BPM_MAX].
 * @param timeSignature Fórmula de compasso (ex: 4/4, 3/4).
 * @param soundProfile Perfil sonoro do click (ex: CLICK, BEEP, WOOD).
 * @param accentFirstBeat Se `true`, o primeiro beat de cada compasso tem volume maior.
 */
data class BeatConfig(
    val bpm: Int = 120,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val soundProfile: SoundProfile = SoundProfile.CLICK,
    val accentFirstBeat: Boolean = true
) {
    init {
        require(bpm in AudioEngineConfig.METRONOME_BPM_MIN..AudioEngineConfig.METRONOME_BPM_MAX) {
            "BPM deve estar entre ${AudioEngineConfig.METRONOME_BPM_MIN} e ${AudioEngineConfig.METRONOME_BPM_MAX}"
        }
    }

    /** Intervalo entre beats em milissegundos. */
    val intervalMs: Long get() = 60_000L / bpm

    /** Intervalo entre beats em nanosegundos (usado pelo engine de precisão). */
    val intervalNanos: Long get() = 60_000_000_000L / bpm
}
