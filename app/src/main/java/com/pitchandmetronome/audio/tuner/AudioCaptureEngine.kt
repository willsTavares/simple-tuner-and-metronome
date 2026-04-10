package com.pitchandmetronome.audio.tuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.core.utils.FrequencyUtils
import com.pitchandmetronome.domain.model.tuner.PitchResult
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Engine de captura de áudio com detecção de pitch via algoritmo YIN.
 * Implementa [IPitchDetector] usando a API [AudioRecord] do Android (pura JVM, sem NDK).
 *
 * ---
 * ## Decisões Técnicas
 *
 * ### 1. `AudioSource.VOICE_RECOGNITION` em vez de `MIC`
 * `VOICE_RECOGNITION` instrui o HAL de áudio a **não aplicar** AGC (Automatic Gain
 * Control) nem supressão de ruído. Esses processamentos distorcem a forma de onda
 * de instrumentos musicais e comprometem a precisão do pitch. `MIC` aplica ambos
 * por padrão. `UNPROCESSED` seria ideal, mas não é garantido em todos os dispositivos.
 *
 * ### 2. Thread dedicada + `Process.setThreadPriority(THREAD_PRIORITY_AUDIO)`
 * Coroutines usam um pool de threads compartilhado (`Dispatchers.Default`). Qualquer
 * task pesada no pool pode atrasar o loop de captura, causando overruns no buffer
 * interno do `AudioRecord` (amostras perdidas). Uma `Thread` dedicada com prioridade
 * `THREAD_PRIORITY_AUDIO` (-16 no scheduler Linux via `setpriority(PRIO_PROCESS)`)
 * recebe slots de CPU mais frequentes e previsíveis. `Thread.MAX_PRIORITY` do Java
 * não é suficiente — ele não altera a prioridade real do SO.
 *
 * ### 3. Buffer pré-alocado — zero alocação no hot loop
 * [captureBuffer] (ShortArray), [floatBuffer] (FloatArray) e [yinBuffer] (FloatArray)
 * são alocados **uma vez** em [startDetection] e reutilizados em cada iteração do
 * loop de captura. Nenhuma alocação heap ocorre dentro de [captureLoop] nem dentro
 * de [detectPitchAndNotify] — eliminando pausas de GC no caminho crítico de áudio.
 *
 * ### 4. Buffer interno do `AudioRecord` = max(4× mínimo, 2× frame YIN)
 * O ring-buffer interno do `AudioRecord` precisa absorver variações no scheduling da
 * thread de captura. Com 4× o tamanho mínimo, há margem para até ~3 frames atrasados
 * antes de um overrun. O `max()` com `2 × bufferSize` garante que o `AudioRecord`
 * sempre caiba pelo menos um frame completo de YIN no seu buffer.
 *
 * ### 5. Leitura bloqueante (`READ_BLOCKING`)
 * `AudioRecord.read()` em modo bloqueante coloca a thread em sleep até que exatamente
 * `pcm.size` amostras estejam prontas. Isso é "zero CPU" enquanto não há áudio —
 * ideal para uma thread de captura que precisa acordar com alta periodicidade.
 *
 * ### 6. Algoritmo YIN com parabolic interpolation
 * YIN (de Cheveigne & Kawahara, 2002) é o algoritmo padrão para pitch monofônico.
 * Vantagens sobre FFT simples:
 * - Precisão sub-sample via interpolação parabólica (erro < 0.1 cent)
 * - Opera no domínio do tempo (sem windowing, sem spectral leakage)
 * - Thresholding integrado que elimina oitavas falsas (octave errors)
 * - O(N²/2) — para N=4096 são ~8M ops computados a ~12 Hz de análise; aceitável.
 *
 * ### 7. `callbackFlow` como bridge Thread → Flow
 * `callbackFlow` é o construtor idiomático do Kotlin para integrar callbacks (ou loops
 * em threads externas) com Flow. O canal interno bufferiza elementos enquanto o coletor
 * processa, sem bloquear a thread de captura. [pitchCallback] é instalado quando o
 * Flow tem um coletor ativo e removido quando ele é cancelado.
 *
 * ---
 * ## Ciclo de Vida
 * ```
 * startDetection(config)
 *   → AudioRecord iniciado
 *   → Thread de captura inicia (THREAD_PRIORITY_AUDIO)
 *     → loop: read() → normaliza → YIN → pitchCallback → Flow
 * stopDetection()
 *   → isRunning = false → thread encerra loop → AudioRecord.stop/release
 * release()
 *   → para tudo e libera recursos permanentemente (não pode ser reiniciado)
 * ```
 */
@Singleton
class AudioCaptureEngine @Inject constructor() : IPitchDetector {

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var captureThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    //
    // Pré-alocados em startDetection; reutilizados em cada iteração do loop.
    // @Volatile garante visibilidade cross-thread (captureThread lê, main thread aloca).
    //
    // captureBuffer : PCM bruto lido do AudioRecord (16-bit signed integer)
    // floatBuffer   : PCM normalizado [-1.0, 1.0] para o algoritmo YIN
    // yinBuffer     : Buffer de diferença do YIN — tamanho = bufferSize / 2
    //
    @Volatile private var captureBuffer = ShortArray(0)
    @Volatile private var floatBuffer   = FloatArray(0)
    @Volatile private var yinBuffer     = FloatArray(0)

    @Volatile private var currentConfig          = TunerConfig()
    @Volatile private var lastEmittedFrequency   = FREQUENCY_IDLE

    // Instalado quando o Flow tem um coletor ativo. Chamado da capture thread.
    // @Volatile: escrito pelo coletor do Flow (main/Default), lido pela capture thread.
    //
    // Usa `fun interface` em vez de `(Float, Float) -> Unit` para evitar boxing de Float.
    // O tipo de função genérico `Function2<Float, Float, Unit>` recebe parâmetros boxados
    // (java.lang.Float) em cada invocação — 2 alocações por frame YIN (~12 Hz).
    // Com `fun interface`, o compilador Kotlin gera uma interface com método de assinatura
    // primitiva `void onPitch(float, float)`, eliminando completamente o boxing.
    @Volatile private var pitchCallback: PitchDataCallback? = null

    // ── Flow público ─────────────────────────────────────────────────────────

    /**
     * Flow de resultados de detecção de pitch.
     *
     * Emite [PitchResult] quando uma frequência é detectada com confiança acima
     * de [AudioEngineConfig.YIN_CONFIDENCE_THRESHOLD] e com variação superior a
     * [FREQUENCY_CHANGE_THRESHOLD] Hz em relação à última emissão.
     *
     * Emite `null` quando não há sinal suficiente ou em silêncio — permite que
     * a UI exiba um indicador de "ouvindo" sem congelar na última nota.
     */
    override val pitchFlow: Flow<PitchResult?> = callbackFlow {
        // SAM conversion para PitchDataCallback — lambda criado UMA vez, reutilizado
        // em todos os frames. A captura de `this` (para lastEmittedFrequency/currentConfig)
        // e de `channel` (trySend) é feita no momento da criação.
        pitchCallback = PitchDataCallback { frequencyHz, confidence ->
            if (frequencyHz > 0f && FrequencyUtils.isInMusicalRange(frequencyHz)) {
                // Só aloca Note + PitchResult quando a variação de pitch é audível.
                // Abaixo do limiar, o frame anterior ainda representa o pitch corretamente.
                if (abs(frequencyHz - lastEmittedFrequency) >= FREQUENCY_CHANGE_THRESHOLD) {
                    lastEmittedFrequency = frequencyHz
                    val note = FrequencyUtils.frequencyToNote(frequencyHz, currentConfig.referenceA4)
                    trySend(PitchResult(frequencyHz, note, confidence))
                }
            } else {
                // Silêncio ou baixa confiança — emite null apenas na transição
                // (evita spam de nulls a cada frame sem sinal).
                if (lastEmittedFrequency != FREQUENCY_IDLE) {
                    lastEmittedFrequency = FREQUENCY_IDLE
                    trySend(null)
                }
            }
        }
        awaitClose { pitchCallback = null }
    }

    // ── IPitchDetector ────────────────────────────────────────────────────────

    /**
     * Inicializa o [AudioRecord] e inicia a thread de captura.
     *
     * É uma `suspend fun` para que o chamador (ViewModel via use case) possa
     * executá-la em `Dispatchers.Default` sem bloquear a Main thread durante a
     * abertura do stream de áudio.
     *
     * @throws IllegalStateException se já estiver ativo, se o hardware não suportar
     *   a configuração ou se a permissão RECORD_AUDIO estiver ausente.
     */
    override suspend fun startDetection(config: TunerConfig) {
        check(!isRunning.get()) { "AudioCaptureEngine já em execução. Chame stopDetection() antes." }
        currentConfig          = config
        lastEmittedFrequency   = FREQUENCY_IDLE

        val bufferSize = config.bufferSize

        // Aloca buffers ANTES de iniciar o AudioRecord.
        // Evitar alocação depois que o stream está ativo reduz o risco de pausa de GC
        // no momento em que o hardware já está entregando amostras.
        captureBuffer = ShortArray(bufferSize)
        floatBuffer   = FloatArray(bufferSize)
        yinBuffer     = FloatArray(bufferSize / 2)  // YIN analisa lags de 1 até bufferSize/2

        val minBufferBytes = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBufferBytes > 0) {
            "AudioRecord.getMinBufferSize() retornou erro ($minBufferBytes). " +
            "Sample rate ${config.sampleRate} Hz pode não ser suportado."
        }

        // Buffer interno >= max(4× mínimo, 2× frame YIN em bytes).
        // O factor 4× absorve jitter de scheduling sem perder amostras.
        // O factor 2× garante espaço para um frame completo enquanto processamos o anterior.
        val audioRecordInternalBuffer = maxOf(
            minBufferBytes * 4,
            bufferSize * Short.SIZE_BYTES * 2
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,  // (1) desliga AGC + noise suppression
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            audioRecordInternalBuffer
        )

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            record.release()
            "AudioRecord falhou ao inicializar. Verifique a permissão RECORD_AUDIO."
        }

        audioRecord = record
        isRunning.set(true)
        record.startRecording()

        captureThread = Thread(
            { captureLoop(config.sampleRate) },
            CAPTURE_THREAD_NAME
        ).apply {
            // isDaemon = true: o processo pode encerrar mesmo se esta thread ainda estiver
            // rodando — evita vazamento de thread ao destruir o ViewModel sem chamar release().
            isDaemon = true
            start()
        }
    }

    /**
     * Para a captura com graceful shutdown.
     *
     * 1. Sinaliza a thread via [isRunning] = false.
     * 2. Aguarda até [STOP_TIMEOUT_MS] para que o loop encerre sozinho.
     * 3. O `AudioRecord.stop/release` acontece dentro da capture thread, após o loop.
     *    Isso evita chamar `AudioRecord.stop()` de fora enquanto um `read()` bloqueante
     *    está em andamento — o que causaria `IllegalStateException` em algumas versões.
     */
    override suspend fun stopDetection() {
        if (!isRunning.getAndSet(false)) return  // Já parado — idempotente
        lastEmittedFrequency = FREQUENCY_IDLE
        captureThread?.join(STOP_TIMEOUT_MS)
        captureThread = null
    }

    override fun isActive(): Boolean = isRunning.get()

    /**
     * Para o engine e libera todos os recursos permanentemente.
     * Deve ser chamado em `ViewModel.onCleared()`.
     * Após [release], este objeto não pode ser reutilizado.
     */
    override fun release() {
        isRunning.set(false)
        // release() pode ser chamado de qualquer thread. Para não depender que
        // captureLoop() encerre naturalmente, forçamos o release do AudioRecord aqui.
        // captureLoop() trata audioRecord == null como sinal de encerramento.
        audioRecord?.apply {
            // try/catch direto: runCatching { } aloca um objeto Result em cada chamada.
            // No shutdown path não é crítico, mas é desnecessário quando o tratamento
            // de erro é ignorar a exceção silenciosamente.
            try { stop() } catch (_: Exception) { }
            release()
        }
        audioRecord      = null
        captureThread    = null
    }

    // ── Loop de captura ───────────────────────────────────────────────────────

    /**
     * Corpo da thread de captura. Executado em [captureThread].
     *
     * A thread coloca-se em sleep dentro de `record.read()` até que
     * [captureBuffer].size amostras estejam prontas, acorda, processa e volta a dormir.
     * Isso garante que a CPU fique idle entre frames — sem busy-waiting.
     */
    private fun captureLoop(sampleRate: Int) {
        // Define a prioridade de scheduling no nível do OS (Linux nice value ≈ -16).
        // Thread.priority do Java mapeia para nice values altos (baixa prioridade relativa);
        // Process.setThreadPriority opera diretamente no scheduler do kernel.
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val record = audioRecord ?: return

        // Referências locais — cada acesso a campo @Volatile é uma leitura de memória com
        // barreira. Guardamo-las em locals uma vez para reutilizar nas N iterações do loop.
        val pcm    = captureBuffer
        val floats = floatBuffer
        val yin    = yinBuffer
        val sr     = sampleRate
        // Cache de pcm.size como val local: embora `pcm` já seja local (sem leitura @Volatile),
        // guardar o tamanho evita o field read `ShortArray.length` em cada iteração e
        // documenta explicitamente que o tamanho é imutável durante o loop.
        val pcmSize = pcm.size

        while (isRunning.get()) {
            // read() bloqueante: a thread dorme até pcmSize samples estarem disponíveis.
            // READ_BLOCKING é o padrão; explicitado aqui para clareza de intenção.
            val samplesRead = record.read(pcm, 0, pcmSize, AudioRecord.READ_BLOCKING)

            // `if` direto em vez de `when {}` sem argumento: semânticamente equivalente
            // (ambos compilam para if/else chain), mas `if` deixa o fluxo mais explícito.
            if (samplesRead <= 0) {
                // ERROR (-1): falha de hardware transiente — continua tentando.
                // Se isRunning foi setado false durante o read() bloqueante, encerra.
                if (!isRunning.get()) break
                continue
                // Leitura parcial (samplesRead in 1 until pcmSize): improvável com
                // READ_BLOCKING, mas aceita — detectPitchAndNotify usa `samplesRead`
                // como length e não lê além desse índice.
            }

            // ── Normalização: SHORT [-32768..32767] → FLOAT [-1.0..1.0] ──────
            //
            // 32768f é uma constante de compile-time. O JIT do Android (ART) converte
            // divisão por constante em multiplicação por reciprocal, que é 2–4× mais
            // rápido em hardware ARM/x86 sem FPU division.
            for (i in 0 until samplesRead) {
                floats[i] = pcm[i] / 32768f
            }

            // ── Detecção de pitch (YIN) ────────────────────────────────────
            detectPitchAndNotify(floats, samplesRead, sr, yin)
        }

        // Encerramento limpo dentro da própria thread — sem race condition com stopDetection().
        // try/catch direto: runCatching { } cria um objeto Result mesmo no caminho feliz.
        try { record.stop() } catch (_: Exception) { }
        record.release()
        if (audioRecord === record) audioRecord = null
    }

    // ── Algoritmo YIN ─────────────────────────────────────────────────────────

    /**
     * Executa o pipeline YIN completo e invoca [pitchCallback] com o resultado.
     *
     * Passos:
     * 1. Função de diferença
     * 2. Diferença normalizada pela média cumulativa (CMND)
     * 3. Limiar absoluto com ajuste ao mínimo local
     * 4. Interpolação parabólica para precisão sub-sample
     *
     * **Zero alocação**: todos os arrays são pré-alocados. Nenhum objeto é criado
     * durante a execução — garantido pela ausência de `new`, boxing, lambdas
     * com captura ou coleções temporárias.
     *
     * @param samples   Amostras normalizadas [-1.0, 1.0]. Deve ter tamanho >= [length].
     * @param length    Número de amostras válidas em [samples] (pode ser < samples.size).
     * @param sampleRate Taxa de amostragem em Hz.
     * @param yinBuf    Buffer de trabalho pré-alocado, tamanho >= [length] / 2.
     */
    private fun detectPitchAndNotify(
        samples: FloatArray,
        length: Int,
        sampleRate: Int,
        yinBuf: FloatArray
    ) {
        val halfLen = length / 2

        // ── Passo 1 — Função de diferença ─────────────────────────────────
        //
        // d(τ) = Σ(j=0..W-1) [x(j) − x(j+τ)]²
        //
        // Para um sinal puramente periódico com período P, d(P) = 0.
        // Portanto o pitch corresponde ao primeiro mínimo de d(τ) próximo a zero.
        //
        // Complexidade: O(W/2 × W) ≈ 8,4M ops para W=4096.
        // A taxa de análise é ~sampleRate/bufferSize ≈ 11,7 Hz → 85ms entre frames.
        // Otimização futura: substituir pela versão FFT em O(W log W) se necessário.
        //
        yinBuf[0] = 0f
        for (tau in 1 until halfLen) {
            var sum = 0f
            // O inner loop é o gargalo. O JIT do ART vetoriza loops simples
            // com operações aritméticas float se não houver dependências de dados.
            for (j in 0 until halfLen) {
                val delta = samples[j] - samples[j + tau]
                sum += delta * delta
            }
            yinBuf[tau] = sum
        }

        // ── Passo 2 — Diferença normalizada pela média cumulativa (CMND) ──
        //
        // d'(0) = 1 (por convenção)
        // d'(τ) = d(τ) × τ / Σ(j=1..τ) d(j)
        //
        // Divide a função de diferença pela sua própria média acumulada.
        // Isso torna o limiar absoluto (Passo 3) independente da amplitude do sinal:
        // um sinal forte e um fraco com o mesmo pitch produzem valores d' semelhantes.
        //
        yinBuf[0] = 1f
        var runningSum = 0f
        for (tau in 1 until halfLen) {
            runningSum += yinBuf[tau]
            // Guard: se runningSum == 0f, todos os deltas anteriores foram zero
            // (sinal DC puro). yinBuf[tau] = 1f sinaliza "sem periodicidade".
            yinBuf[tau] = if (runningSum > 0f) yinBuf[tau] * tau / runningSum else 1f
        }

        // ── Passo 3 — Limiar absoluto + ajuste ao mínimo local ────────────
        //
        // Encontra o primeiro τ ≥ TAU_MIN onde d'(τ) < threshold.
        // "Ajuste ao mínimo local": após encontrar o primeiro cruzamento,
        // desliza τ para frente enquanto d'(τ+1) < d'(τ) para chegar ao
        // mínimo local mais próximo. Reduz erros de estimativa de pitch.
        //
        var tauEstimate = -1
        var tau = TAU_MIN
        while (tau < halfLen) {
            if (yinBuf[tau] < AudioEngineConfig.YIN_CONFIDENCE_THRESHOLD) {
                while (tau + 1 < halfLen && yinBuf[tau + 1] < yinBuf[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }

        if (tauEstimate == -1) {
            // Nenhum cruzamento de limiar encontrado — sinal não periódico.
            // Pode ser: silêncio, ruído de banda larga, sopro, etc.
            pitchCallback?.onPitch(0f, 0f)
            return
        }

        // ── Passo 4 — Interpolação parabólica ─────────────────────────────
        //
        // O mínimo de d'(τ) raramente cai exatamente em um índice inteiro.
        // Aproximamos o ponto de mínimo real por uma parábola pelos 3 pontos
        // ao redor de [tauEstimate]:
        //
        //                       s₀ − s₂
        //   betterTau = τ + ─────────────────────
        //                   2 × (2s₁ − s₀ − s₂)
        //
        // onde s₀ = d'(τ−1), s₁ = d'(τ), s₂ = d'(τ+1).
        // Essa refinamento reduz o erro de estimativa de pitch para < 0.1 cent.
        //
        val betterTau: Float = if (tauEstimate in 1 until halfLen - 1) {
            val s0 = yinBuf[tauEstimate - 1]
            val s1 = yinBuf[tauEstimate]
            val s2 = yinBuf[tauEstimate + 1]
            val denom = 2f * (2f * s1 - s0 - s2)
            // denom ≈ 0 significa parábola degenerada (mínimo muito plano) — usa inteiro.
            if (denom != 0f) tauEstimate + (s0 - s2) / denom else tauEstimate.toFloat()
        } else {
            tauEstimate.toFloat()
        }

        if (betterTau <= 0f) {
            pitchCallback?.onPitch(0f, 0f)
            return
        }

        // Frequência fundamental: f₀ = sampleRate / betterTau
        val frequency = sampleRate / betterTau

        // Confiança: quanto mais próximo de 0 for d'(tauEstimate), mais periódico
        // é o sinal. Como threshold ≈ 0.15, confidence estará tipicamente em [0.85, 1.0].
        val confidence = (1f - yinBuf[tauEstimate]).coerceIn(0f, 1f)

        pitchCallback?.onPitch(frequency, confidence)
    }

    // ── Constantes ────────────────────────────────────────────────────────────

    /**
     * Interface funcional com assinatura de método primitiva.
     *
     * Diferença crucial em relação a `(Float, Float) -> Unit`:
     * - Tipo de função Kotlin → JVM `Function2<Float, Float, Unit>` → parâmetros **boxados**
     *   (java.lang.Float). Cada `.invoke()` aloca 2 objetos Float no heap.
     * - `fun interface` com parâmetros `Float` → JVM `interface { void onPitch(float, float) }`,
     *   chamada com `float` primitivo. **Zero alocação por chamada.**
     *
     * Esse ganho é especialmente relevante aqui porque [onPitch] é chamado a cada frame
     * YIN (~12 Hz durante detecção ativa), totalizando ~24 objetos Float/s eliminados.
     */
    private fun interface PitchDataCallback {
        fun onPitch(frequencyHz: Float, confidence: Float)
    }

    companion object {
        /**
         * Variação mínima de frequência (Hz) para emitir um novo [PitchResult].
         * 0.8 Hz ≈ 3 cents em torno de A4 (440 Hz). Reduz flutter de detecção
         * mantendo responsividade para mudanças reais de pitch.
         */
        private const val FREQUENCY_CHANGE_THRESHOLD = 0.8f

        /**
         * Sentinela para [lastEmittedFrequency] indicando "nenhum pitch emitido ainda".
         * -1f é fora da faixa musical válida (≥ 20 Hz), portanto nunca colide
         * com uma frequência real.
         */
        private const val FREQUENCY_IDLE = -1f

        /**
         * Lag mínimo analisado pelo YIN.
         * τ = 1 corresponderia a f = sampleRate / 1 = sampleRate Hz (acima de Nyquist).
         * τ = 2 é o menor lag fisicamente significativo.
         * Adicionalmente, frequências > sampleRate/TAU_MIN são filtradas por
         * [FrequencyUtils.isInMusicalRange].
         */
        private const val TAU_MIN = 2

        /**
         * Tempo máximo aguardado para a capture thread encerrar graciosamente.
         * O thread dorme dentro de `AudioRecord.read()` por no máximo
         * bufferSize / sampleRate segundos (ex: 4096/48000 ≈ 85ms).
         * 500ms é 5× esse valor — margem ampla mesmo em dispositivos lentos.
         */
        private const val STOP_TIMEOUT_MS = 500L

        private const val CAPTURE_THREAD_NAME = "PitchCapture"
    }
}
