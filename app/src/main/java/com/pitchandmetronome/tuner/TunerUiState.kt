package com.pitchandmetronome.tuner

import com.pitchandmetronome.core.audio.AudioEngineConfig

/**
 * Estado imutável da UI do afinador.
 *
 * @param isListening Stream de captura de áudio ativo.
 * @param hasSignal Há um pitch sendo detectado agora. `false` em silêncio — a UI
 *   mantém a última nota esmaecida em vez de apagá-la.
 * @param detectedNote Nome da nota exibida (ex: "A4", "C#3"). "--" antes da primeira detecção.
 * @param detectedFrequency Frequência estabilizada em Hz. 0f quando inativo.
 * @param centsDeviation Desvio em cents em relação a [detectedNote], limitado a -50..+50.
 * @param isInTune Dentro da tolerância de afinação (com histerese — ver
 *   [com.pitchandmetronome.domain.tuner.TuningCalculator.IN_TUNE_ENTER_CENTS]).
 * @param referenceA4 Frequência de referência para A4 atualmente configurada.
 * @param hasAudioPermission Estado da permissão RECORD_AUDIO.
 * @param isLoading `true` durante start/stop do detector.
 * @param errorMessage Mensagem de erro para exibir como Snackbar.
 */
data class TunerUiState(
    val isListening: Boolean = false,
    val hasSignal: Boolean = false,
    val detectedNote: String = "--",
    val detectedFrequency: Float = 0f,
    val centsDeviation: Float = 0f,
    val isInTune: Boolean = false,
    val referenceA4: Float = AudioEngineConfig.DEFAULT_REFERENCE_A4_HZ,
    val hasAudioPermission: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
