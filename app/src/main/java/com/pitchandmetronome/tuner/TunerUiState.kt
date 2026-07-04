package com.pitchandmetronome.tuner

import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.domain.model.tuner.TunerPrecisionMode

/**
 * Estado imutável da UI do afinador.
 *
 * @param isListening Stream de captura de áudio ativo.
 * @param detectedNote Nome da nota detectada (ex: "A4", "C#3"). "--" quando inativo.
 * @param detectedFrequency Frequência detectada em Hz. 0f quando inativo.
 * @param centsDeviation Desvio em cents (-50..+50). 0f quando inativo.
 * @param confidence Índice de confiança do algoritmo (0f..1f).
 * @param referenceA4 Frequência de referência para A4 atualmente configurada.
 * @param precisionMode Modo de precisão (estabilidade vs resposta) configurado.
 * @param hasAudioPermission Estado da permissão RECORD_AUDIO.
 * @param isLoading `true` durante start/stop do detector.
 * @param errorMessage Mensagem de erro para exibir como Snackbar.
 */
data class TunerUiState(
    val isListening: Boolean = false,
    val detectedNote: String = "--",
    val detectedFrequency: Float = 0f,
    val centsDeviation: Float = 0f,
    val confidence: Float = 0f,
    val micLevel: Float = 0f,
    val referenceA4: Float = AudioEngineConfig.DEFAULT_REFERENCE_A4_HZ,
    val precisionMode: TunerPrecisionMode = TunerPrecisionMode.DEFAULT,
    val hasAudioPermission: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
