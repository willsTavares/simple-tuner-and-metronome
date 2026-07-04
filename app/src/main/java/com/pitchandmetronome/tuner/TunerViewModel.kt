package com.pitchandmetronome.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchandmetronome.audio.tuner.IPitchDetector
import com.pitchandmetronome.core.utils.CoroutineDispatchers
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import com.pitchandmetronome.domain.model.tuner.TunerPrecisionMode
import com.pitchandmetronome.domain.repository.ITunerRepository
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
    private val tunerRepository: ITunerRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    // Config atual (A4 + modo de precisão), carregada do DataStore no init e
    // mantida em memória para persistir mudanças sem reler as preferências.
    @Volatile private var currentConfig = TunerConfig()

    // ── Campos de deduplicação — evitam copy() quando nada mudou ─────────
    // Comparações primitivas (Float/String) são muito mais baratas do que
    // alocar um novo TunerUiState via copy() a cada frame (~12×/s).
    @Volatile private var lastNote: String = "--"
    @Volatile private var lastCents: Float = 0f
    @Volatile private var lastConfidence: Float = 0f
    @Volatile private var silenceEmitted: Boolean = true

    // ── Campos de estabilização de nota ───────────────────────────────────
    // Implementa histérese: a nota exibida só muda após N frames consecutivos
    // com a mesma nota detectada, evitando oscilação no limite de semitom.
    @Volatile private var pendingNote: String = "--"
    @Volatile private var pendingNoteCount: Int = 0

    // Última nota REALMENTE detectada (sem histerese) — usada só para saber
    // quando resetar a EMA de cents. Ver comentário em [rawCents] abaixo.
    @Volatile private var lastRawNoteName: String = "--"

    // EMA aplicada ao valor de cents para suavizar a agulha.
    @Volatile private var smoothedCents: Float = 0f
    @Volatile private var centsInitialized: Boolean = false

    init {
        // Carrega a config persistida (A4 + modo de precisão) para o uiState
        // refletir os valores salvos ao abrir a tela.
        viewModelScope.launch(dispatchers.default) {
            val config = tunerRepository.getConfig()
            currentConfig = config
            _uiState.update {
                it.copy(
                    referenceA4 = config.referenceA4,
                    precisionMode = config.precisionMode
                )
            }
        }

        // Traduz PitchResult → campos da UI com estabilização de nota e suavização.
        //
        // Estabilização (note confirmation):
        //   O NOME exibido só muda após N frames consecutivos com a mesma nota
        //   detectada (N vem do modo de precisão) — elimina o texto piscando no
        //   limite de semitom. Afeta só o texto, não o cálculo de cents (ver abaixo).
        //
        // Suavização de cents (EMA):
        //   Os cents usam sempre a nota mais próxima da frequência detectada
        //   ([Note.centsDeviation], já limitado a (-50, +50]) — nunca a nota
        //   "confirmada" com atraso. Usar a referência atrasada fazia a agulha
        //   disparar para longe durante uma troca real de nota (a frequência já
        //   estava perto da nota nova, mas os cents continuavam calculados
        //   contra a nota antiga até a confirmação chegar) e só "estalar" de
        //   volta quando o nome finalmente trocava — pior quanto mais frames de
        //   confirmação o modo exigisse (Balanceado/Estável).
        //   A EMA (α do modo de precisão) suaviza o jitter residual, e é
        //   resetada sempre que a nota DETECTADA muda (não quando o nome exibido
        //   muda, que tem atraso) — sem misturar cents de notas diferentes.
        observePitch()
            .onEach { result ->
                if (result != null) {
                    silenceEmitted = false
                    val rawNoteName = result.note.fullName
                    val conf = result.confidence
                    val mode = currentConfig.precisionMode

                    // 1. Note confirmation: só troca o NOME exibido após N frames consecutivos.
                    val displayNote: String = if (rawNoteName == lastNote) {
                        pendingNote = rawNoteName
                        pendingNoteCount = 0
                        lastNote
                    } else {
                        if (rawNoteName == pendingNote) {
                            pendingNoteCount++
                        } else {
                            pendingNote = rawNoteName
                            pendingNoteCount = 1
                        }
                        if (pendingNoteCount >= mode.noteConfirmationFrames) {
                            rawNoteName
                        } else {
                            lastNote // mantém nome confirmado enquanto acumula frames
                        }
                    }

                    // 2. Cents sempre relativos à nota mais próxima da frequência DETECTADA
                    //    (não à exibida) + EMA para suavizar a agulha.
                    val rawCents = result.note.centsDeviation
                    // Reset quando a nota detectada muda de fato: misturar cents de
                    // notas diferentes na EMA causaria um salto espúrio na agulha.
                    val rawNoteChanged = rawNoteName != lastRawNoteName
                    smoothedCents = if (!centsInitialized || rawNoteChanged) {
                        centsInitialized = true
                        rawCents
                    } else {
                        mode.centsEmaAlpha * rawCents + (1f - mode.centsEmaAlpha) * smoothedCents
                    }
                    lastRawNoteName = rawNoteName

                    // 3. Deduplicação: só emite novo estado quando algo mudou visivelmente.
                    val noteChanged = displayNote != lastNote // != por valor (não ===)
                    val centsChanged = kotlin.math.abs(smoothedCents - lastCents) > CENTS_CHANGE_THRESHOLD
                    val confChanged = kotlin.math.abs(conf - lastConfidence) > CONFIDENCE_CHANGE_THRESHOLD

                    if (noteChanged || centsChanged || confChanged) {
                        lastNote = displayNote
                        lastCents = smoothedCents
                        lastConfidence = conf
                        _uiState.update { ui ->
                            ui.copy(
                                detectedNote = displayNote,
                                detectedFrequency = result.detectedFrequency,
                                centsDeviation = smoothedCents,
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
                pendingNote = "--"
                pendingNoteCount = 0
                lastRawNoteName = "--"
                smoothedCents = 0f
                centsInitialized = false
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

    /**
     * Ajusta a frequência de referência A4: aplica a quente no detector
     * (vale a partir do próximo frame) e persiste no DataStore.
     */
    fun onReferenceA4Change(frequency: Float) {
        _uiState.update { it.copy(referenceA4 = frequency) }
        applyConfigChange { it.copy(referenceA4 = frequency) }
    }

    /**
     * Troca o modo de precisão (estabilidade vs resposta): aplica a quente
     * no detector e nos parâmetros de suavização da UI, e persiste.
     */
    fun onPrecisionModeChange(mode: TunerPrecisionMode) {
        _uiState.update { it.copy(precisionMode = mode) }
        applyConfigChange { it.copy(precisionMode = mode) }
    }

    /** Atualiza [currentConfig], propaga ao detector ativo e persiste. */
    private fun applyConfigChange(transform: (TunerConfig) -> TunerConfig) {
        val newConfig = transform(currentConfig)
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
        // Frames de confirmação de nota e α da EMA de cents vêm do
        // TunerPrecisionMode escolhido pelo usuário (currentConfig.precisionMode).

        // Mudança mínima em cents para emitir novo estado (1 cent — variação sutil mas real).
        private const val CENTS_CHANGE_THRESHOLD = 1.0f

        // Mudança mínima de confiança para emitir novo estado (5%).
        private const val CONFIDENCE_CHANGE_THRESHOLD = 0.05f
    }
}
