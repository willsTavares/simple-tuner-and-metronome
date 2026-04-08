package com.pitchandmetronome.domain.tuner

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Mapeia uma frequência detectada para a nota musical mais próxima,
 * respeitando a frequência de referência A4 e o sistema de temperamento
 * configurados na instância.
 *
 * ---
 *
 * # Decisões matemáticas
 *
 * ## Por que `Double` internamente?
 *
 * A aritmética de cents é baseada em `log2(f / fRef)`. Para A4 = 440 Hz, um
 * semitom cobre aproximadamente 26 Hz — 1 Hz de erro ≈ 3,9 cents em Float.
 * O `Float` de 32 bits tem ~7 dígitos decimais de precisão, o que produz erros
 * de arredondamento de até ±0,5 cents na identificação da nota. Em `Double`
 * (64 bits, ~15 dígitos), o erro fica abaixo de 0,001 cents — imperceptível.
 * A conversão para `Float` acontece apenas na saída, no `NoteMapping`.
 *
 * ## Note identification — fórmula central
 *
 *   `semitonesFromA4 = 12 × log₂(f / A4)`
 *
 * Arredondando para o inteiro mais próximo (`n`), a nota mais próxima é
 * encontrada sem nenhuma tabela de lookup. O número MIDI de A4 é 69, portanto:
 *
 *   `midiNote = 69 + n`
 *   `pitchClass = midiNote mod 12`       → índice 0–11
 *   `octave = midiNote ÷ 12 − 1`         → oitava científica (C4 = oitava 4)
 *
 * ## Frequência de referência por temperamento
 *
 * Para **Temperamento Igual** (padrão):
 *
 *   `fRef(n) = A4 × 2^(n/12)`
 *
 * Para **Pitagórico** e **Justo (5-limit)**, as razões de cada classe de nota
 * em relação à tônica C são pré-definidas como constantes racionais. A
 * frequência de C na mesma oitava de A é calculada invertendo a razão C→A
 * naquele sistema, e então as demais notas são derivadas a partir das razões.
 *
 *   `fC = A4 / ratio(A)`                 → C no mesmo temperamento
 *   `fRef = fC × ratio(pitchClass) × 2^octaveShift`
 *
 * Isso significa que, em temperamentos não-iguais, fixar A4 = 440 Hz produz
 * frequências ligeiramente diferentes das notas adjacentes em relação ao
 * temperamento igual — comportamento matematicamente correto e musical mente
 * esperado em afinações históricas.
 *
 * @param referenceA4Hz Frequência de referência para A4 em Hz (padrão: 440.0).
 * @param tuningSystem Sistema de temperamento. Ver [TuningSystem].
 */
class NoteMapper(
    val referenceA4Hz: Double = 440.0,
    val tuningSystem: TuningSystem = TuningSystem.EQUAL_TEMPERAMENT
) {

    // ── Tipo público: nota identificada ──────────────────────────────────────

    /**
     * Resultado da identificação de nota — separado de `cents` intencionalmente.
     *
     * A separação de responsabilidades entre "qual é esta nota?" e "quão afinada
     * está?" é o que permite testar cada parte de forma independente e reutilizar
     * [NoteMapping] sem provocar cálculo de cents desnecessário.
     *
     * @param pitchClass Classe de altura (C, C#, D, …) sem informação de oitava.
     * @param octave Oitava científica (A4 está na oitava 4; C4 = Middle C).
     * @param semitoneOffsetFromA4 Número de semitons de distância de A4 (sinal incluso).
     *   A4 = 0, A#4 = +1, G#4 = −1, A5 = +12.
     * @param exactReferenceHz Frequência exata de referência para esta nota no
     *   sistema de temperamento configurado, em Hz (Double — mantém precisão máxima).
     */
    data class NoteMapping(
        val pitchClass: PitchClass,
        val octave: Int,
        val semitoneOffsetFromA4: Int,
        val exactReferenceHz: Double
    ) {
        /** Representação compacta: "A4", "C#3", "Bb5". */
        val fullName: String get() = "${pitchClass.label}$octave"
    }

    // ── Enumerações de domínio ────────────────────────────────────────────────

    /**
     * As 12 classes de altura do sistema cromático ocidental.
     *
     * Convenção: sustenidos (#) para as teclas pretas, conforme padrão MIDI.
     * [semitone] é o índice 0–11 dentro de uma oitava, onde C = 0.
     */
    enum class PitchClass(val label: String, val semitone: Int) {
        C("C",   0),
        C_SHARP("C#", 1),
        D("D",   2),
        D_SHARP("D#", 3),
        E("E",   4),
        F("F",   5),
        F_SHARP("F#", 6),
        G("G",   7),
        G_SHARP("G#", 8),
        A("A",   9),
        A_SHARP("A#", 10),
        B("B",   11);

        companion object {
            /** Retorna a [PitchClass] dado um índice de semitom mod 12. */
            fun fromSemitoneIndex(index: Int): PitchClass =
                entries[(index % 12 + 12) % 12]
        }
    }

    /**
     * Sistema de temperamento musical. Define como as frequências das 12 notas
     * dentro de uma oitava se relacionam entre si.
     *
     * A escolha do temperamento afeta a frequência de referência calculada
     * para cada nota — e portanto o desvio em cents reportado pelo
     * [TuningCalculator].
     */
    enum class TuningSystem {

        /**
         * **Temperamento Igual de 12 tons (12-TET)**
         *
         * Cada semitom é exatamente 2^(1/12) ≈ 1,05946 vezes o anterior.
         * Todos os intervalos são igualmente "temperados" — nenhuma quinta
         * é pura; cada uma é ≈ 1,96 cents menor do que a quinta pura (3/2).
         * É o sistema padrão de instrumento afinado desde o século XIX.
         *
         * Fórmula: `fRef(n) = A4 × 2^(n/12)`
         */
        EQUAL_TEMPERAMENT,

        /**
         * **Temperamento Pitagórico**
         *
         * Notas derivadas empilhando quintas puras (3/2). As quintas são
         * perfeitas; os terços maiores ficam ≈ 21,5 cents mais altos do que
         * no temperamento igual.
         *
         * As 12 razões são calculadas subindo/descendo pela cadeia de quintas
         * e reduzindo à oitava:
         *   C=1/1, C#=2187/2048, D=9/8, D#=19683/16384, E=81/64, F=4/3,
         *   F#=729/512, G=3/2, G#=6561/4096, A=27/16, A#=59049/32768, B=243/128
         *
         * Uso típico: música medieval, música árabe, análise de quinta pura.
         */
        PYTHAGOREAN,

        /**
         * **Afinação Justa (5-limit, escala intensa de Ptolemeu)**
         *
         * Intervalos baseados em razões de inteiros pequenos (harmônicos naturais).
         * Terços maiores (5/4) e menores (6/5) são puros; quintas (3/2) também puras.
         * Inconveniente: a 2ª oitava não é equivalente ao dobro da 1ª para todas
         * as progressões de acorde.
         *
         * Razões: C=1/1, C#=16/15, D=9/8, D#=6/5, E=5/4, F=4/3, F#=45/32,
         *         G=3/2, G#=8/5, A=5/3, A#=9/5, B=15/8
         *
         * Uso típico: análise de harmonia vocal, instrumentos de corda ajustados
         * para uma tonalidade específica.
         */
        JUST_INTONATION
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Identifica a nota mais próxima de [frequencyHz].
     *
     * @param frequencyHz Frequência detectada em Hz. Deve ser > 0.
     * @return [NoteMapping] com classe de altura, oitava, offset em semitons
     *   e frequência de referência exata no temperamento configurado.
     * @throws IllegalArgumentException se [frequencyHz] ≤ 0.
     */
    fun map(frequencyHz: Float): NoteMapping = map(frequencyHz.toDouble())

    /**
     * Sobrecarga Double — precisão máxima sem conversão intermediária.
     * Preferida quando a frequência já está em Double
     * (e.g., saída de [TuningCalculator]).
     */
    fun map(frequencyHz: Double): NoteMapping {
        require(frequencyHz > 0.0) { "frequencyHz must be > 0, was $frequencyHz" }

        // n = round(12 × log₂(f/A4)) — semitom mais próximo de A4.
        // Usa Double para evitar arredondamento prematuro antes do round().
        val semitonesFromA4 = 12.0 * log2(frequencyHz / referenceA4Hz)
        val n = semitonesFromA4.roundToInt()

        // MIDI 69 = A4. Subtrai 1 porque a convenção de oitava científica
        // começa em -1 para MIDI 0 (C-1), e C4 = MIDI 60 → 60/12 - 1 = 4. ✓
        val midiNote = 69 + n
        val semitoneInOctave = ((midiNote % 12) + 12) % 12
        val octave = midiNote / 12 - 1

        return NoteMapping(
            pitchClass = PitchClass.fromSemitoneIndex(semitoneInOctave),
            octave = octave,
            semitoneOffsetFromA4 = n,
            exactReferenceHz = referenceHzFor(n)
        )
    }

    /**
     * Calcula a frequência de referência para uma nota deslocada [semitoneOffset]
     * semitons de A4, no sistema de temperamento configurado.
     *
     * Útil para gerar tabelas de referência completas ou para conversão inversa
     * nota → Hz fora do contexto de detecção.
     *
     * @param semitoneOffset Deslocamento em semitons a partir de A4.
     *   A4=0, A#4=+1, A3=−12, C4=−9.
     */
    fun referenceHzFor(semitoneOffset: Int): Double = when (tuningSystem) {
        TuningSystem.EQUAL_TEMPERAMENT -> equalTempHz(semitoneOffset)
        TuningSystem.PYTHAGOREAN       -> nonEqualHz(semitoneOffset, PYTHAGOREAN_RATIOS)
        TuningSystem.JUST_INTONATION   -> nonEqualHz(semitoneOffset, JUST_INTONATION_RATIOS)
    }

    /**
     * Gera um mapa completo de todas as notas detectáveis na faixa de oitavas
     * `[startOctave, endOctave]`, associando o nome da nota à sua frequência
     * de referência no temperamento configurado.
     *
     * Exemplo de uso: pré-preencher um display de afinador com todas as notas
     * possíveis antes de o usuário tocar.
     *
     * @param startOctave Oitava mínima (inclusiva). Default: 1 (E1 ≈ 41 Hz).
     * @param endOctave Oitava máxima (inclusiva). Default: 8 (C8 ≈ 4186 Hz).
     * @return Mapa ordenado: fullName → frequência em Hz (Float para a UI).
     */
    fun referenceTable(startOctave: Int = 1, endOctave: Int = 8): Map<String, Float> {
        val result = LinkedHashMap<String, Float>((endOctave - startOctave + 1) * 12)
        // Itera em ordem de pitch (grave → agudo) para facilitar exibição.
        for (octave in startOctave..endOctave) {
            for (pitchClass in PitchClass.entries) {
                // Semitom de A4: midiNote = (octave + 1) * 12 + pitchClass.semitone
                // A4 = MIDI 69 → semitoneOffset = midiNote - 69
                val midiNote = (octave + 1) * 12 + pitchClass.semitone
                val semitoneOffset = midiNote - 69
                val hz = referenceHzFor(semitoneOffset)
                if (hz in 20.0..4200.0) {   // filtra fora da faixa musical detectável
                    result["${pitchClass.label}$octave"] = hz.toFloat()
                }
            }
        }
        return result
    }

    // ── Implementações de temperamento ────────────────────────────────────────

    /**
     * Temperamento Igual: `A4 × 2^(n/12)`.
     *
     * Usa `2.0.pow(n / 12.0)` em vez de `exp(n × ln2 / 12)` para evitar
     * acúmulo de erro numérico — a JVM aplica a fórmula diretamente via
     * `StrictMath.pow`, garantindo resultado correto para IEEE 754.
     */
    private fun equalTempHz(semitoneOffset: Int): Double =
        referenceA4Hz * 2.0.pow(semitoneOffset / 12.0)

    /**
     * Temperamentos não-iguais (Pitagórico / Justo).
     *
     * Estratégia para A4 como referência:
     *
     * 1. O índice de A dentro de uma oitava é semitone 9 (C=0 … A=9).
     *    A razão `ratio(A)` é a 10ª entrada de [ratios].
     *    Portanto: `fC_sameOctave = A4 / ratios[9]`
     *
     * 2. Para um offset `n` em relação a A4, a oitava relativa a C é
     *    `midiNote = 69 + n`, `octaveShift = midiNote / 12 - (referenceOctave A4 + 1)`.
     *    Simplificado: `octaveShift = (69 + n) / 12 - 6`
     *    (porque o C de referência do A4 é o MIDI 60, octave index 5 no cálculo
     *    com 12 notas por oitava — a divisão inteira abaixo captura isso diretamente).
     *
     * 3. `fRef = fC × ratios[pitchClassIndex] × 2^octaveShift`
     *
     * Nota: o resultado pode diferir do temperamento igual por alguns cents.
     * Isso é correto e esperado — é exatamente a diferença entre os sistemas.
     */
    private fun nonEqualHz(semitoneOffset: Int, ratios: DoubleArray): Double {
        val midiNote = 69 + semitoneOffset
        val pitchClassIndex = ((midiNote % 12) + 12) % 12

        // Frequência de C na mesma oitava em que A4 reside, no temperamento dado.
        // ratios[9] = razão de A em relação a C naquele temperamento.
        val fCinA4Octave = referenceA4Hz / ratios[A_INDEX_IN_OCTAVE]

        // Deslocamento de oitava: MIDI 60 = C4; cada 12 MIDIs = 1 oitava.
        // 69 → A4 está 0 oitavas acima de C4 na divisão (69/12 = 5, 60/12 = 5: mesma oitava).
        val octaveShiftFromC4 = midiNote / 12 - A4_MIDI / 12

        return fCinA4Octave * ratios[pitchClassIndex] * 2.0.pow(octaveShiftFromC4)
    }

    // ── Tabelas de razões de temperamento ────────────────────────────────────

    companion object {

        /** Índice de A dentro de uma oitava (C=0 … A=9). */
        private const val A_INDEX_IN_OCTAVE = 9

        /** Número MIDI de A4. */
        private const val A4_MIDI = 69

        /**
         * Razões pitagóricas para os 12 semitons (relativos a C = 0),
         * construídas empilhando quintas puras (3/2) e reduzindo à oitava.
         *
         * Cada razão `r[i]` satisfaz `2^(n/12) ≈ r[i]`, mas com quintas
         * exatamente puras. A coma pitagórica (≈ 23,46 cents) é "absorvida"
         * pelo D# (19683/16384) — ou seja, o D# pitagórico não é idêntico ao
         * Eb. Esta coleção usa sustenidos ao longo de toda a escala.
         *
         * Derivação para cada grau (n subidas de quinta, m reduções de oitava):
         *   C:  (3/2)^0             = 1/1
         *   G:  (3/2)^1 / 2^0      = 3/2
         *   D:  (3/2)^2 / 2^1      = 9/8
         *   A:  (3/2)^3 / 2^1      = 27/16
         *   E:  (3/2)^4 / 2^2      = 81/64
         *   B:  (3/2)^5 / 2^2      = 243/128
         *   F#: (3/2)^6 / 2^3      = 729/512
         *   C#: (3/2)^7 / 2^4      = 2187/2048
         *   G#: (3/2)^8 / 2^4      = 6561/4096
         *   D#: (3/2)^9 / 2^5      = 19683/16384
         *   A#: (3/2)^10 / 2^5     = 59049/32768
         *   F:  2 / (3/2)          = 4/3   (descendo uma quinta)
         */
        private val PYTHAGOREAN_RATIOS = doubleArrayOf(
            1.0,               // C   semitone 0
            2187.0 / 2048,     // C#  semitone 1   ≈ 1.06787
            9.0 / 8,           // D   semitone 2   = 1.12500
            19683.0 / 16384,   // D#  semitone 3   ≈ 1.20135
            81.0 / 64,         // E   semitone 4   ≈ 1.26563
            4.0 / 3,           // F   semitone 5   ≈ 1.33333
            729.0 / 512,       // F#  semitone 6   ≈ 1.42383
            3.0 / 2,           // G   semitone 7   = 1.50000
            6561.0 / 4096,     // G#  semitone 8   ≈ 1.60180
            27.0 / 16,         // A   semitone 9   = 1.68750
            59049.0 / 32768,   // A#  semitone 10  ≈ 1.80203
            243.0 / 128        // B   semitone 11  ≈ 1.89844
        )

        /**
         * Razões de afinação justa (5-limit) para os 12 semitons relativos a C.
         *
         * Fonte: Escala intensa de Ptolemeu, a mais usada em análises de
         * harmonia tonal. Todos os intervalos fundamentais são razões de inteiros
         * pequenos: quinta pura (3/2), terço maior puro (5/4), terço menor puro (6/5).
         *
         *   C  = 1/1    D  = 9/8    E  = 5/4    F  = 4/3    G  = 3/2
         *   A  = 5/3    B  = 15/8   C# = 16/15  D# = 6/5    F# = 45/32
         *   G# = 8/5    A# = 9/5
         *
         * Diferença prática em relação ao temperamento igual (em cents):
         *   C#: −11.7,  D#: +15.6,  F#: −9.8,  G#: +13.7,  A#: +17.6
         */
        private val JUST_INTONATION_RATIOS = doubleArrayOf(
            1.0,         // C   semitone 0   = 1.00000
            16.0 / 15,   // C#  semitone 1   ≈ 1.06667
            9.0 / 8,     // D   semitone 2   = 1.12500
            6.0 / 5,     // D#  semitone 3   = 1.20000
            5.0 / 4,     // E   semitone 4   = 1.25000
            4.0 / 3,     // F   semitone 5   ≈ 1.33333
            45.0 / 32,   // F#  semitone 6   ≈ 1.40625
            3.0 / 2,     // G   semitone 7   = 1.50000
            8.0 / 5,     // G#  semitone 8   = 1.60000
            5.0 / 3,     // A   semitone 9   ≈ 1.66667
            9.0 / 5,     // A#  semitone 10  = 1.80000
            15.0 / 8     // B   semitone 11  = 1.87500
        )
    }
}
