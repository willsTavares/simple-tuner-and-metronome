package com.pitchandmetronome

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.pitchandmetronome.ui.navigation.AppNavGraph
import com.pitchandmetronome.ui.theme.AppThemeMode
import com.pitchandmetronome.ui.theme.PitchAndMetronomeTheme
import com.pitchandmetronome.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity do aplicativo (Single-Activity pattern).
 *
 * Responsabilidades:
 * - Habilitar edge-to-edge rendering
 * - Observar o [AppThemeMode] persistido no DataStore via [ThemeViewModel]
 * - Ajustar o estilo das system bars (status bar / nav bar) ao tema ativo
 * - Inicializar o NavController raiz
 * - Hospedar o [AppNavGraph] que gerencia as rotas Metronome / Tuner
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Definir fundo escuro da window ANTES de super.onCreate e setContent
        // para evitar flash branco no cold start e ao trocar de janela.
        window.setBackgroundDrawable(ColorDrawable(DARK_BG))
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            val useDark = when (themeMode) {
                AppThemeMode.LIGHT  -> false
                AppThemeMode.DARK   -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Atualiza a window background e system bars ao mudar de tema.
            // SideEffect roda sincronamente após cada composição bem-sucedida,
            // garantindo zero frames de atraso ao trocar dark ↔ light.
            SideEffect {
                val bgColor = if (useDark) DARK_BG else LIGHT_BG
                window.setBackgroundDrawable(ColorDrawable(bgColor))

                enableEdgeToEdge(
                    statusBarStyle = if (useDark)
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                    navigationBarStyle = if (useDark)
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(
                            LIGHT_BG,
                            DARK_BG
                        )
                )
            }

            PitchAndMetronomeTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    themeMode = themeMode,
                    onThemeModeChange = { themeViewModel.setThemeMode(it) }
                )
            }
        }
    }

    companion object {
        /** Cor de fundo da window no tema escuro — GitHub dark background. */
        private const val DARK_BG = 0xFF010409.toInt()
        /** Cor de fundo da window no tema claro. */
        private const val LIGHT_BG = 0xFFF5F6FC.toInt()
    }
}
