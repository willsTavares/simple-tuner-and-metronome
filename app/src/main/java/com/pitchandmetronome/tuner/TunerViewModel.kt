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

    // ── Campos de deduplicação — evitam copy() quando nada mudou ─────────
    // Comparações primitivas (Float/String) são muito mais baratas do que
    // alocar um novo TunerUiState via copy() a cada frame (~12×/s).
    @Volatile private var lastNote: String = "--"
    @Volatile private var lastCents: Float = 0f
    @Volatile private var lastConfidence: Float = 0f
    @Volatile private var silenceEmitted: Boolean = true

    init {
        // Traduz PitchResult → campos da UI.
        // Otimização: só emite novo estado quando os valores mudam de forma
        // perceptível para a UI. Thresholds:
        //   - nota: mudança de nome (String identity via ===, fullName é pré-computado)
        //   - cents: mudança > 0.5 cent (imperceptível abaixo disso)
        //   - confiança: mudança > 0.02 (2% — invisível na UI)
        observePitch()
            .onEach { result ->
                if (result != null) {
                    silenceEmitted = false
                    val noteName = result.note.fullName
                    val cents = result.note.centsDeviation
                    val conf = result.confidence

                    // Deduplica: só aloca novo TunerUiState se algo mudou visivelmente
                    val noteChanged = noteName !== lastNote
                    val centsChanged = kotlin.math.abs(cents - lastCents) > CENTS_CHANGE_THRESHOLD
                    val confChanged = kotlin.math.abs(conf - lastConfidence) > CONFIDENCE_CHANGE_THRESHOLD

                    if (noteChanged || centsChanged || confChanged) {
                        lastNote = noteName
                        lastCents = cents
                        lastConfidence = conf
                        _uiState.update { ui ->
                            ui.copy(
                                detectedNote = noteName,
                                detectedFrequency = result.detectedFrequency,
                                centsDeviation = cents,
                                confidence = conf,
                                micLevel = conf.coerceIn(0f, 1f)
                            )
                        }
                    }
                } else {
                    // Silêncio ou baixa confiança — emite apenas na transição
                    // (evita spam de copy() a cada frame sem sinal).
                    if (!silenceEmitted) {
                        silenceEmitted = true
                        lastConfidence = 0f
                        _uiState.update { it.copy(confidence = 0f, micLevel = 0f) }
                    }
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
            try {
                startTuner()
                _uiState.update { it.copy(isListening = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onStopTuner() {
        viewModelScope.launch(dispatchers.default) {
            try {
                stopTuner()
                silenceEmitted = true
                lastNote = "--"
                lastCents = 0f
                lastConfidence = 0f
                _uiState.update {
                    it.copy(
                        isListening = false,
                        detectedNote = "--",
                        detectedFrequency = 0f,
                        centsDeviation = 0f,
                        confidence = 0f
                    )
                }
            } catch (_: Exception) { /* stop é idempotente — ignora erros silenciosamente */ }
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

    companion object {
        // Mudança mínima em cents para emitir novo estado (1 cent — variação sutil mas real).
        private const val CENTS_CHANGE_THRESHOLD = 1.0f
        // Mudança mínima de confiança para emitir novo estado (5%).
        private const val CONFIDENCE_CHANGE_THRESHOLD = 0.05f
    }
}
