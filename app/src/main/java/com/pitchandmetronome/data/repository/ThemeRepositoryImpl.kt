package com.pitchandmetronome.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pitchandmetronome.data.preferences.PreferencesKeys
import com.pitchandmetronome.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório de persistência do tema do aplicativo.
 *
 * Lê e grava o [AppThemeMode] escolhido pelo usuário no DataStore.
 * Se nenhum valor estiver salvo, retorna [AppThemeMode.SYSTEM] como padrão.
 */
@Singleton
class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /** Flow do modo de tema persistido. Emite imediatamente o valor atual. */
    val themeModeFlow: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        val name = prefs[PreferencesKeys.THEME_MODE]
        if (name != null) {
            try { AppThemeMode.valueOf(name) } catch (_: Exception) { AppThemeMode.SYSTEM }
        } else {
            AppThemeMode.SYSTEM
        }
    }

    /** Persiste o modo de tema escolhido. */
    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
}

