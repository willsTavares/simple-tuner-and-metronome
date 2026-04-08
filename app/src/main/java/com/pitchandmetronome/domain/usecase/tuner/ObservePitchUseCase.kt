package com.pitchandmetronome.domain.usecase.tuner

import com.pitchandmetronome.audio.tuner.IPitchDetector
import com.pitchandmetronome.domain.model.tuner.PitchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Expõe o [Flow] de resultados de detecção de pitch.
 *
 * Emite `null` quando não há sinal suficiente ou o detector está inativo.
 * O ViewModel filtra os nulos para exibir o último resultado válido na UI.
 */
class ObservePitchUseCase @Inject constructor(
    private val pitchDetector: IPitchDetector
) {
    operator fun invoke(): Flow<PitchResult?> = pitchDetector.pitchFlow
}
