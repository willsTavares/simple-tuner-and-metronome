package com.pitchandmetronome

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — ponto de entrada do grafo de injeção de dependência (Hilt).
 *
 * @HiltAndroidApp dispara a geração de código do Hilt e inicializa o componente
 * raiz da aplicação. Todos os módulos Hilt registrados em [core.di] são
 * descobertos automaticamente neste momento.
 */
@HiltAndroidApp
class PitchAndMetronomeApp : Application()
