package com.pitchandmetronome.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchandmetronome.audio.metronome.IMetronomeEngine
import com.pitchandmetronome.core.utils.CoroutineDispatchers
import com.pitchandmetronome.domain.model.metronome.SoundProfile
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import com.pitchandmetronome.domain.usecase.metronome.ObserveMetronomeStateUseCase
import com.pitchandmetronome.domain.usecase.metronome.SetBpmUseCase
import com.pitchandmetronome.domain.usecase.metronome.SetTimeSignatureUseCase
import com.pitchandmetronome.domain.usecase.metronome.StartMetronomeUseCase
import com.pitchandmetronome.domain.usecase.metronome.StopMetronomeUseCase
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
 * ViewModel da feature de Metrônomo.
 *
 * Responsabilidades:
 * - Orquestrar use cases em resposta a eventos da UI
 * - Combinar [MetronomeState] do repositório com [BeatEvent] do engine
 *   em um único [MetronomeUiState] para a Composable
 * - Gerenciar estados de loading e erro
 * - Liberar recursos do engine ao ser destruído
 *
 * **Por que combinar dois Flows?**
 * O repositório conhece a configuração persistida (BPM, compasso).
 * O engine emite eventos em tempo-real (beats). A UI precisa dos dois.
 * O ViewModel faz o merge sem expor a complexidade para a Composable.
 */
@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val startMetronome: StartMetronomeUseCase,
    private val stopMetronome: StopMetronomeUseCase,
    private val setBpm: SetBpmUseCase,
    private val setTimeSignature: SetTimeSignatureUseCase,
    private val observeMetronomeState: ObserveMetronomeStateUseCase,
    private val engine: IMetronomeEngine,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetronomeUiState())
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    /** Timestamps dos últimos taps para cálculo de TAP tempo. */
    private val tapTimestamps = mutableListOf<Long>()

    /** Tempo máximo entre taps antes de resetar (2 segundos). */
    private val tapTimeoutMs = 2000L

    /** Número máximo de taps a considerar para a média. */
    private val maxTaps = 8

    init {
        // Observa mudanças de configuração do repositório
        observeMetronomeState()
            .onEach { state ->
                _uiState.update { ui ->
                    ui.copy(
                        isPlaying = state.isPlaying,
                        bpm = state.bpm,
                        timeSignature = state.timeSignature,
                        soundProfile = state.soundProfile,
                        accentFirstBeat = state.accentFirstBeat
                    )
                }
            }
            .launchIn(viewModelScope)

        // Observa eventos de beat do engine para acionar animações
        engine.beatFlow
            .onEach { beatEvent ->
                _uiState.update { it.copy(lastBeatEvent = beatEvent) }
            }
            .launchIn(viewModelScope)
    }

    /** Alterna entre play e pause. */
    fun onPlayPause() {
        viewModelScope.launch(dispatchers.default) {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                if (_uiState.value.isPlaying) stopMetronome() else startMetronome()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Chamado a cada mudança no slider de BPM (inclui feedback háptico na UI). */
    fun onBpmChange(bpm: Int) {
        viewModelScope.launch(dispatchers.default) {
            runCatching { setBpm(bpm) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun onTimeSignatureChange(timeSignature: TimeSignature) {
        viewModelScope.launch(dispatchers.default) {
            runCatching { setTimeSignature(timeSignature) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    /** Altera apenas o numerador (beatsPerMeasure) mantendo o denominador atual. */
    fun onBeatsPerMeasureChange(beats: Int) {
        val current = _uiState.value.timeSignature
        val newTs = runCatching { TimeSignature(beats, current.beatUnit) }.getOrNull() ?: return
        onTimeSignatureChange(newTs)
    }

    /** Altera apenas o denominador (beatUnit) mantendo o numerador atual. */
    fun onBeatUnitChange(unit: Int) {
        val current = _uiState.value.timeSignature
        val newTs = runCatching { TimeSignature(current.beatsPerMeasure, unit) }.getOrNull() ?: return
        onTimeSignatureChange(newTs)
    }

    /** Reseta o BPM para o primeiro beat do compasso (reinicia contagem). */
    fun onResetBeat() {
        if (_uiState.value.isPlaying) {
            viewModelScope.launch(dispatchers.default) {
                runCatching {
                    stopMetronome()
                    startMetronome()
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            }
        }
    }

    fun onSoundProfileChange(soundProfile: SoundProfile) {
        // Implementar: delegar para SetSoundProfileUseCase
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** TAP tempo: calcula BPM baseado nos intervalos entre taps. */
    fun onTapTempo() {
        val now = System.currentTimeMillis()

        // Se passou muito tempo desde o último tap, recomeça
        if (tapTimestamps.isNotEmpty() && (now - tapTimestamps.last()) > tapTimeoutMs) {
            tapTimestamps.clear()
        }

        tapTimestamps.add(now)

        // Mantém apenas os últimos N taps
        while (tapTimestamps.size > maxTaps) {
            tapTimestamps.removeFirst()
        }

        // Precisa de pelo menos 2 taps para calcular BPM
        if (tapTimestamps.size >= 2) {
            val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
            val avgIntervalMs = intervals.average()
            val calculatedBpm = (60_000.0 / avgIntervalMs)
                .toInt()
                .coerceIn(
                    com.pitchandmetronome.core.audio.AudioEngineConfig.METRONOME_BPM_MIN,
                    com.pitchandmetronome.core.audio.AudioEngineConfig.METRONOME_BPM_MAX
                )
            onBpmChange(calculatedBpm)
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
