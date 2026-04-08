package com.pitchandmetronome.audio.metronome

import com.pitchandmetronome.domain.model.metronome.BeatConfig
import com.pitchandmetronome.domain.model.metronome.BeatEvent
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import kotlinx.coroutines.flow.Flow

/**
 * Contrato da engine de áudio do metrônomo.
 *
 * A implementação concreta ([MetronomeEngineImpl]) delega para código C++ via JNI,
 * que gerencia um stream AAudio de saída através da biblioteca Oboe.
 *
 * **Contrato de threading:**
 * - [start], [stop], [updateBpm], [updateTimeSignature] e [release] podem ser
 *   chamados de qualquer thread (são thread-safe).
 * - [beatFlow] emite no dispatcher especificado em [callbackFlow] do JNI bridge —
 *   normalmente `Dispatchers.Default`.
 * - O callback nativo do AAudio roda em um thread de alta prioridade do sistema;
 *   nunca chame código Kotlin/JVM diretamente de lá (use PostEventQueue).
 *
 * **Ciclo de vida:**
 * ```
 * start(config) → [beats emitidos via beatFlow] → stop() → [pode ser reiniciado]
 *                                                         ↘ release() [finaliza]
 * ```
 */
interface IMetronomeEngine {

    /**
     * Flow de beats gerados pelo engine nativo.
     * Emite um [BeatEvent] a cada beat com precisão de sample.
     * O Flow é frio — apenas começa a emitir após [start].
     */
    val beatFlow: Flow<BeatEvent>

    /**
     * Inicializa e abre o stream AAudio, começando a gerar beats.
     *
     * @param config Parâmetros imutáveis para este ciclo de reprodução.
     * @throws IllegalStateException se o engine já estiver tocando.
     */
    suspend fun start(config: BeatConfig)

    /**
     * Para o stream de áudio. O Flow [beatFlow] para de emitir.
     * Os recursos do stream são liberados, mas o engine pode ser reiniciado.
     */
    suspend fun stop()

    /**
     * Atualiza o BPM em tempo real sem interromper o stream.
     * Apenas efetivo se [isPlaying] retornar `true`.
     *
     * @param bpm Novo valor de BPM. Deve estar no intervalo válido.
     */
    fun updateBpm(bpm: Int)

    /**
     * Atualiza a fórmula de compasso em tempo real.
     * Reinicia o contador de beats para o início do compasso.
     */
    fun updateTimeSignature(timeSignature: TimeSignature)

    /** Retorna `true` se o stream de áudio estiver ativo. */
    fun isPlaying(): Boolean

    /**
     * Libera todos os recursos nativos permanentemente.
     * Deve ser chamado no `onCleared()` do ViewModel ou no `onDestroy()`.
     * Após [release], o objeto não deve ser reutilizado.
     */
    fun release()
}
