package com.pitchandmetronome.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchandmetronome.audio.tuner.IPitchDetector
import com.pitchandmetronome.core.utils.CoroutineDispatchers
import com.pitchandmetronome.domain.model.tuner.Note
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import com.pitchandmetronome.domain.repository.ITunerRepository
import com.pitchandmetronome.domain.tuner.TuningCalculator
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.math.abs

/**
 * ViewModel da feature de Afinador.
 *
 * Responsabilidades:
 * - Verificar e reagir ao estado da permissão RECORD_AUDIO
 * - Iniciar/parar o detector via use cases
 * - Transformar [PitchResult] em campos legíveis do [TunerUiState]
 * - Liberar recursos do detector ao ser destruído
 *
 * A frequência já chega estabilizada pela engine (ver
 * [com.pitchandmetronome.domain.tuner.PitchStabilizer]); aqui só se decide
 * qual nota exibir e se ela está afinada.
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
    private val tunerRepository: ITunerRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    // Config atual, carregada do DataStore no init e mantida em memória para
    // persistir mudanças sem reler as preferências.
    @Volatile private var currentConfig = TunerConfig()

    // Nota exibida. Histerese por banda: só troca quando a frequência passa
    // NOTE_SWITCH_CENTS da nota atual — perto da fronteira de semitom (±50) o
    // nome não pisca, e os cents nunca são medidos contra uma nota distante.
    @Volatile private var displayedNote: Note? = null

    @Volatile private var inTune = false

    // Start/stop chegam em rajada pelo ciclo de vida (primeiro plano, troca de
    // aba); o mutex garante que nunca rodam intercalados.
    private val startStopMutex = Mutex()

    init {
        viewModelScope.launch(dispatchers.default) {
            val config = tunerRepository.getConfig()
            currentConfig = config
            _uiState.update { it.copy(referenceA4 = config.referenceA4) }
        }

        observePitch()
            .onEach { result ->
                if (result == null) {
                    inTune = false
                    _uiState.update { it.copy(hasSignal = false, isInTune = false) }
                    return@onEach
                }

                val frequency = result.detectedFrequency
                var note = displayedNote
                var cents = note?.let {
                    TuningCalculator.centsFromReference(frequency, it.referenceFrequency)
                }
                if (note == null || cents == null || abs(cents) > NOTE_SWITCH_CENTS) {
                    note = result.note
                    cents = note.centsDeviation
                    displayedNote = note
                }

                inTune = abs(cents) <= if (inTune) {
                    TuningCalculator.IN_TUNE_EXIT_CENTS
                } else {
                    TuningCalculator.IN_TUNE_ENTER_CENTS
                }

                _uiState.update {
                    it.copy(
                        hasSignal = true,
                        detectedNote = note.fullName,
                        detectedFrequency = frequency,
                        centsDeviation = cents.coerceIn(-50f, 50f),
                        isInTune = inTune
                    )
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
        _uiState.update { it.copy(hasAudioPermission = false) }
    }

    /** Idempotente — pode ser chamado a cada volta ao primeiro plano. */
    fun onStartTuner() {
        viewModelScope.launch(dispatchers.default) {
            startStopMutex.withLock {
                if (pitchDetector.isActive()) return@withLock
                _uiState.update { it.copy(isLoading = true) }
                try {
                    startTuner()
                    _uiState.update { it.copy(isListening = true, errorMessage = null) }
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(errorMessage = "Não foi possível acessar o microfone. " +
                            "Feche outros apps que estejam usando o microfone e tente novamente.")
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Idempotente — pode ser chamado mesmo com o afinador parado. */
    fun onStopTuner() {
        viewModelScope.launch(dispatchers.default) {
            startStopMutex.withLock {
                if (!pitchDetector.isActive() && !_uiState.value.isListening) return@withLock
                stopAndReset()
            }
        }
    }

    private suspend fun stopAndReset() {
        try {
            stopTuner()
        } catch (_: Exception) { /* stop é idempotente — ignora erros silenciosamente */ }
        displayedNote = null
        inTune = false
        _uiState.update {
            it.copy(
                isListening = false,
                hasSignal = false,
                detectedNote = "--",
                detectedFrequency = 0f,
                centsDeviation = 0f,
                isInTune = false
            )
        }
    }

    /**
     * Ajusta a frequência de referência A4: aplica a quente no detector
     * (vale a partir do próximo frame) e persiste no DataStore.
     */
    fun onReferenceA4Change(frequency: Float) {
        // A frequência de referência da nota exibida muda com o A4.
        displayedNote = null
        _uiState.update { it.copy(referenceA4 = frequency) }
        val newConfig = currentConfig.copy(referenceA4 = frequency)
        currentConfig = newConfig
        pitchDetector.updateConfig(newConfig)
        viewModelScope.launch(dispatchers.default) {
            try {
                tunerRepository.saveConfig(newConfig)
            } catch (_: Exception) { /* falha de persistência não afeta a sessão atual */ }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pitchDetector.release()
    }

    companion object {
        // Distância (cents) da nota exibida a partir da qual ela é trocada pela
        // mais próxima. 10 cents além da fronteira de semitom.
        private const val NOTE_SWITCH_CENTS = 60f
    }
}
