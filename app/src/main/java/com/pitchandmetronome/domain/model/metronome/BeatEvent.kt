package com.pitchandmetronome.domain.model.metronome

/**
 * Evento emitido pelo engine de áudio a cada beat.
 *
 * Produzido no thread de callback do AAudio e roteado até a UI via Flow.
 * É intencional que seja um data class simples — o engine nativo não conhece
 * nada de Android ou Compose.
 *
 * @param beatNumber Posição do beat dentro do compasso (começa em 1).
 * @param isAccent `true` apenas para o primeiro beat do compasso.
 * @param timestampNanos Timestamp em nanosegundos via `System.nanoTime()`.
 *   Útil para sincronizar animações com o tempo real do beat, compensando
 *   o atraso entre o callback de áudio e o próximo frame de UI.
 */
data class BeatEvent(
    val beatNumber: Int,
    val isAccent: Boolean,
    val timestampNanos: Long
)
