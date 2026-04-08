package com.pitchandmetronome.domain.usecase.tuner

import com.pitchandmetronome.audio.tuner.IPitchDetector
import javax.inject.Inject

/**
 * Para a captura de áudio e libera os recursos do detector de pitch.
 */
class StopTunerUseCase @Inject constructor(
    private val pitchDetector: IPitchDetector
) {
    suspend operator fun invoke() {
        pitchDetector.stopDetection()
    }
}
