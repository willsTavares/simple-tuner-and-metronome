package com.pitchandmetronome.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pitchandmetronome.ui.theme.TuneColors

/**
 * Texto de status com a ação a tomar: "Afinado", "Alto — abaixe", "Baixo — suba".
 */
@Composable
fun TunerIndicator(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    hasSignal: Boolean,
    isInTune: Boolean,
    centsDeviation: Float
) {
    val onSurfVar = MaterialTheme.colorScheme.onSurfaceVariant
    val (label: String, color: Color) = when {
        !isListening        -> "Parado" to onSurfVar
        !hasSignal          -> "Toque uma nota" to onSurfVar
        isInTune            -> "Afinado" to TuneColors.InTune
        centsDeviation > 0f -> "Alto — abaixe" to TuneColors.forTuning(false, centsDeviation)
        else                -> "Baixo — suba" to TuneColors.forTuning(false, centsDeviation)
    }

    AnimatedContent(
        targetState = label,
        transitionSpec = { fadeIn(INDICATOR_TWEEN) togetherWith fadeOut(INDICATOR_TWEEN) },
        label = "TunerIndicatorAnim",
        modifier = modifier
    ) { text ->
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

private val INDICATOR_TWEEN: FiniteAnimationSpec<Float> = tween(180)
