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

    /**
     * Número de cantos de áudio lidos por chamada de [AudioRecord.read].
     * Separado do [YIN_BUFFER_SIZE] para permitir que cada implementação
     * ajuste o tamanho de leitura independentemente da janela de análise.
     *
     * 256 samples @ 48 kHz ≈ 5.3 ms por leitura — baixa latência de leitura.
     * Usado pela implementação [AudioCaptureEngine] que faz leituras menores
     * em um buffer de acumulação antes de chamar o YIN.
     */
    const val CAPTURE_FRAME_SIZE = 256

    /**
     * Correlação normalizada mínima para o [PitchDetector] baseado em NACF
     * aceitar um pico como pitch válido. Espelha [PitchDetector.ACF_CONFIDENCE_THRESHOLD].
     * Separado de [YIN_CONFIDENCE_THRESHOLD] porque os dois algoritmos usam
     * escalas invertidas: NACF busca máximos próximos de 1.0; YIN busca
     * mínimos próximos de 0.0.
     */
    const val ACF_CONFIDENCE_THRESHOLD = 0.50f

    /** Frequência de referência padrão para A4 (diapasão padrão ISO 16). */
    const val DEFAULT_REFERENCE_A4_HZ = 440f

    /** BPM mínimo suportado pelo metrônomo. */
    const val METRONOME_BPM_MIN = 20

    /** BPM máximo suportado pelo metrônomo. */
    const val METRONOME_BPM_MAX = 300
}
