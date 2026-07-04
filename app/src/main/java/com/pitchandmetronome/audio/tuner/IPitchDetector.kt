package com.pitchandmetronome.audio.tuner

import com.pitchandmetronome.domain.model.tuner.PitchResult
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import kotlinx.coroutines.flow.Flow

/**
 * Contrato da engine de detecção de pitch.
 *
 * A implementação concreta ([AudioCaptureEngine]) captura áudio via
 * `AudioRecord` em uma thread dedicada e executa o algoritmo YIN em cada
 * frame de amostras capturado.
 *
 * **Contrato de threading:**
 * - [startDetection] e [stopDetection] são suspend functions — aguardam a
 *   abertura/fechamento do stream antes de retornar.
 * - [pitchFlow] emite resultados a partir da thread de captura, já processados
 *   pelo algoritmo YIN. Resultados `null` indicam silêncio ou baixa confiança.
 *
 * **Ciclo de vida:**
 * ```
 * startDetection(config) → [PitchResult? emitidos via pitchFlow] → stopDetection()
 *                                                                  ↘ release() [finaliza]
 * ```
 */
interface IPitchDetector {

    /**
     * Flow de resultados de detecção de pitch.
     * Emite `null` quando não há sinal suficiente (silêncio, ruído, baixa confiança).
     * Emite [PitchResult] quando uma frequência fundamental é detectada com confiança
     * acima de [AudioEngineConfig.YIN_CONFIDENCE_THRESHOLD].
     */
    val pitchFlow: Flow<PitchResult?>

    /**
     * Abre o stream de captura de áudio e inicia a análise YIN.
     *
     * @param config Parâmetros de configuração do detector.
     * @throws SecurityException se a permissão RECORD_AUDIO não estiver concedida.
     * @throws IllegalStateException se o detector já estiver ativo.
     */
    suspend fun startDetection(config: TunerConfig)

    /**
     * Para a captura de áudio e libera o stream.
     * O Flow [pitchFlow] para de emitir após este ponto.
     */
    suspend fun stopDetection()

    /**
     * Atualiza a configuração a quente, sem reiniciar a captura.
     *
     * Aplica-se aos parâmetros lidos por frame ([TunerConfig.referenceA4] e
     * [TunerConfig.precisionMode]). Mudanças de [TunerConfig.sampleRate] ou
     * [TunerConfig.bufferSize] exigem [stopDetection] + [startDetection].
     */
    fun updateConfig(config: TunerConfig)

    /** Retorna `true` se o stream de entrada estiver ativo. */
    fun isActive(): Boolean

    /**
     * Libera todos os recursos nativos permanentemente.
     * Deve ser chamado no `onCleared()` do ViewModel.
     */
    fun release()
}
