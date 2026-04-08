package com.pitchandmetronome.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pitchandmetronome.data.preferences.PreferencesKeys
import com.pitchandmetronome.domain.model.metronome.MetronomeState
import com.pitchandmetronome.domain.model.metronome.SoundProfile
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação concreta de [IMetronomeRepository].
 *
 * Estratégia de estado dual:
 * - [_stateFlow]: mantém o estado em memória (rápido, para observação reativa)
 * - [dataStore]: persiste apenas as preferências configuráveis (BPM, compasso, etc.)
 *   `isPlaying` nunca é persistido — o metrônomo sempre inicia parado.
 *
 * O [_stateFlow] é inicializado com valores padrão e atualizado no primeiro
 * acesso (lazy load via [getState]).
 */
@Singleton
class MetronomeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : IMetronomeRepository {

    private val _stateFlow = MutableStateFlow(MetronomeState())

    override fun observeState(): Flow<MetronomeState> = _stateFlow

    override suspend fun getState(): MetronomeState {
        // Inicializa o estado com valores persistidos na primeira chamada
        if (_stateFlow.value == MetronomeState()) {
            val prefs = dataStore.data.first()
            _stateFlow.value = _stateFlow.value.copy(
                bpm = prefs[PreferencesKeys.METRONOME_BPM] ?: 120,
                timeSignature = prefs[PreferencesKeys.METRONOME_TIME_SIGNATURE]
                    ?.let { TimeSignature.fromString(it) }
                    ?: TimeSignature.FOUR_FOUR,
                soundProfile = prefs[PreferencesKeys.METRONOME_SOUND_PROFILE]
                    ?.let { runCatching { SoundProfile.valueOf(it) }.getOrNull() }
                    ?: SoundProfile.CLICK,
                accentFirstBeat = prefs[PreferencesKeys.METRONOME_ACCENT_FIRST_BEAT] ?: true
            )
        }
        return _stateFlow.value
    }

    override suspend fun updateBpm(bpm: Int) {
        _stateFlow.update { it.copy(bpm = bpm) }
        dataStore.edit { prefs -> prefs[PreferencesKeys.METRONOME_BPM] = bpm }
    }

    override suspend fun updateTimeSignature(timeSignature: TimeSignature) {
        _stateFlow.update { it.copy(timeSignature = timeSignature) }
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.METRONOME_TIME_SIGNATURE] = timeSignature.displayName
        }
    }

    override suspend fun updateSoundProfile(soundProfile: SoundProfile) {
        _stateFlow.update { it.copy(soundProfile = soundProfile) }
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.METRONOME_SOUND_PROFILE] = soundProfile.name
        }
    }

    override suspend fun updateAccentFirstBeat(accent: Boolean) {
        _stateFlow.update { it.copy(accentFirstBeat = accent) }
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.METRONOME_ACCENT_FIRST_BEAT] = accent
        }
    }

    override suspend fun setPlaying(isPlaying: Boolean) {
        _stateFlow.update { it.copy(isPlaying = isPlaying) }
    }
}
