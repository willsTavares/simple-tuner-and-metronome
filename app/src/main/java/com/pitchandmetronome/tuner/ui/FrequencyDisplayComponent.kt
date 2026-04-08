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

/**
 * Simple frequency display — shows Hz value in a clean single line.
 */
@Composable
fun FrequencyDisplay(
    modifier: Modifier = Modifier,
    frequency: Float,
    centsDeviation: Float,
    isListening: Boolean,
    confidence: Float
) {
    val active = isListening && confidence >= 0.3f
    val hzText = if (active && frequency > 0f)
        "%.2f Hz".format(frequency)
    else
        "--- Hz"

    AnimatedContent(
        targetState = hzText,
        transitionSpec = { fadeIn(FREQ_TWEEN) togetherWith fadeOut(FREQ_TWEEN) },
        label = "FreqAnim",
        modifier = modifier
    ) { txt ->
        Text(
            text = txt,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
    }
}

private val FREQ_TWEEN: FiniteAnimationSpec<Float> = tween(100)
