package com.pitchandmetronome.ui.navigation

/**
 * Definição type-safe das rotas de navegação do app.
 *
 * Usando um sealed class para evitar strings mágicas nas chamadas de navigate().
 * Cada feature tem sua própria rota; novas features são adicionadas aqui.
 */
sealed class AppDestination(val route: String) {
    data object Metronome : AppDestination("metronome")
    data object Tuner : AppDestination("tuner")
}
