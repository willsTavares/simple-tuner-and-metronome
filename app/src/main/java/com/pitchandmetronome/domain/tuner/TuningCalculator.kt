package com.pitchandmetronome.domain.tuner

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Aritmética musical de cents: desvio de afinação, distância entre frequências
 * e status de afinação.
 *
 * Todas as funções são **puras** — sem estado, sem efeitos colaterais, sem
 * alocação de memória no caminho feliz (exceto [calculate], que constrói
 * um [TuningResult]).
 *
 * ---
 *
 * # A fórmula dos cents — decisão matemática central
 *
 * ## Definição
 *
 *   `cents(f, fRef) = 1200 × log₂(f / fRef)`
 *
 * Um oitava = 1200 cents; um semitom (no temperamento igual) = 100 cents.
 *
 * ## Por que log₂ e não log₁₀ ou ln?
 *
 * O ouvido humano percebe **razões** de frequência, não diferenças absolutas (Hz).
 * A escala logarítmica captura isso: dobrar a frequência (uma oitava) sempre
 * produz o mesmo número de cents independentemente de qual oitava seja.
 * Log₂ é escolhido porque `log₂(2) = 1`, tornando o fator de 1200 exato para
 * a oitava inteira: `1200 × log₂(2) = 1200 cents = 1 oitava`. ✓
 *
 * Se usássemos ln: `1200 × ln(2) / ln(2) = 1200` — mesma resposta, mas com
 * uma divisão desnecessária que não adiciona clareza semântica.
 *
 * ## Desvio do semitom mais próximo
 *
 * Para saber se uma nota está "muito aguda" ou "muito grave" em relação à nota
 * mais próxima, o desvio é calculado em dois passos:
 *
 * 1. Semitons desde A4 (real, não inteiro):
 *      `semitonesFromA4 = 12 × log₂(f / A4)`
 *
 * 2. Arredondar para o inteiro mais próximo `n`, depois:
 *
 *   **Via fórmula direta** (mais preciso para testes):
 *      `centsDeviation = 1200 × log₂(f / fRef)`
 *      onde `fRef = A4 × 2^(n/12)` é a frequência exata da nota mais próxima.
 *
 *   **Via equivalência algébrica** (mais eficiente, sem `pow`):
 *      `centsDeviation = (semitonesFromA4 − n) × 100`
 *
 *   Ambas são matematicamente equivalentes. Este objeto usa a segunda forma
 *   internamente (apenas uma log2 por chamada), mas expõe a primeira via
 *   [centsFromReference] para que o chamador possa usar frequências de
 *   referência de temperamentos não-iguais.
 *
 * ## Precisão: Float vs Double
 *
 * A4@Float = 440.0f. log2(440.0f) ≈ 8.78136 — arredondamento de Float
 * acumula ≈ 0.3–0.5 cents de erro. Em Double, o erro fica abaixo de 0.001
 * cents. As funções públicas aceitam Float por conveniência da chamada, mas
 * toda a aritmética ocorre em Double, e o resultado retorna como Float.
 *
 * ---
 *
 * # Estrutura para upgrade
 *
 * A separação entre [centsFromReference] (fórmula pura) e [calculate]
 * (resultado completo) permite que futuras versões substituam o
 * `referenceA4Hz` padrão por valores do [NoteMapper] sem alterar esta classe.
 *
 * Para integrar com [NoteMapper]:
 * ```kotlin
 * val mapper  = NoteMapper(referenceA4Hz = 432.0, tuningSystem = PYTHAGOREAN)
 * val mapping = mapper.map(detectedHz)
 * val result  = TuningCalculator.calculate(
 *     detectedHz    = detectedHz,
 *     referenceHz   = mapping.exactReferenceHz   // usa referência do temperamento
 * )
 * ```
 */
object TuningCalculator {

    // ── Tipos públicos ────────────────────────────────────────────────────────

    /**
     * Resultado completo de uma análise de afinação.
     *
     * @param detectedHz Frequência detectada em Hz.
     * @param referenceHz Frequência de referência usada no cálculo (nota mais próxima).
     * @param centsTotal Distância total em cents de [detectedHz] até [referenceHz].
     *   `1200 × log₂(detected / reference)`. Faixa normal: (−50, +50].
     * @param deviationCents Mesmo valor que [centsTotal] quando [referenceHz] é a
     *   frequência da nota mais próxima. Separado para clareza semântica: é explicitamente
     *   "o quanto esta frequência está desafinada da nota alvo".
     * @param tuneStatus Classificação de afinação baseada em [deviationCents] e
     *   [toleranceCents].
     * @param toleranceCents Tolerância usada para calcular [tuneStatus].
     */
    data class TuningResult(
        val detectedHz: Double,
        val referenceHz: Double,
        val centsTotal: Double,
        val deviationCents: Double,
        val tuneStatus: TuneStatus,
        val toleranceCents: Double
    )

    /**
     * Status de afinação qualitativo.
     *
     * Derivado de [deviationCents] comparado à [TuningResult.toleranceCents].
     *
     * - [IN_TUNE]: `|deviation| ≤ tolerance`
     * - [SHARP]:   `deviation > tolerance`  (dedilhada/embocadura muito aguda)
     * - [FLAT]:    `deviation < −tolerance` (dedilhada/embocadura muito grave)
     * - [TOO_FAR]: `|deviation| > 50 cents` — mais de meio semitom; a nota
     *   identificada pode estar errada; o afinador deve aguardar nova detecção.
     */
    enum class TuneStatus { IN_TUNE, SHARP, FLAT, TOO_FAR }

    // ── Constantes ────────────────────────────────────────────────────────────

    /**
     * Tolerância padrão de afinação em cents.
     *
     * ±5 cents é o limiar prático para "afinação aceitável": abaixo disso, a
     * maioria dos ouvintes não percebe o desvio em contexto musical. Afinadores
     * profissionais costumam usar ±3 cents (violinistas, cantores de coro).
     */
    const val DEFAULT_TOLERANCE_CENTS = 5.0

    /**
     * Desvio máximo considerado "na mesma nota" (meio semitom).
     *
     * Acima de 50 cents, a frequência está mais próxima da nota adjacente do
     * que da nota identificada — indica detecção instável ou sinal transitório.
     */
    const val MAX_DEVIATION_CENTS = 50.0

    // ── API pública — funções puras ───────────────────────────────────────────

    /**
     * Implementação direta da fórmula de cents:
     *
     *   `cents = 1200 × log₂(detectedHz / referenceHz)`
     *
     * Esta é a função mais primitiva — todas as outras são derivadas desta.
     *
     * Retorna 0.0 se as frequências forem iguais.
     * Retorna um número negativo se [detectedHz] < [referenceHz] (flat).
     * Retorna um número positivo se [detectedHz] > [referenceHz] (sharp).
     *
     * @param detectedHz Frequência medida (Hz). Deve ser > 0.
     * @param referenceHz Frequência de referência (Hz). Deve ser > 0.
     *   Pode ser a frequência de uma nota específica gerada por [NoteMapper.referenceHzFor].
     */
    fun centsFromReference(detectedHz: Double, referenceHz: Double): Double {
        require(detectedHz > 0.0)  { "detectedHz must be > 0, was $detectedHz" }
        require(referenceHz > 0.0) { "referenceHz must be > 0, was $referenceHz" }
        return 1200.0 * log2(detectedHz / referenceHz)
    }

    /** Sobrecarga Float — converte para Double e delega. */
    fun centsFromReference(detectedHz: Float, referenceHz: Float): Float =
        centsFromReference(detectedHz.toDouble(), referenceHz.toDouble()).toFloat()

    /**
     * Calcula o desvio em cents em relação ao **semitom mais próximo**.
     *
     * Equivalente a `centsFromReference(detected, nearestNoteHz)`, mas
     * sem precisar construir um [NoteMapper] — útil quando o único interesse
     * é o desvio numérico, não a identidade da nota.
     *
     * ## Implementação
     *
     * Usa a equivalência algébrica `desvio = (x − round(x)) × 100` em vez
     * de `1200 × log₂(f/fRef)` porque evita a chamada `pow()` para reconstruir
     * `fRef`, mantendo o custo em apenas uma `log2`.
     *
     * Prova de equivalência:
     *   `x = 12 × log₂(f/A4)`,  `n = round(x)`
     *   `fRef = A4 × 2^(n/12)`
     *   `1200 × log₂(f/fRef) = 1200 × log₂(f/A4 / 2^(n/12))`
     *                        `= 1200 × (log₂(f/A4) − n/12)`
     *                        `= 1200 × (x/12 − n/12)`
     *                        `= (x − n) × 100` ✓
     *
     * @param detectedHz Frequência detectada em Hz. Deve ser > 0.
     * @param referenceA4Hz Frequência de A4 de referência (padrão: 440 Hz).
     * @return Desvio em cents na faixa (−50, +50]. Positivo = sharp, negativo = flat.
     */
    fun deviationCents(
        detectedHz: Double,
        referenceA4Hz: Double = 440.0
    ): Double {
        require(detectedHz > 0.0) { "detectedHz must be > 0, was $detectedHz" }
        val semitonesFromA4 = 12.0 * log2(detectedHz / referenceA4Hz)
        val n = semitonesFromA4.roundToInt()
        return (semitonesFromA4 - n) * 100.0
    }

    /** Sobrecarga Float. */
    fun deviationCents(detectedHz: Float, referenceA4Hz: Float = 440f): Float =
        deviationCents(detectedHz.toDouble(), referenceA4Hz.toDouble()).toFloat()

    /**
     * Distância total em cents de [detectedHz] até A4 de referência.
     *
     * Diferente de [deviationCents]: **não** arrredonda ao semitom mais próximo.
     * Útil para visualizar onde exatamente uma frequência recai no espaço
     * cromático (e.g., em uma linha de cents de −600 a +600).
     *
     *   `centsFromA4 = 1200 × log₂(detected / A4) = semitonesFromA4 × 100`
     *
     * @return Positivo se acima de A4, negativo se abaixo.
     *   A5 retorna +1200; G#4 retorna −100; A3 retorna −1200.
     */
    fun centsFromA4(
        detectedHz: Double,
        referenceA4Hz: Double = 440.0
    ): Double {
        require(detectedHz > 0.0) { "detectedHz must be > 0, was $detectedHz" }
        return 1200.0 * log2(detectedHz / referenceA4Hz)
    }

    /** Sobrecarga Float. */
    fun centsFromA4(detectedHz: Float, referenceA4Hz: Float = 440f): Float =
        centsFromA4(detectedHz.toDouble(), referenceA4Hz.toDouble()).toFloat()

    /**
     * Classifica o desvio de afinação como [TuneStatus].
     *
     * Ordem de verificação:
     * 1. `|deviation| > 50` → [TuneStatus.TOO_FAR] (nota errada ou transitória)
     * 2. `|deviation| ≤ tolerance` → [TuneStatus.IN_TUNE]
     * 3. `deviation > 0` → [TuneStatus.SHARP]
     * 4. else → [TuneStatus.FLAT]
     *
     * @param deviationCents Saída de [deviationCents] ou [centsFromReference].
     * @param toleranceCents Limiar de afinação aceitável. Default: [DEFAULT_TOLERANCE_CENTS].
     */
    fun tuneStatus(
        deviationCents: Double,
        toleranceCents: Double = DEFAULT_TOLERANCE_CENTS
    ): TuneStatus {
        if (abs(deviationCents) > MAX_DEVIATION_CENTS) return TuneStatus.TOO_FAR
        if (abs(deviationCents) <= toleranceCents)     return TuneStatus.IN_TUNE
        return if (deviationCents > 0.0) TuneStatus.SHARP else TuneStatus.FLAT
    }

    /** Sobrecarga Float. */
    fun tuneStatus(deviationCents: Float, toleranceCents: Float = DEFAULT_TOLERANCE_CENTS.toFloat()): TuneStatus =
        tuneStatus(deviationCents.toDouble(), toleranceCents.toDouble())

    /**
     * Calcula a frequência exata de uma nota em **temperamento igual** para um
     * deslocamento de semitons dado.
     *
     * `fRef(n) = A4 × 2^(n/12)`
     *
     * Para temperamentos não-iguais, use [NoteMapper.referenceHzFor] diretamente.
     *
     * @param semitoneOffsetFromA4 Semitons em relação a A4.
     *   A4=0, A#4/Bb4=+1, G#4/Ab4=−1, A3=−12, A5=+12.
     * @param referenceA4Hz Frequência de A4 de referência (padrão: 440 Hz).
     */
    fun referenceHzFor(
        semitoneOffsetFromA4: Int,
        referenceA4Hz: Double = 440.0
    ): Double = referenceA4Hz * 2.0.pow(semitoneOffsetFromA4 / 12.0)

    /** Sobrecarga Float. */
    fun referenceHzFor(semitoneOffsetFromA4: Int, referenceA4Hz: Float = 440f): Float =
        referenceHzFor(semitoneOffsetFromA4, referenceA4Hz.toDouble()).toFloat()

    /**
     * Cálculo completo de afinação: combina [deviationCents] e [tuneStatus]
     * em um único [TuningResult].
     *
     * **Sobrecarga com `referenceHz` explícito** — use quando o chamador já
     * possui a frequência de referência calculada (e.g., vinda de [NoteMapper]
     * com temperamento não-igual). Evita recalcular a nota mais próxima.
     *
     * ```kotlin
     * // Exemplo com temperamento pitagórico:
     * val mapper  = NoteMapper(referenceA4Hz = 440.0, tuningSystem = PYTHAGOREAN)
     * val mapping = mapper.map(detectedHz)
     * val result  = TuningCalculator.calculate(
     *     detectedHz   = detectedHz,
     *     referenceHz  = mapping.exactReferenceHz
     * )
     * ```
     *
     * @param detectedHz Frequência detectada em Hz.
     * @param referenceHz Frequência de referência da nota alvo em Hz.
     * @param toleranceCents Tolerância de afinação. Default: [DEFAULT_TOLERANCE_CENTS].
     */
    fun calculate(
        detectedHz: Double,
        referenceHz: Double,
        toleranceCents: Double = DEFAULT_TOLERANCE_CENTS
    ): TuningResult {
        val cents = centsFromReference(detectedHz, referenceHz)
        return TuningResult(
            detectedHz     = detectedHz,
            referenceHz    = referenceHz,
            centsTotal     = cents,
            deviationCents = cents,
            tuneStatus     = tuneStatus(cents, toleranceCents),
            toleranceCents = toleranceCents
        )
    }

    /**
     * **Sobrecarga com `referenceA4Hz`** — calcula a referência internamente a
     * partir do semitom mais próximo em **temperamento igual**.
     *
     * Use esta sobrecarga no caminho padrão (igual temperament):
     *
     * ```kotlin
     * val result = TuningCalculator.calculate(detectedHz = 442.5f)
     * // result.tuneStatus == SHARP
     * // result.deviationCents ≈ +9.8f
     * ```
     *
     * Para temperamentos alternativos, obtenha `referenceHz` do [NoteMapper]
     * e use a outra sobrecarga.
     *
     * @param detectedHz Frequência detectada em Hz (Float por conveniência de API).
     * @param referenceA4Hz Frequência de A4 de referência. Default: 440 Hz.
     * @param toleranceCents Tolerância de afinação. Default: [DEFAULT_TOLERANCE_CENTS].
     */
    fun calculate(
        detectedHz: Float,
        referenceA4Hz: Float = 440f,
        toleranceCents: Double = DEFAULT_TOLERANCE_CENTS
    ): TuningResult {
        val detected = detectedHz.toDouble()
        val a4 = referenceA4Hz.toDouble()

        // Recalcula semitom mais próximo e fRef uma só vez — deviationCents usa
        // a forma mais eficiente (sem pow), e fRef é construída só para o resultado.
        val semitonesFromA4 = 12.0 * log2(detected / a4)
        val n = semitonesFromA4.roundToInt()
        val deviation = (semitonesFromA4 - n) * 100.0
        val refHz = a4 * 2.0.pow(n / 12.0)

        return TuningResult(
            detectedHz     = detected,
            referenceHz    = refHz,
            centsTotal     = semitonesFromA4 * 100.0,
            deviationCents = deviation,
            tuneStatus     = tuneStatus(deviation, toleranceCents),
            toleranceCents = toleranceCents
        )
    }

    /**
     * Frequência resultante de transpor [frequencyHz] por [cents] cents.
     *
     *   `f_out = f_in × 2^(cents / 1200)`
     *
     * Inversão da fórmula principal. Útil para gerar frequências de referência
     * alternativas (e.g., um tom de teste transposto do A4 para outra nota).
     *
     * @param frequencyHz Frequência de partida em Hz.
     * @param cents Transposição em cents (positivo = agudo, negativo = grave).
     */
    fun transpose(frequencyHz: Double, cents: Double): Double =
        frequencyHz * 2.0.pow(cents / 1200.0)

    /** Sobrecarga Float. */
    fun transpose(frequencyHz: Float, cents: Float): Float =
        transpose(frequencyHz.toDouble(), cents.toDouble()).toFloat()
}
