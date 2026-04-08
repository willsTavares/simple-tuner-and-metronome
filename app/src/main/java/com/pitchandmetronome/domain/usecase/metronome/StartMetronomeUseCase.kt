package com.pitchandmetronome.domain.usecase.metronome

import com.pitchandmetronome.audio.metronome.IMetronomeEngine
import com.pitchandmetronome.domain.model.metronome.BeatConfig
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import javax.inject.Inject

/**
 * Inicia o metrônomo com a configuração atual.
 *
 * Orquestra o engine de áudio e o repositório:
 * 1. Recupera o estado atual do repositório para construir a [BeatConfig]
 * 2. Abre o stream AAudio via [IMetronomeEngine.start]
 * 3. Atualiza o repositório para refletir o estado `isPlaying = true`
 *
 * **Threading:** deve ser chamado em uma coroutine de background.
 * O engine de áudio é iniciado de forma síncrona; o callback do AAudio
 * passará a ser chamado no thread de áudio de alta prioridade.
 */
class StartMetronomeUseCase @Inject constructor(
    private val engine: IMetronomeEngine,
    private val repository: IMetronomeRepository
) {
    suspend operator fun invoke() {
        val state = repository.getState()
        engine.start(state.toBeatConfig())
        repository.setPlaying(isPlaying = true)
    }

    suspend operator fun invoke(config: BeatConfig) {
        engine.start(config)
        repository.setPlaying(isPlaying = true)
    }
}
