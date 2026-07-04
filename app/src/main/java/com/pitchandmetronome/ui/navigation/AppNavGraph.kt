package com.pitchandmetronome.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pitchandmetronome.R
import com.pitchandmetronome.metronome.ui.MetronomeScreen
import com.pitchandmetronome.onboarding.OnboardingViewModel
import com.pitchandmetronome.onboarding.ui.OnboardingDialog
import com.pitchandmetronome.tuner.ui.TunerScreen
import com.pitchandmetronome.ui.components.CapsuleNavItem
import com.pitchandmetronome.ui.components.FloatingCapsuleNav
import com.pitchandmetronome.ui.theme.AppThemeMode

/**
 * Grafo de navegação raiz do aplicativo com [FloatingCapsuleNav] sobreposto.
 *
 * ## Por que Box em vez de Scaffold.bottomBar?
 *
 * O design da cápsula flutuante **não empurra** o conteúdo das telas para cima —
 * ela flutua sobre o conteúdo. Se usássemos [Scaffold.bottomBar], o sistema
 * adicionaria padding ao `NavHost`, deslocando visualmente os componentes centrados
 * (como o ponteiro do afinador) para fora do centro óptico da tela.
 *
 * Em vez disso, cada tela reserva seu próprio espaço inferior via
 * `padding(bottom = NAV_BOTTOM_RESERVED_DP)`, mantendo o controle fino do layout.
 *
 * ## Preservação de estado entre abas
 *
 * `saveState/restoreState = true` garante que o metrônomo não seja parado ao
 * navegar para o afinador, e vice-versa — o estado do ViewModel sobrevive
 * enquanto a back stack da aba existe.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val showOnboarding by onboardingViewModel.showOnboarding.collectAsStateWithLifecycle()

    // Painters criados aqui — rememberVectorPainter e painterResource fazem cache
    // internamente, garantindo zero reallocation entre recomposições.
    val metronomeIcon = painterResource(R.drawable.ic_metronome)
    val tunerIcon = painterResource(R.drawable.ic_tuning_fork)
    val navItems = remember(metronomeIcon, tunerIcon) {
        listOf(
            CapsuleNavItem(
                icon = tunerIcon,
                contentDescription = "Afinador",
                route = AppDestination.Tuner.route
            ),
            CapsuleNavItem(
                icon = metronomeIcon,
                contentDescription = "Metrônomo",
                route = AppDestination.Metronome.route
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // NavHost ocupa a tela inteira; cada screen reserva padding próprio
        NavHost(
            navController = navController,
            startDestination = AppDestination.Tuner.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestination.Tuner.route) { TunerScreen() }
            composable(AppDestination.Metronome.route) { MetronomeScreen() }
        }

        // Botão de alternância de tema — canto superior direito
        IconButton(
            onClick = {
                // Ciclo direto entre claro e escuro para evitar estados
                // visualmente idênticos (SYSTEM pode ser igual a DARK).
                val next = when (themeMode) {
                    AppThemeMode.DARK   -> AppThemeMode.LIGHT
                    AppThemeMode.LIGHT  -> AppThemeMode.DARK
                    AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                }
                onThemeModeChange(next)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
                .size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Icon(
                imageVector = when (themeMode) {
                    AppThemeMode.DARK   -> Icons.Filled.DarkMode
                    AppThemeMode.LIGHT  -> Icons.Filled.LightMode
                    AppThemeMode.SYSTEM -> Icons.Filled.LightMode
                },
                contentDescription = when (themeMode) {
                    AppThemeMode.DARK   -> "Tema escuro (toque para claro)"
                    AppThemeMode.LIGHT  -> "Tema claro (toque para escuro)"
                    AppThemeMode.SYSTEM -> "Tema do sistema (toque para claro)"
                },
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Cápsula flutuante sobreposta no centro-inferior, acima do nav bar do sistema
        FloatingCapsuleNav(
            items = navItems,
            currentRoute = currentRoute,
            onItemClick = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )

        // Introdução exibida apenas na primeira abertura do app.
        if (showOnboarding) {
            OnboardingDialog(onDismiss = onboardingViewModel::onDismiss)
        }
    }
}
