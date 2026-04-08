package com.pitchandmetronome.domain.model.metronome

/**
 * Estado completo do metrônomo, mantido no repositório e observado pela UI.
 *
 * Representa o estado "persistível" — diferente de [BeatEvent], que é efêmero.
 * Quando o app vai para background e volta, o repositório restaura este estado
 * (exceto [isPlaying], que sempre inicia como `false`).
 */
data class MetronomeState(
    val isPlaying: Boolean = false,
    val bpm: Int = 120,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val soundProfile: SoundProfile = SoundProfile.CLICK,
    val accentFirstBeat: Boolean = true
) {
    /** Converte o estado atual em uma [BeatConfig] para passar ao engine. */
    fun toBeatConfig(): BeatConfig = BeatConfig(
        bpm = bpm,
        timeSignature = timeSignature,
        soundProfile = soundProfile,
        accentFirstBeat = accentFirstBeat
    )
}
