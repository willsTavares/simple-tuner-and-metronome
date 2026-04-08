package com.pitchandmetronome.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchandmetronome.audio.tuner.IPitchDetector
import com.pitchandmetronome.core.utils.CoroutineDispatchers
import com.pitchandmetronome.domain.usecase.tuner.ObservePitchUseCase
import com.pitchandmetronome.domain.usecase.tuner.StartTunerUseCase
import com.pitchandmetronome.domain.usecase.tuner.StopTunerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel da feature de Afinador.
 *
 * Responsabilidades:
 * - Verificar e reagir ao estado da permissão RECORD_AUDIO
 * - Iniciar/parar o detector via use cases
 * - Transformar [PitchResult] em campos legíveis do [TunerUiState]
 * - Liberar recursos do detector ao ser destruído
 *
 * **Fluxo de permissão:**
 * A UI (Composable) gerencia a UI de permissão via Accompanist.
 * Quando a permissão é concedida, chama [onPermissionGranted].
 * O ViewModel nunca solicita permissão diretamente — apenas reage ao resultado.
 */
@HiltViewModel
class TunerViewModel @Inject constructor(
    private val startTuner: StartTunerUseCase,
    private val stopTuner: StopTunerUseCase,
    private val observePitch: ObservePitchUseCase,
    private val pitchDetector: IPitchDetector,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    init {
        // Traduz PitchResult → campos da UI
        observePitch()
            .onEach { result ->
                if (result != null) {
                    _uiState.update { ui ->
                        ui.copy(
                            detectedNote = result.note.fullName,
                            detectedFrequency = result.detectedFrequency,
                            centsDeviation = result.note.centsDeviation,
                            confidence = result.confidence,
                            micLevel = result.confidence.coerceIn(0f, 1f)
                        )
                    }
                } else {
                    // Silêncio ou baixa confiança — mantém a última nota visível
                    // mas zera a confiança para indicar instabilidade
                    _uiState.update { it.copy(confidence = 0f, micLevel = 0f) }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Chamado pela UI quando a permissão RECORD_AUDIO é concedida. */
    fun onPermissionGranted() {
        _uiState.update { it.copy(hasAudioPermission = true) }
    }

    /** Chamado pela UI quando a permissão RECORD_AUDIO é negada. */
    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                hasAudioPermission = false,
                errorMessage = "Permissão de microfone necessária para o afinador"
            )
        }
    }

    fun onStartTuner() {
        viewModelScope.launch(dispatchers.default) {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { startTuner() }
                .onSuccess { _uiState.update { it.copy(isListening = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onStopTuner() {
        viewModelScope.launch(dispatchers.default) {
            runCatching { stopTuner() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            detectedNote = "--",
                            detectedFrequency = 0f,
                            centsDeviation = 0f,
                            confidence = 0f
                        )
                    }
                }
        }
    }

    fun onReferenceA4Change(frequency: Float) {
        _uiState.update { it.copy(referenceA4 = frequency) }
        // Implementar: persistir via SaveTunerConfigUseCase
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pitchDetector.release()
    }
}
