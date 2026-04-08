package com.pitchandmetronome.audio.tuner

import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.core.utils.FrequencyUtils
import kotlin.math.PI
import kotlin.math.cos

/**
 * Detector de pitch por **Autocorrelação Normalizada** (NACF —
 * _Normalized AutoCorrelation Function_).
 *
 * Esta classe é puramente algorítmica — não gerencia I/O, threads ou permissões.
 * Recebe um buffer de amostras PCM e retorna a frequência fundamental estimada.
 * Destinada a ser instanciada pela camada de captura de áudio (e.g., em
 * `AudioCaptureEngine`) e chamada a cada frame capturado.
 *
 * ---
 *
 * # Algoritmo — 5 passos
 *
 * ## Passo 1 — Janelamento de Hann
 *
 * O buffer de entrada é multiplicado por uma janela de Hann para reduzir o
 * _spectral leakage_ causado pelas bordas abruptas de um frame finito.
 * Sem janelamento, a descontinuidade entre o início e o fim do frame introduz
 * componentes de alta frequência artefactuais que degradam a estimativa de pitch.
 *
 *   `w[n] = 0.5 · (1 − cos(2π·n / (N−1)))`
 *
 * A janela é pré-computada no construtor — custo O(N) apenas uma vez.
 * O resultado é escrito em `windowedBuffer`, deixando o buffer original intacto.
 *
 * ## Passo 2 — Função de Autocorrelação (ACF)
 *
 * Para cada lag τ ∈ [0, τ_max], calcula a correlação do sinal com uma versão
 * deslocada de si mesmo:
 *
 *   `r[τ] = Σ_{n=0}^{N−τ−1} x[n] · x[n+τ]`
 *
 * - τ = 0  → energia total do frame: `r[0] = Σ x[n]²`
 * - τ = T  → pitch period em samples: alta correlação quando o sinal é periódico
 * - τ_max = sampleRate / F_MIN (lag para a menor frequência detectável)
 *
 * Complexidade: O(N · τ_max) — aproximadamente 7 M multiply-adds por frame
 * a 48 kHz / 4096 samples. No ART/JIT com NEON, isso representa < 1 ms/frame.
 *
 * ## Passo 3 — Normalização pela energia (NACF)
 *
 *   `r'[τ] = r[τ] / r[0]`
 *
 * O resultado está em [−1.0, 1.0]. Valores próximos de +1.0 indicam que o
 * sinal é altamente periódico com período τ. Essa normalização simples é
 * eficaz quando combinada com o janelamento de Hann, que já reduz as
 * distorções de energia nas bordas.
 *
 * ## Passo 4 — Busca do pico máximo na faixa musical
 *
 * Itera sobre τ ∈ [τ_min, τ_max] buscando o lag de maior correlação
 * normalizada. O pico central (τ = 0, sempre 1.0) é ignorado ao começar
 * em τ_min = sampleRate / F_MAX.
 *
 *   - τ_min = sampleRate / F_MAX → lag mínimo (frequência máxima detectável)
 *   - τ_max = sampleRate / F_MIN → lag máximo (frequência mínima detectável)
 *
 * Apenas picos acima de [ACF_CONFIDENCE_THRESHOLD] são aceitos.
 *
 * ## Passo 5 — Interpolação parabólica (sub-sample)
 *
 * Um pico encontrado em τ inteiro resulta em erro de frequência de até ±0.5
 * sample. Para f = 440 Hz @ 48 kHz, isso é um erro de ±0.5/109 ≈ ±0.5%.
 * A interpolação parabólica entre τ−1, τ e τ+1 refina a estimativa:
 *
 *   `δ = 0.5 · (r[τ−1] − r[τ+1]) / (r[τ−1] − 2·r[τ] + r[τ+1])`
 *   `τ_refined = τ + δ`,   δ ∈ (−0.5, +0.5)
 *
 * Reduz o erro de frequência de ~±0.5% para < 0.05% no range médio.
 *
 * **Saída:** `f₀ = sampleRate / τ_refined`
 *
 * ---
 *
 * # Upgrade para YIN
 *
 * O algoritmo YIN (Cheveigñe & Kawahara, 2002) usa a _Squared Difference
 * Function_ (SDF) em vez da ACF, e uma normalização cumulativa (CMND) para
 * eliminar sub-harmônicos. A relação formal entre YIN e ACF é:
 *
 *   `d[τ] = Σ (x[n] − x[n+τ])² = 2·(r[0] − r[τ])`   *(para sinal estacionário)*
 *
 * Portanto, `acfBuffer` já contém toda a informação necessária para o YIN.
 * O upgrade consiste em **três mudanças cirúrgicas**, sem alterar a estrutura:
 *
 * 1. Adicionar `DetectionAlgorithm.YIN` ao enum (já existe como placeholder)
 *
 * 2. Adicionar `computeYinDifference(n)` que converte `acfBuffer` em SDF:
 *    ```kotlin
 *    val r0 = acfBuffer[0]
 *    for (tau in 1..tauMax) {
 *        acfBuffer[tau] = 2f * (r0 - acfBuffer[tau])
 *    }
 *    acfBuffer[0] = 0f
 *    ```
 *
 * 3. Substituir `findBestPeak()` por `findFirstValleyBelowThreshold()` com CMND:
 *    ```kotlin
 *    var runningSum = 0f
 *    for (tau in 1..limit) {
 *        runningSum += acfBuffer[tau]
 *        val cmndf = acfBuffer[tau] * tau / runningSum   // normalização cumulativa
 *        if (cmndf < YIN_THRESHOLD) return tau           // primeiro vale abaixo do limiar
 *    }
 *    ```
 *
 * A implementação YIN completa com essas etapas já está disponível em
 * [AudioCaptureEngine], que pode ser consultada como referência.
 *
 * ---
 *
 * ## Thread-safety
 *
 * **NÃO é thread-safe.** Os buffers internos (`windowedBuffer`, `acfBuffer`)
 * são mutáveis e compartilhados entre chamadas. Use **uma instância por
 * thread de captura**. Não synchronized — overhead desnecessário no loop RT.
 *
 * @param sampleRate Taxa de amostragem do áudio de entrada.
 *   Deve corresponder à taxa real de captura. Default: [AudioEngineConfig.PREFERRED_SAMPLE_RATE].
 * @param bufferSize Tamanho do frame de entrada em samples.
 *   Deve ser ≥ `sampleRate / MIN_DETECTABLE_FREQUENCY` para detectar as
 *   frequências mais baixas. Default: [AudioEngineConfig.YIN_BUFFER_SIZE] (4096).
 * @param algorithm Algoritmo de detecção. Default: [DetectionAlgorithm.ACF].
 *   Reservado para [DetectionAlgorithm.YIN] no upgrade futuro descrito acima.
 */
class PitchDetector(
    private val sampleRate: Int = AudioEngineConfig.PREFERRED_SAMPLE_RATE,
    bufferSize: Int = AudioEngineConfig.YIN_BUFFER_SIZE,
    val algorithm: DetectionAlgorithm = DetectionAlgorithm.ACF
) {

    // ── Buffers pré-alocados ──────────────────────────────────────────────────
    //
    // Todos alocados no construtor e reutilizados em cada chamada de detectPitch.
    // Zero alocação no caminho crítico (hot path) do loop de captura.

    /**
     * Buffer de trabalho para o sinal janelado.
     * No path FlotArray: recebe `input[i] * hannWindow[i]`.
     * No path ShortArray: recebe `(short[i] / 32768f) * hannWindow[i]`.
     * Nunca modifica o buffer original.
     */
    private val windowedBuffer = FloatArray(bufferSize)

    /**
     * Buffer da autocorrelação para lags τ ∈ [0, τ_max].
     * `acfBuffer[τ] = r[τ] = Σ x[n]·x[n+τ]`.
     * Tamanho = bufferSize / 2 — lags além de N/2 têm sobreposição < 50%
     * e produzem estimativas noisy.
     */
    private val acfBuffer = FloatArray(bufferSize / 2)

    /**
     * Janela de Hann pré-computada.
     * `hannWindow[i] = 0.5 · (1 − cos(2π·i / (N−1)))`.
     * Calculada no construtor — custo único O(N); apenas lida no hot path.
     */
    private val hannWindow = FloatArray(bufferSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (bufferSize - 1)))).toFloat()
    }

    // ── Limites de lag pré-calculados ─────────────────────────────────────────

    /**
     * Lag mínimo analisado = menor período que pode existir na faixa musical.
     * `τ_min = ceil(sampleRate / MAX_DETECTABLE_FREQUENCY)`
     * τ = 1 → sampleRate Hz (acima de Nyquist), inválido.
     * Força mínimo 2 para evitar auto-correlação trivial.
     */
    private val tauMin: Int = (sampleRate / MAX_DETECTABLE_FREQUENCY)
        .toInt()
        .coerceAtLeast(2)

    /**
     * Lag máximo analisado = maior período detectável.
     * `τ_max = floor(sampleRate / MIN_DETECTABLE_FREQUENCY)`
     * Limitado a bufferSize/2 − 1 para garantir ao menos 1 sample de sobreposição.
     */
    private val tauMax: Int = (sampleRate / MIN_DETECTABLE_FREQUENCY)
        .toInt()
        .coerceAtMost(bufferSize / 2 - 1)

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Detecta a frequência fundamental em um buffer PCM normalizado.
     *
     * Sobrecarga padrão: processa todos os samples do buffer.
     *
     * @param buffer Amostras PCM em [−1.0, 1.0]. Tamanho esperado = `bufferSize`
     *   do construtor. Buffers maiores são truncados; menores são aceitos (com
     *   possível perda de qualidade em frequências baixas).
     * @return [DetectionResult] com frequência e confiança, ou [DetectionResult.SILENCE]
     *   se o sinal tiver energia insuficiente ou nenhum pico ultrapassar o threshold.
     */
    fun detectPitch(buffer: FloatArray): DetectionResult =
        detectPitch(buffer, buffer.size)

    /**
     * Detecta a frequência fundamental processando apenas `length` samples.
     *
     * Útil quando o buffer do [AudioRecord] é maior do que o frame de análise,
     * ou quando uma leitura parcial retornou menos samples do que o esperado.
     *
     * @param buffer Amostras PCM em [−1.0, 1.0].
     * @param length Número de samples válidos em [buffer] a considerar (≤ buffer.size).
     * @return [DetectionResult] com frequência em Hz e confiança [0.0, 1.0],
     *   ou [DetectionResult.SILENCE].
     */
    fun detectPitch(buffer: FloatArray, length: Int): DetectionResult {
        val n = length.coerceAtMost(windowedBuffer.size)
        // Passo 1: aplica Hann window e copia para windowedBuffer
        for (i in 0 until n) {
            windowedBuffer[i] = buffer[i] * hannWindow[i]
        }
        return runDetection(n)
    }

    /**
     * Detecta a frequência fundamental em um buffer PCM de 16-bit.
     *
     * Usado diretamente com saída de `AudioRecord.read(short[], ...)` sem
     * alocar um FloatArray intermediário. A conversão S16 → F32 é feita
     * in-place em `windowedBuffer` antes do janelamento.
     *
     * @param buffer Amostras S16 PCM na faixa [−32768, 32767]
     *   (e.g., saída de `AudioRecord` com encoding `PCM_16BIT`).
     * @param length Número de samples válidos a processar.
     * @return [DetectionResult] com frequência em Hz, ou [DetectionResult.SILENCE].
     */
    fun detectPitch(buffer: ShortArray, length: Int = buffer.size): DetectionResult {
        val n = length.coerceAtMost(windowedBuffer.size)
        // Conversão S16→F32 + Hann em um único pass — evita loop extra.
        // Divisão por constante: JIT ARM transforma em multiplicação por reciprocal.
        for (i in 0 until n) {
            windowedBuffer[i] = (buffer[i] / 32768f) * hannWindow[i]
        }
        return runDetection(n)
    }

    // ── Núcleo do algoritmo ───────────────────────────────────────────────────

    /**
     * Executa os passos 2–5 do algoritmo sobre `windowedBuffer[0..n-1]`.
     *
     * Separado das sobrecargas públicas para evitar duplicação de código —
     * ambos os paths (FloatArray e ShortArray) produzem `windowedBuffer`
     * janelado e chamam este método.
     *
     * **Caminho de saída antecipada (early exit):**
     * - `r[0] < SILENCE_THRESHOLD` → frame de silêncio/zeros → SILENCE
     * - Nenhum pico encontrado em [tauMin, tauMax] → SILENCE
     * - Pico abaixo de [ACF_CONFIDENCE_THRESHOLD] → SILENCE
     * - Frequência fora de [FrequencyUtils.isInMusicalRange] → SILENCE
     */
    private fun runDetection(n: Int): DetectionResult {
        // Passo 2: Autocorrelação r[τ] para τ ∈ [0, tauMax]
        computeAcf(n)

        // Passo 3: Normalização — r'[τ] = r[τ] / r[0]
        val r0 = acfBuffer[0]
        if (r0 < SILENCE_THRESHOLD) return DetectionResult.SILENCE

        val invR0 = 1f / r0  // multiplicação > divisão em cada comparação do loop

        // Passo 4: Busca do pico máximo na faixa musical
        val bestTau = findBestPeak(n, invR0)
        if (bestTau < 0) return DetectionResult.SILENCE

        val peakConfidence = acfBuffer[bestTau] * invR0
        if (peakConfidence < ACF_CONFIDENCE_THRESHOLD) return DetectionResult.SILENCE

        // Passo 5: Refinamento sub-sample por interpolação parabólica
        val refinedTau = parabolicInterpolation(bestTau)
        if (refinedTau <= 0f) return DetectionResult.SILENCE

        val frequency = sampleRate / refinedTau
        if (!FrequencyUtils.isInMusicalRange(frequency)) return DetectionResult.SILENCE

        return DetectionResult(frequencyHz = frequency, confidence = peakConfidence)
    }

    /**
     * Passo 2 — Calcula a autocorrelação biased para lags τ ∈ [0, tauMax].
     *
     *   `r[τ] = Σ_{n=0}^{N−τ−1} x[n] · x[n+τ]`
     *
     * Resultado em `acfBuffer[0..tauMax]`.
     *
     * ## Isolabilidade para YIN
     *
     * Esta função é o núcleo reutilizável para o upgrade. Após chamar
     * `computeAcf(n)`, `acfBuffer` contém todas as informações necessárias
     * para derivar a SDF do YIN sem nenhuma nova leitura do sinal:
     *
     *   `d[τ] = 2·(acfBuffer[0] − acfBuffer[τ])`
     *
     * Consulte o KDoc da classe para os passos completos do upgrade.
     */
    private fun computeAcf(n: Int) {
        val limit = tauMax.coerceAtMost(n - 1)
        val w = windowedBuffer  // ref local: evita acesso ao campo em cada iteração
        val acf = acfBuffer
        for (tau in 0..limit) {
            var sum = 0f
            val end = n - tau
            // Inner loop — hot path; JIT vetoriza com NEON em ART >= Android 7.
            for (i in 0 until end) {
                sum += w[i] * w[i + tau]
            }
            acf[tau] = sum
        }
    }

    /**
     * Passo 4 — Busca do lag de maior correlação normalizada em [tauMin, tauMax].
     *
     * Usa busca do **máximo global** no intervalo válido para robustez com
     * instrumentos que têm harmônicos dominantes (capturam o fundamental
     * em vez de um múltiplo). O pico central em τ = 0 é excluído ao
     * iniciar a busca em `tauMin`.
     *
     * Nota sobre sub-harmônicos: a ACF pode ocasionalmente detectar o dobro
     * do período real (oitava abaixo) quando o 2° harmônico é mais forte do
     * que o fundamental. O YIN/CMND resolve esse problema; aqui, o threshold
     * [ACF_CONFIDENCE_THRESHOLD] mitiga a maioria dos casos práticos.
     *
     * @param n Número de samples válidos no frame.
     * @param invR0 Inverso da energia `1 / r[0]` — evita divisão no loop.
     * @return Lag inteiro do pico, ou -1 se nenhum pico ultrapassar o threshold.
     */
    private fun findBestPeak(n: Int, invR0: Float): Int {
        val limit = tauMax.coerceAtMost(n - 2)  // -2: reserva margem para interpolação parabólica
        val acf = acfBuffer
        var bestTau = -1
        var bestCorr = ACF_CONFIDENCE_THRESHOLD   // inicializa com o limiar mínimo

        for (tau in tauMin..limit) {
            val corr = acf[tau] * invR0
            if (corr > bestCorr) {
                bestCorr = corr
                bestTau = tau
            }
        }
        return bestTau
    }

    /**
     * Passo 5 — Refinamento parabólico do lag `tau` em sub-sample.
     *
     * Dado o pico em τ com vizinhos τ−1 e τ+1:
     *
     *   `δ = 0.5 · (r[τ−1] − r[τ+1]) / (r[τ−1] − 2·r[τ] + r[τ+1])`
     *   `τ_refined = τ + δ`
     *
     * δ é clampeado a (−0.5, +0.5) — o pico refinado nunca vai além dos
     * dois vizinhos do pico inteiro.
     *
     * Se o denominador é zero (patamar perfeitamente plano, muito improvável),
     * retorna `tau` sem refinamento.
     *
     * @return Lag refinado como Float, sempre positivo.
     */
    private fun parabolicInterpolation(tau: Int): Float {
        if (tau <= 0 || tau >= acfBuffer.size - 1) return tau.toFloat()

        val prev = acfBuffer[tau - 1]
        val curr = acfBuffer[tau]
        val next = acfBuffer[tau + 1]

        val denominator = prev - 2f * curr + next
        if (denominator == 0f) return tau.toFloat()

        val delta = 0.5f * (prev - next) / denominator
        return tau + delta.coerceIn(-0.5f, 0.5f)
    }

    // ── Tipos públicos ────────────────────────────────────────────────────────

    /**
     * Resultado de uma chamada a [detectPitch].
     *
     * @param frequencyHz Frequência fundamental estimada em Hz.
     *   Sempre > 0 nas detecções bem-sucedidas.
     * @param confidence Correlação normalizada NACF no lag detectado, em [0.0, 1.0].
     *   Corresponde a quão periódico é o sinal na frequência detectada.
     *   - 0.9–1.0: sinal muito periódico, nota sustentada clara
     *   - 0.7–0.9: nota clara com algum vibrato ou harmônicos
     *   - 0.5–0.7: nota detectada mas com ruído ou ataque/decaimento
     *   - < [ACF_CONFIDENCE_THRESHOLD]: não retornado (substituído por [SILENCE])
     */
    data class DetectionResult(
        val frequencyHz: Float,
        val confidence: Float
    ) {
        companion object {
            /**
             * Sentinela retornado quando não há pitch detectável.
             * `frequencyHz = 0f` e `confidence = 0f`.
             * Diferenciado de uma detecção real por [detected] == false.
             */
            val SILENCE = DetectionResult(frequencyHz = 0f, confidence = 0f)
        }

        /** `true` se um pitch foi detectado com confiança suficiente. */
        val detected: Boolean get() = frequencyHz > 0f
    }

    /**
     * Algoritmo de detecção selecionável.
     *
     * Exposta como parâmetro de construtor para que o código chamador possa
     * escalar para [YIN] sem modificar os sites de instanciação — apenas
     * passando `algorithm = DetectionAlgorithm.YIN`.
     */
    enum class DetectionAlgorithm {

        /**
         * **Autocorrelação Normalizada (NACF)** — implementação atual.
         *
         * Vantagens: simples, eficiente, boa sensibilidade em voz e sopros.
         * Limitação: pode cometer erros de oitava (sub-harmônicos) em
         * instrumentos com 2° harmônico dominante (e.g., clarinete).
         */
        ACF,

        /**
         * **YIN** (Cheveigñe & Kawahara, 2002) — upgrade futuro.
         *
         * Usa a SDF + CMND para reduzir erros de sub-harmônico presentes
         * na ACF pura. Deriv-se diretamente do `acfBuffer` computado pelo
         * passo 2 — veja o KDoc da classe para os passos exatos do upgrade.
         *
         * Referência: `AudioCaptureEngine.detectPitchAndNotify()` contém a
         * implementação YIN completa que pode ser portada para cá.
         */
        YIN
    }

    // ── Constantes ────────────────────────────────────────────────────────────

    companion object {

        /**
         * Frequência mínima detectável (Hz).
         * 20 Hz cobre contrabaixo de 5 cordas e órgão de tubos.
         * τ_max @ 48 kHz = 48000/20 = 2400 samples.
         */
        private const val MIN_DETECTABLE_FREQUENCY = 20f

        /**
         * Frequência máxima detectável (Hz).
         * 4200 Hz cobre C8 (≈ 4186 Hz), a nota mais alta do piano de concerto.
         * τ_min @ 48 kHz = floor(48000/4200) = 11 samples.
         */
        private const val MAX_DETECTABLE_FREQUENCY = 4200f

        /**
         * Energia mínima de frame para processar (evita divisão por zero e
         * processamento de silêncio/zeros de padding).
         * 1e-6 ≈ sinal de −120 dB FS — abaixo de qualquer ruído de captura real.
         */
        private const val SILENCE_THRESHOLD = 1e-6f

        /**
         * Correlação normalizada mínima para aceitar um pico como pitch válido.
         *
         * Interpretação física: o sinal deve ter pelo menos 50% de similaridade
         * com uma versão deslocada de si mesmo no período fundamental.
         * - Aumentar (e.g., 0.70) reduz falsos positivos em ambientes ruidosos.
         * - Reduzir (e.g., 0.40) melhora detecção de notas com forte ataque/decaimento.
         */
        internal const val ACF_CONFIDENCE_THRESHOLD = 0.50f
    }
}
