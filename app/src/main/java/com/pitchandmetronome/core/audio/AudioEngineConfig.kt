package com.pitchandmetronome.core.audio

/**
 * Constantes de configuração compartilhadas pelas engines de áudio.
 *
 * Valores escolhidos para equilibrar latência e qualidade:
 * - SAMPLE_RATE 48000 Hz: taxa nativa da maioria dos DSPs Android modernos.
 *   Usar a taxa nativa do dispositivo evita conversão de sample rate, que
 *   adiciona latência e artefatos.
 * - FRAMES_PER_BURST: Oboe determina automaticamente o valor ótimo para o
 *   dispositivo. Forçar um valor muito pequeno pode causar underruns.
 * - YIN_BUFFER_SIZE: múltiplo de 2 para eficiência no YIN; cobre ~93ms a
 *   48kHz — suficiente para detectar notas baixas (< 80Hz, período > 12ms).
 */
object AudioEngineConfig {

    /** Taxa de amostragem preferida. Oboe pode negociar outra taxa com o hardware. */
    const val PREFERRED_SAMPLE_RATE = 48_000

    /**
     * Tamanho do buffer de captura para detecção de pitch (YIN).
     * 4096 amostras @ 48kHz ≈ 85ms — necessário para frequências abaixo de ~80Hz.
     */
    const val YIN_BUFFER_SIZE = 4_096

    /**
     * Limiar de confiança mínima do YIN (d' < threshold = pitch detectado).
     *
     * Na função CMND do YIN, valores baixos indicam alta periodicidade.
     * - 0.10 → altamente estrito; descartar quase tudo que não seja sinal puro.
     * - 0.15 → equilíbrio entre precisão e sensibilidade (recomendado).
     * - 0.20 → mais permissivo; aceita sinais com mais harmônicos/ruído.
     *
     * Ajuste para baixo se o afinador perder notas legítimas.
     * Ajuste para cima se emitir muitos falsos positivos em silêncio.
     */
    const val YIN_CONFIDENCE_THRESHOLD = 0.15f

    /** Frequência de referência padrão para A4 (diapasão padrão ISO 16). */
    const val DEFAULT_REFERENCE_A4_HZ = 440f

    /** BPM mínimo suportado pelo metrônomo. */
    const val METRONOME_BPM_MIN = 20

    /** BPM máximo suportado pelo metrônomo. */
    const val METRONOME_BPM_MAX = 300
}
