package com.pitchandmetronome.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Descritor de um item do [FloatingCapsuleNav].
 *
 * @param icon Ícone a exibir.
 * @param contentDescription Acessibilidade (TalkBack).
 * @param route Rota de navegação correspondente a [AppDestination.route].
 */
data class CapsuleNavItem(
    val icon: Painter,
    val contentDescription: String,
    val route: String
)

/**
 * Menu de navegação flutuante em formato cápsula.
 *
 * Posicionado com [Alignment.BottomCenter] em um [Box] que cobre a tela inteira
 * — sem [Scaffold.bottomBar]. Isso mantém o conteúdo de cada tela centrado
 * visualmente sem ser empurrado para cima pelo navigation bar padrão.
 *
 * ## Design decisions
 *
 * - **Surface + shadow:** `shadow()` no modifier externo cria elevação real
 *   (shadow do sistema) sem depender do `tonalElevation` do M3, que em modo
 *   escuro produz apenas uma leve mudança de tint — invisível num palco.
 * - **Scale animation:** o item selecionado cresce 1.08× via `animateFloatAsState`
 *   com tween de 200 ms — rápido o suficiente para parecer responsivo, curto
 *   o suficiente para não atrasar a navegação perceptivelmente.
 * - **primaryContainer background:** usa o container da cor primária, não o
 *   primary em si — garante contraste adequado para o ícone (onPrimaryContainer)
 *   em ambos os temas.
 *
 * @param items Lista de items de navegação (no mínimo 2, sem limite teórico).
 * @param currentRoute Rota da tela atualmente ativa.
 * @param onItemClick Callback chamado com a rota do item clicado.
 * @param modifier Aplicado à [Surface] externa da cápsula.
 */
@Composable
fun FloatingCapsuleNav(
    items: List<CapsuleNavItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        // shadow() no modifier externo produz sombra real além da tonal elevation
        modifier = modifier.shadow(elevation = 16.dp, shape = CapsuleShape),
        shape = CapsuleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.route == currentRoute

                // Telegram-style spring animations: scale + vertical bounce
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "CapsuleScale_${item.route}"
                )

                val translationY by animateFloatAsState(
                    targetValue = if (selected) -2f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "CapsuleTransY_${item.route}"
                )

                val alpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.6f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "CapsuleAlpha_${item.route}"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.translationY = translationY
                            this.alpha = alpha
                        }
                        .background(
                            color = if (selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = CapsuleShape
                        )
                        .clip(CapsuleShape)
                        .clickable(role = Role.Tab) { onItemClick(item.route) }
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = item.icon,
                        contentDescription = item.contentDescription,
                        tint = if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Forma compartilhada internamente — RoundedCornerShape(50) = cápsula completa
private val CapsuleShape = RoundedCornerShape(50)
