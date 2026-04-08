package com.pitchandmetronome.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchandmetronome.data.repository.ThemeRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel global de tema — observado pela [MainActivity] para alternar
 * entre os esquemas claro, escuro e sistema.
 *
 * Persiste a escolha no DataStore via [ThemeRepositoryImpl].
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepositoryImpl
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = themeRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }
}

