package com.pitchandmetronome.domain.usecase.metronome

import com.pitchandmetronome.audio.metronome.IMetronomeEngine
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import javax.inject.Inject

/**
 * Para o metrônomo e libera o stream de áudio.
 *
 * Chama [IMetronomeEngine.stop], que encerra o callback AAudio,
 * e atualiza o repositório para `isPlaying = false`.
 */
class StopMetronomeUseCase @Inject constructor(
    private val engine: IMetronomeEngine,
    private val repository: IMetronomeRepository
) {
    suspend operator fun invoke() {
        engine.stop()
        repository.setPlaying(isPlaying = false)
    }
}
