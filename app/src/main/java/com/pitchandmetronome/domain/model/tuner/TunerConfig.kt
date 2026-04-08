package com.pitchandmetronome.domain.model.tuner

import com.pitchandmetronome.core.audio.AudioEngineConfig

/**
 * Parâmetros de configuração do afinador.
 *
 * @param referenceA4 Frequência de A4 usada para calcular todas as outras notas.
 *   Valor padrão: 440 Hz (ISO 16). Pode ser ajustado para afinações alternativas
 *   (ex: 432 Hz, 415 Hz para música barroca).
 * @param sampleRate Taxa de amostragem do stream de captura. Deve corresponder
 *   à taxa nativa do dispositivo para evitar re-sampling.
 * @param bufferSize Número de amostras por frame de análise YIN.
 */
data class TunerConfig(
    val referenceA4: Float = AudioEngineConfig.DEFAULT_REFERENCE_A4_HZ,
    val sampleRate: Int = AudioEngineConfig.PREFERRED_SAMPLE_RATE,
    val bufferSize: Int = AudioEngineConfig.YIN_BUFFER_SIZE
)
