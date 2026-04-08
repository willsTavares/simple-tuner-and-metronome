package com.pitchandmetronome.domain.usecase.metronome

import com.pitchandmetronome.audio.metronome.IMetronomeEngine
import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import javax.inject.Inject

/**
 * Altera o BPM do metrônomo, em tempo real se estiver tocando.
 *
 * Válida a faixa de BPM antes de qualquer operação.
 * Se o engine estiver ativo, [IMetronomeEngine.updateBpm] recalcula o intervalo
 * entre beats sem interromper o stream de áudio.
 */
class SetBpmUseCase @Inject constructor(
    private val engine: IMetronomeEngine,
    private val repository: IMetronomeRepository
) {
    suspend operator fun invoke(bpm: Int) {
        require(bpm in AudioEngineConfig.METRONOME_BPM_MIN..AudioEngineConfig.METRONOME_BPM_MAX) {
            "BPM fora do intervalo suportado: $bpm"
        }
        repository.updateBpm(bpm)
        if (engine.isPlaying()) {
            engine.updateBpm(bpm)
        }
    }
}
