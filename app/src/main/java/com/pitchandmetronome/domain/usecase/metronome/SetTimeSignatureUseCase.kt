package com.pitchandmetronome.domain.usecase.metronome

import com.pitchandmetronome.audio.metronome.IMetronomeEngine
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import javax.inject.Inject

/**
 * Altera a fórmula de compasso.
 *
 * Se o metrônomo estiver tocando, o engine precisa reiniciar o contador de
 * beats para evitar que o novo compasso comece na posição errada.
 * Isso é feito via [IMetronomeEngine.updateTimeSignature].
 */
class SetTimeSignatureUseCase @Inject constructor(
    private val engine: IMetronomeEngine,
    private val repository: IMetronomeRepository
) {
    suspend operator fun invoke(timeSignature: TimeSignature) {
        repository.updateTimeSignature(timeSignature)
        if (engine.isPlaying()) {
            engine.updateTimeSignature(timeSignature)
        }
    }
}
