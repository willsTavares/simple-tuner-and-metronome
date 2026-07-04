package com.pitchandmetronome.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchandmetronome.data.repository.OnboardingRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel global da introdução (onboarding) — observado pela [AppNavGraph]
 * para decidir se exibe o modal de boas-vindas.
 *
 * Parte de `false` por padrão: evita qualquer flash do modal para usuários
 * recorrentes enquanto a leitura do DataStore ainda não completou. Só vira
 * `true` se a leitura confirmar que o onboarding nunca foi visto.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepositoryImpl
) : ViewModel() {

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            _showOnboarding.value = !repository.hasSeenOnboarding()
        }
    }

    /** Chamado quando o usuário fecha o modal (botão ou dismiss). */
    fun onDismiss() {
        _showOnboarding.value = false
        viewModelScope.launch {
            repository.markOnboardingSeen()
        }
    }
}
