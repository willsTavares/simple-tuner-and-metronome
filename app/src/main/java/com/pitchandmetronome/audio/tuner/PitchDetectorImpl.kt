package com.pitchandmetronome.audio.tuner

import com.pitchandmetronome.core.utils.FrequencyUtils
import com.pitchandmetronome.domain.model.tuner.PitchResult
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação JNI bridge do [IPitchDetector].
 *
 * Fluxo interno:
 * 1. [startDetection] abre um stream AAudio de entrada via Oboe no lado nativo
 * 2. O callback AAudio acumula amostras em um ring-buffer circular
 * 3. Uma thread worker nativa (não-RT) roda o YIN a cada frame cheio
 * 4. O YIN chama [onPitchDetected] via JNI com a frequência e confiança
 * 5. Este método mapeia para [PitchResult] e emite no [callbackFlow]
 *
 * A conversão Hz → [Note] usa [FrequencyUtils.frequencyToNote], que leva
 * em conta a frequência de referência A4 configurada em [TunerConfig].
 *
 * **Otimização de alocação:** [Note] e [PitchResult] só são alocados quando a
 * frequência muda mais do que [FREQUENCY_CHANGE_THRESHOLD] Hz em relação à
 * última emissão. Callbacks repetidos com pitch estável (~20–50×/s) são
 * descartados sem nenhuma alocação no heap.
 */
@Singleton
class PitchDetectorImpl @Inject constructor() : IPitchDetector {

    private var currentConfig: TunerConfig = TunerConfig()

    // Última frequência emitida; -1f indica estado inativo / sem detecção.
    // @Volatile porque é lida/escrita em threads diferentes (callback nativo
    // e possíveis chamadas de stopDetection).
    @Volatile private var lastEmittedFrequency = -1f

    init {
        System.loadLibrary("pitchandmetronome")
    }

    override val pitchFlow: Flow<PitchResult?> = callbackFlow {
        nativeSetPitchCallback { frequencyHz, confidence ->
            if (frequencyHz > 0f && FrequencyUtils.isInMusicalRange(frequencyHz)) {
                // Só aloca Note + PitchResult se a mudança for perceptível.
                // Abaixo do limiar, o resultado anterior ainda é válido para a UI.
                if (kotlin.math.abs(frequencyHz - lastEmittedFrequency) >= FREQUENCY_CHANGE_THRESHOLD) {
                    lastEmittedFrequency = frequencyHz
                    val note = FrequencyUtils.frequencyToNote(frequencyHz, currentConfig.referenceA4)
                    trySend(PitchResult(frequencyHz, note, confidence))
                }
            } else {
                if (lastEmittedFrequency != -1f) {
                    lastEmittedFrequency = -1f
                    trySend(null)
                }
            }
        }
        awaitClose { nativeClearPitchCallback() }
    }

    override suspend fun startDetection(config: TunerConfig) {
        currentConfig = config
        nativeStartDetection(
            sampleRate = config.sampleRate,
            bufferSize = config.bufferSize
        )
    }

    override suspend fun stopDetection() {
        lastEmittedFrequency = -1f
        nativeStopDetection()
    }

    override fun isActive(): Boolean = nativeIsActive()

    override fun release() = nativeRelease()

    // ── JNI declarations ────────────────────────────────────────────────────

    private external fun nativeStartDetection(sampleRate: Int, bufferSize: Int)
    private external fun nativeStopDetection()
    private external fun nativeIsActive(): Boolean
    private external fun nativeRelease()

    private external fun nativeSetPitchCallback(
        callback: (frequencyHz: Float, confidence: Float) -> Unit
    )

    private external fun nativeClearPitchCallback()

    companion object {
        // Mudança mínima em Hz para emitir um novo PitchResult e alocar Note + PitchResult.
        // 0.8 Hz ≈ 3 cents em torno de A4 — reduz flutter sem perder responsividade.
        private const val FREQUENCY_CHANGE_THRESHOLD = 0.8f
    }
}
