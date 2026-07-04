package com.pitchandmetronome.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.data.preferences.PreferencesKeys
import com.pitchandmetronome.domain.model.tuner.TunerConfig
import com.pitchandmetronome.domain.model.tuner.TunerPrecisionMode
import com.pitchandmetronome.domain.repository.ITunerRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação concreta de [ITunerRepository].
 *
 * As preferências do afinador são lidas diretamente do DataStore — não há
 * necessidade de um StateFlow em memória, pois o afinador não precisa de
 * observação reativa de configuração (o usuário raramente muda o A4 de referência).
 */
@Singleton
class TunerRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ITunerRepository {

    override suspend fun getConfig(): TunerConfig {
        val prefs = dataStore.data.first()
        return TunerConfig(
            referenceA4 = prefs[PreferencesKeys.TUNER_REFERENCE_A4]
                ?: AudioEngineConfig.DEFAULT_REFERENCE_A4_HZ,
            sampleRate = prefs[PreferencesKeys.TUNER_SAMPLE_RATE]
                ?: AudioEngineConfig.PREFERRED_SAMPLE_RATE,
            bufferSize = prefs[PreferencesKeys.TUNER_BUFFER_SIZE]
                ?: AudioEngineConfig.YIN_BUFFER_SIZE,
            precisionMode = TunerPrecisionMode.fromName(
                prefs[PreferencesKeys.TUNER_PRECISION_MODE]
            )
        )
    }

    override suspend fun saveConfig(config: TunerConfig) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.TUNER_REFERENCE_A4] = config.referenceA4
            prefs[PreferencesKeys.TUNER_SAMPLE_RATE] = config.sampleRate
            prefs[PreferencesKeys.TUNER_BUFFER_SIZE] = config.bufferSize
            prefs[PreferencesKeys.TUNER_PRECISION_MODE] = config.precisionMode.name
        }
    }
}
