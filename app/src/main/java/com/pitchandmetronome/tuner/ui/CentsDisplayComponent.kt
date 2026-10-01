package com.pitchandmetronome.tuner.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Desvio numérico em cents ("+3", "0", "−12") — a leitura que permite afinar
 * com precisão além do que a posição da agulha mostra.
 *
 * Dígitos tabulares (`tnum`): a largura do texto não muda entre valores, então o
 * número não "dança" horizontalmente enquanto atualiza.
 */
@Composable
fun CentsDisplay(
    modifier: Modifier = Modifier,
    cents: Float,
    hasSignal: Boolean,
    color: Color
) {
    val value = cents.roundToInt()
    val text = when {
        !hasSignal -> "--"
        value > 0  -> "+$value"
        value < 0  -> "−${-value}"
        else       -> "0"
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            style = TabularNumbers,
            color = color
        )
        Text(
            text = "cents",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
    }
}

/** Dígitos de largura fixa — números que atualizam rápido não "dançam". */
internal val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
