package com.pitchandmetronome.metronome

import com.pitchandmetronome.domain.model.metronome.BeatEvent
import com.pitchandmetronome.domain.model.metronome.SoundProfile
import com.pitchandmetronome.domain.model.metronome.TimeSignature

/**
 * Estado imutável da UI do metrônomo.
 *
 * Produzido exclusivamente pelo [MetronomeViewModel] e consumido pelas
 * Composables. A UI nunca modifica este objeto — ela apenas emite eventos
 * (funções `on*` no ViewModel) que levam a um novo estado.
 *
 * @param isPlaying Metrônomo em reprodução.
 * @param bpm Batidas por minuto atualmente configuradas.
 * @param timeSignature Fórmula de compasso selecionada.
 * @param soundProfile Perfil sonoro do click.
 * @param accentFirstBeat Se o primeiro beat recebe acento visual e sonoro.
 * @param lastBeatEvent Último evento de beat, para acionar animações. Nullable
 *   pois antes do primeiro beat nenhum evento foi emitido.
 * @param isLoading `true` durante o start/stop do engine (operação assíncrona).
 * @param errorMessage Mensagem de erro não-crítico para exibir como Snackbar.
 */
data class MetronomeUiState(
    val isPlaying: Boolean = false,
    val bpm: Int = 120,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val soundProfile: SoundProfile = SoundProfile.CLICK,
    val accentFirstBeat: Boolean = true,
    val lastBeatEvent: BeatEvent? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
