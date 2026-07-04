package com.pitchandmetronome.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Chaves type-safe para o DataStore de preferências do usuário.
 *
 * Agrupadas aqui para evitar strings mágicas espalhadas pelo código e
 * facilitar a identificação de conflitos de chave em um único lugar.
 */
object PreferencesKeys {
    // Tema
    val THEME_MODE = stringPreferencesKey("theme_mode")

    // Metrônomo
    val METRONOME_BPM = intPreferencesKey("metronome_bpm")
    val METRONOME_TIME_SIGNATURE = stringPreferencesKey("metronome_time_signature")
    val METRONOME_SOUND_PROFILE = stringPreferencesKey("metronome_sound_profile")
    val METRONOME_ACCENT_FIRST_BEAT = booleanPreferencesKey("metronome_accent_first_beat")

    // Afinador
    val TUNER_REFERENCE_A4 = floatPreferencesKey("tuner_reference_a4")
    val TUNER_SAMPLE_RATE = intPreferencesKey("tuner_sample_rate")
    val TUNER_BUFFER_SIZE = intPreferencesKey("tuner_buffer_size")
    val TUNER_PRECISION_MODE = stringPreferencesKey("tuner_precision_mode")
}
