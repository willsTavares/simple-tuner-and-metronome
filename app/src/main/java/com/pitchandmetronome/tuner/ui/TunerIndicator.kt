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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pitchandmetronome.ui.theme.TuneColors
import kotlin.math.abs

/**
 * Status text indicator — shows tuning state as colored text.
 * "In tune!", "High (D#)", "Low (Db)", "Waiting...", "Stopped"
 */
@Composable
fun TunerIndicator(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    confidence: Float,
    centsDeviation: Float
) {
    val label: String
    val color: androidx.compose.ui.graphics.Color
    val onSurfVar = MaterialTheme.colorScheme.onSurfaceVariant

    when {
        !isListening -> {
            label = "Parado"
            color = onSurfVar
        }
        confidence < 0.3f -> {
            label = "Aguardando..."
            color = onSurfVar
        }
        abs(centsDeviation) <= 5f -> {
            label = "Afinado!"
            color = TuneColors.InTune
        }
        centsDeviation > 0f -> {
            label = "Alto"
            color = TuneColors.Close
        }
        else -> {
            label = "Baixo"
            color = TuneColors.Close
        }
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
