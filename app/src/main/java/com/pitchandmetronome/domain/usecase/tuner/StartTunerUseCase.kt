package com.pitchandmetronome.domain.usecase.tuner

import com.pitchandmetronome.audio.tuner.IPitchDetector
import com.pitchandmetronome.domain.repository.ITunerRepository
import javax.inject.Inject

/**
 * Inicia a captura de áudio e detecção de pitch.
 *
 * Pré-condição: a permissão RECORD_AUDIO deve ter sido concedida.
 * A verificação de permissão é responsabilidade da camada de UI/ViewModel —
 * este use case assume que a permissão está disponível.
 */
class StartTunerUseCase @Inject constructor(
    private val pitchDetector: IPitchDetector,
    private val repository: ITunerRepository
) {
    suspend operator fun invoke() {
        val config = repository.getConfig()
        pitchDetector.startDetection(config)
    }
}
