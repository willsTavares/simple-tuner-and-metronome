package com.pitchandmetronome.domain.model.tuner

/**
 * Estado do afinador modelado como sealed class.
 *
 * O ViewModel mapeia esses estados para [TunerUiState], separando lógica
 * de apresentação da lógica de domínio.
 */
sealed class TunerState {
    /** Afinador parado — nenhum recurso de áudio alocado. */
    data object Idle : TunerState()

    /** Stream de captura aberto, aguardando sinal suficiente. */
    data object Listening : TunerState()

    /** Pitch detectado com confiança acima do limiar. */
    data class Detecting(val result: PitchResult) : TunerState()

    /**
     * Erro irrecuperável (ex: permissão negada, hardware indisponível).
     * O ViewModel deve traduzir isso em uma mensagem legível para o usuário.
     */
    data class Error(val cause: Throwable) : TunerState()
}
