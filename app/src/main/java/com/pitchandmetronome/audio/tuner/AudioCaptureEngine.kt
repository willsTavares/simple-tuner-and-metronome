package com.pitchandmetronome.audio.tuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.pitchandmetronome.core.utils.FrequencyUtils
import com.pitchandmetronome.domain.model.tuner.PitchResult
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import com.pitchandmetronome.domain.tuner.PitchStabilizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

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
 * `THREAD_PRIORITY_AUDIO` recebe slots de CPU mais frequentes e previsíveis.
 *
 * ### 3. Buffers pré-alocados — zero alocação no hot loop
 * Todos os arrays são alocados **uma vez** em [startDetection] e reutilizados em
 * cada iteração — sem pausas de GC no caminho crítico de áudio.
 *
 * ### 4. Janela deslizante com 50% de sobreposição
 * Cada `read()` traz meia janela; a análise roda sobre a janela completa. Dobra a
 * taxa de análise (~23 Hz para 4096 @ 48 kHz) sem perder resolução em graves.
 *
 * ### 5. YIN + estabilização
 * O [YinPitchDetector] produz uma estimativa bruta por frame; o [PitchStabilizer] (mediana,
 * confirmação de salto, EMA adaptativa, hold de silêncio) transforma essa
 * sequência em um valor estável para a UI. Toda a suavização vive ali — a UI e o
 * ViewModel recebem um valor já pronto para exibir.
 *
 * ### 6. `callbackFlow` conflado como bridge Thread → Flow
 * A thread de captura nunca bloqueia; se o coletor atrasar, só o resultado mais
 * recente importa.
 *
 * ---
 * ## Ciclo de Vida
 * ```
 * startDetection(config)
 *   → AudioRecord iniciado
 *   → Thread de captura inicia (THREAD_PRIORITY_AUDIO)
 *     → loop: read() → janela → YIN → estabilizador → pitchCallback → Flow
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
    // captureBuffer : PCM bruto de cada leitura (hop = bufferSize / 2).
    // floatBuffer   : janela deslizante normalizada [-1.0, 1.0] (bufferSize).
    // yinDetector   : YIN com seus próprios buffers de trabalho.
    //
    @Volatile private var captureBuffer = ShortArray(0)
    @Volatile private var floatBuffer   = FloatArray(0)
    @Volatile private var yinDetector: YinPitchDetector? = null

    @Volatile private var currentConfig = TunerConfig()

    // Instalado quando o Flow tem um coletor ativo. Chamado da capture thread.
    // `fun interface` com parâmetros primitivos evita boxing de Float por frame.
    @Volatile private var pitchCallback: PitchDataCallback? = null

    // ── Flow público ─────────────────────────────────────────────────────────

    /**
     * Resultados já estabilizados, um por frame de análise com pitch (~23 Hz).
     * Emite `null` uma vez na transição para silêncio (após o hold do estabilizador).
     */
    override val pitchFlow: Flow<PitchResult?> = callbackFlow {
        var voicedEmitted = false
        pitchCallback = PitchDataCallback { frequencyHz, confidence ->
            if (frequencyHz > 0f && FrequencyUtils.isInMusicalRange(frequencyHz)) {
                voicedEmitted = true
                val note = FrequencyUtils.frequencyToNote(frequencyHz, currentConfig.referenceA4)
                trySend(PitchResult(frequencyHz, note, confidence))
            } else if (voicedEmitted) {
                voicedEmitted = false
                trySend(null)
            }
        }
        awaitClose { pitchCallback = null }
    }.buffer(Channel.CONFLATED)

    // ── IPitchDetector ────────────────────────────────────────────────────────

    /**
     * Inicializa o [AudioRecord] e inicia a thread de captura.
     *
     * @throws IllegalStateException se já estiver ativo, se o hardware não suportar
     *   a configuração ou se a permissão RECORD_AUDIO estiver ausente.
     */
    override suspend fun startDetection(config: TunerConfig) {
        check(!isRunning.get()) { "AudioCaptureEngine já em execução. Chame stopDetection() antes." }
        currentConfig = config

        val bufferSize = config.bufferSize

        // Aloca buffers ANTES de iniciar o AudioRecord — sem GC com o stream ativo.
        captureBuffer = ShortArray(bufferSize / 2)
        floatBuffer   = FloatArray(bufferSize)
        yinDetector   = YinPitchDetector(bufferSize, config.sampleRate)

        val minBufferBytes = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBufferBytes > 0) {
            "AudioRecord.getMinBufferSize() retornou erro ($minBufferBytes). " +
            "Sample rate ${config.sampleRate} Hz pode não ser suportado."
        }

        // Buffer interno >= max(4× mínimo, 2× janela): absorve jitter de scheduling
        // sem perder amostras.
        val audioRecordInternalBuffer = maxOf(
            minBufferBytes * 4,
            bufferSize * Short.SIZE_BYTES * 2
        )

        // A UI só chama startDetection com RECORD_AUDIO concedida, mas a permissão
        // pode ser revogada a qualquer momento — SecurityException vira erro tratável.
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,  // desliga AGC + noise suppression
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioRecordInternalBuffer
            )
        } catch (e: SecurityException) {
            throw IllegalStateException("Permissão RECORD_AUDIO ausente.", e)
        }

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
            isDaemon = true
            start()
        }
    }

    /**
     * Para a captura com graceful shutdown. O `AudioRecord.stop/release` acontece
     * dentro da capture thread, após o loop — evita chamar `stop()` de fora durante
     * um `read()` bloqueante.
     */
    override suspend fun stopDetection() {
        if (!isRunning.getAndSet(false)) return  // Já parado — idempotente
        captureThread?.join(STOP_TIMEOUT_MS)
        captureThread = null
    }

    /**
     * Atualiza a configuração a quente — sem parar a captura.
     *
     * `referenceA4` é lido a cada frame pelo callback do [pitchFlow], então passa
     * a valer imediatamente. `sampleRate`/`bufferSize` só têm efeito no próximo
     * [startDetection].
     */
    override fun updateConfig(config: TunerConfig) {
        currentConfig = config
    }

    override fun isActive(): Boolean = isRunning.get()

    /**
     * Para o engine e libera todos os recursos permanentemente.
     * Deve ser chamado em `ViewModel.onCleared()`.
     */
    override fun release() {
        isRunning.set(false)
        audioRecord?.apply {
            try { stop() } catch (_: Exception) { }
            release()
        }
        audioRecord   = null
        captureThread = null
    }

    // ── Loop de captura ───────────────────────────────────────────────────────

    private fun captureLoop(sampleRate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val record = audioRecord ?: return
        val yin = yinDetector ?: return

        // Locais: evita leitura @Volatile a cada iteração.
        val pcm        = captureBuffer
        val window     = floatBuffer
        val hop        = pcm.size
        val windowSize = window.size

        // Criado na própria thread de captura — único usuário, sem concorrência.
        val holdFrames = ceil(SILENCE_HOLD_MS / 1000f * sampleRate / hop).toInt()
        val stabilizer = PitchStabilizer(holdFrames)
        var lastVoicedConfidence = 0f

        // A análise só começa quando a janela enche — evita analisar zeros.
        var filled = 0

        while (isRunning.get()) {
            val samplesRead = record.read(pcm, 0, hop, AudioRecord.READ_BLOCKING)

            if (samplesRead <= 0) {
                if (!isRunning.get()) break
                continue
            }

            // Janela deslizante: desloca o conteúdo antigo e anexa o novo no fim.
            System.arraycopy(window, samplesRead, window, 0, windowSize - samplesRead)
            val base = windowSize - samplesRead
            for (i in 0 until samplesRead) {
                window[base + i] = pcm[i] / 32768f
            }

            filled = (filled + samplesRead).coerceAtMost(windowSize)
            if (filled < windowSize) continue

            val rawHz = yin.detect(window)
            if (rawHz > 0f) lastVoicedConfidence = yin.confidence
            val stableHz = stabilizer.process(rawHz)
            pitchCallback?.onPitch(stableHz, lastVoicedConfidence)
        }

        try { record.stop() } catch (_: Exception) { }
        record.release()
        if (audioRecord === record) audioRecord = null
    }

    // ── Tipos e constantes ───────────────────────────────────────────────────

    /**
     * Callback com assinatura primitiva (`void onPitch(float, float)` na JVM) —
     * evita o boxing de Float que `(Float, Float) -> Unit` causaria a cada frame.
     */
    private fun interface PitchDataCallback {
        fun onPitch(frequencyHz: Float, confidence: Float)
    }

    companion object {
        /**
         * Tempo que o último pitch continua exibido em frames sem sinal —
         * cobre o decaimento de uma corda e pausas curtas sem piscar a tela.
         */
        private const val SILENCE_HOLD_MS = 350f

        /** Tempo máximo aguardado para a capture thread encerrar. */
        private const val STOP_TIMEOUT_MS = 500L

        private const val CAPTURE_THREAD_NAME = "PitchCapture"
    }
}
