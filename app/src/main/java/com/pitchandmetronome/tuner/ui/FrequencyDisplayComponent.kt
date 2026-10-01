package com.pitchandmetronome.tuner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Frequência detectada em Hz, discreta. Sem animação de troca: o valor atualiza
 * ~23×/s e um fade a cada mudança só produziria cintilação.
 */
@Composable
fun FrequencyDisplay(
    modifier: Modifier = Modifier,
    frequency: Float,
    hasSignal: Boolean
) {
    Text(
        text = if (hasSignal && frequency > 0f) "%.1f Hz".format(frequency) else "--- Hz",
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        style = TabularNumbers,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = modifier
    )
}
