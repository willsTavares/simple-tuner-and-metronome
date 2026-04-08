package com.pitchandmetronome.audio.tuner

import com.pitchandmetronome.domain.model.tuner.PitchResult
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import kotlinx.coroutines.flow.Flow

/**
 * Contrato da engine de detecção de pitch.
 *
 * A implementação concreta ([PitchDetectorImpl]) delega para código C++ via JNI,
 * que gerencia um stream AAudio de entrada (microfone) através do Oboe e
 * executa o algoritmo YIN em cada frame de amostras capturado.
 *
 * **Contrato de threading:**
 * - [startDetection] e [stopDetection] são suspend functions — aguardam a
 *   abertura/fechamento do stream antes de retornar.
 * - [pitchFlow] emite resultados em `Dispatchers.Default`, já processados
 *   pelo algoritmo YIN. Resultados `null` indicam silêncio ou baixa confiança.
 * - O callback de entrada AAudio roda em thread RT; a análise YIN ocorre
 *   em um thread de trabalho separado para não bloquear o buffer de captura.
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

    /** Retorna `true` se o stream de entrada estiver ativo. */
    fun isActive(): Boolean

    /**
     * Libera todos os recursos nativos permanentemente.
     * Deve ser chamado no `onCleared()` do ViewModel.
     */
    fun release()
}
