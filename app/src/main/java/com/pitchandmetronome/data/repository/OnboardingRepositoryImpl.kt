package com.pitchandmetronome.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pitchandmetronome.data.preferences.PreferencesKeys
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório de persistência da introdução (onboarding) exibida na primeira
 * abertura do app.
 *
 * Ausência da chave no DataStore ([hasSeenOnboarding] retorna `false`) indica
 * que o usuário ainda não viu a introdução — cobre tanto a primeira instalação
 * quanto qualquer estado anterior à existência desta feature.
 */
@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun hasSeenOnboarding(): Boolean =
        dataStore.data.first()[PreferencesKeys.ONBOARDING_SEEN] ?: false

    suspend fun markOnboardingSeen() {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ONBOARDING_SEEN] = true
        }
    }
}
