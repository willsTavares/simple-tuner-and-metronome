package com.pitchandmetronome.core.utils

import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.domain.model.tuner.Note
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Utilitários de conversão entre frequência (Hz), notas musicais e cents.
 *
 * Todas as funções são puras (sem efeitos colaterais) e podem ser chamadas
 * em qualquer thread, incluindo o callback de áudio.
 */
object FrequencyUtils {

    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /**
     * Converte uma frequência em Hz para a [Note] mais próxima.
     *
     * @param frequencyHz Frequência detectada em Hz (deve ser > 0).
     * @param referenceA4 Frequência de referência para A4 (padrão: 440 Hz).
     * @return [Note] com nome, oitava, frequência de referência e desvio em cents.
     */
    fun frequencyToNote(
        frequencyHz: Float,
        referenceA4: Float = AudioEngineConfig.DEFAULT_REFERENCE_A4_HZ
    ): Note {
        // Semitones from A4: n = 12 × log₂(f / A4)
        val semitonesFromA4 = 12.0 * log2(frequencyHz / referenceA4.toDouble())
        val roundedSemitones = semitonesFromA4.roundToInt()

        // MIDI note number (A4 = 69)
        val midiNote = 69 + roundedSemitones
        val noteIndex = ((midiNote % 12) + 12) % 12
        val octave = (midiNote / 12) - 1

        val referenceFrequency = referenceA4 * 2.0.pow(roundedSemitones / 12.0)
        val centsDeviation = 100f * (semitonesFromA4 - roundedSemitones).toFloat()

        return Note(
            name = NOTE_NAMES[noteIndex],
            octave = octave,
            referenceFrequency = referenceFrequency.toFloat(),
            centsDeviation = centsDeviation
        )
    }

    /**
     * Verifica se uma frequência está dentro do alcance detectável.
     * Limita a faixa a instrumentos musicais convencionais (E1–C8).
     */
    fun isInMusicalRange(frequencyHz: Float): Boolean =
        frequencyHz in 20f..4200f

    /**
     * Retorna `true` se o desvio em cents indica que a nota está afinada
     * dentro de uma tolerância aceitável.
     */
    fun isInTune(centsDeviation: Float, toleranceCents: Float = 5f): Boolean =
        abs(centsDeviation) <= toleranceCents
}
