package com.pitchandmetronome.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchandmetronome.domain.tuner.TuningCalculator
import com.pitchandmetronome.ui.theme.TuneColors
import kotlin.math.abs

/**
 * Indicador de afinação horizontal com escala ampliada perto do centro.
 *
 * **Escala não linear:** os primeiros ±10 cents ocupam metade da largura e os
 * 40 cents restantes a outra metade. Cada cent perto do alvo fica ~5× maior que
 * numa escala linear de ±50 — é onde a precisão importa; longe do alvo, basta
 * saber a direção.
 *
 * **Zona de afinado:** faixa verde em ±[TuningCalculator.IN_TUNE_ENTER_CENTS] —
 * o objetivo é colocar a agulha dentro dela. Acende quando [isInTune].
 *
 * **Movimento amortecido:** mola sem overshoot. Uma mola "bouncy" ultrapassa o
 * alvo e volta a cada atualização, o que parece oscilação do sinal mesmo quando
 * o pitch está parado.
 */
@Composable
fun TuningNeedle(
    modifier: Modifier = Modifier,
    centsDeviation: Float,
    hasSignal: Boolean,
    isInTune: Boolean,
    color: Color
) {
    val position by animateFloatAsState(
        targetValue = centsToScale(centsDeviation),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "NeedlePosition"
    )
    val needleColor by animateColorAsState(color, tween(200), label = "NeedleColor")
    val needleAlpha by animateFloatAsState(
        targetValue = if (hasSignal) 1f else 0.25f,
        animationSpec = tween(250),
        label = "NeedleAlpha"
    )
    val zoneAlpha by animateFloatAsState(
        targetValue = if (isInTune && hasSignal) 0.28f else 0.10f,
        animationSpec = tween(200),
        label = "ZoneAlpha"
    )

    val tickBase = MaterialTheme.colorScheme.onSurfaceVariant
    val tickMinor = remember(tickBase) { tickBase.copy(alpha = 0.18f) }
    val tickMajor = remember(tickBase) { tickBase.copy(alpha = 0.40f) }

    val textMeasurer = rememberTextMeasurer()
    val labels = remember(textMeasurer, tickBase) {
        val style = TextStyle(
            fontSize = 11.sp,
            color = tickBase.copy(alpha = 0.6f),
            fontFeatureSettings = "tnum"
        )
        LABELED_CENTS.map { c ->
            val text = when {
                c > 0 -> "+$c"
                c < 0 -> "−${-c}"
                else  -> "0"
            }
            c to textMeasurer.measure(text, style)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val cx = size.width / 2f
        val half = size.width * 0.45f
        val cy = size.height * 0.40f
        val maxTick = size.height * 0.50f
        fun xFor(cents: Float) = cx + centsToScale(cents) * half

        // Zona de afinado
        val zoneLeft = xFor(-TuningCalculator.IN_TUNE_ENTER_CENTS)
        val zoneRight = xFor(TuningCalculator.IN_TUNE_ENTER_CENTS)
        drawRoundRect(
            color = TuneColors.InTune.copy(alpha = zoneAlpha),
            topLeft = Offset(zoneLeft, cy - maxTick / 2f - 6.dp.toPx()),
            size = Size(zoneRight - zoneLeft, maxTick + 12.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // Marcas: 1 cent em ±10, 10 cents além
        for (c in TICK_CENTS) {
            val a = abs(c)
            val (h, tickColor, width) = when {
                a == 0                     -> Triple(maxTick, tickMajor, 2.dp)
                a == 10 || a == 50         -> Triple(maxTick * 0.8f, tickMajor, 1.5.dp)
                a == 5 || a > 10           -> Triple(maxTick * 0.55f, tickMajor, 1.dp)
                else                       -> Triple(maxTick * 0.35f, tickMinor, 1.dp)
            }
            val x = xFor(c.toFloat())
            drawLine(
                color = tickColor,
                start = Offset(x, cy - h / 2f),
                end = Offset(x, cy + h / 2f),
                strokeWidth = width.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Rótulos
        val labelTop = cy + maxTick / 2f + 14.dp.toPx()
        for ((c, layout) in labels) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(xFor(c.toFloat()) - layout.size.width / 2f, labelTop)
            )
        }

        // Agulha
        val nx = cx + position * half
        val needleH = maxTick + 20.dp.toPx()
        drawLine(
            color = needleColor.copy(alpha = 0.25f * needleAlpha),
            start = Offset(nx, cy - needleH / 2f),
            end = Offset(nx, cy + needleH / 2f),
            strokeWidth = 12.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = needleColor.copy(alpha = needleAlpha),
            start = Offset(nx, cy - needleH / 2f),
            end = Offset(nx, cy + needleH / 2f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Mapeia cents para a posição na escala (−1..+1): linear até ±10 cents
 * (metade da escala) e comprimido de 10 a 50 cents (outra metade).
 */
private fun centsToScale(cents: Float): Float {
    val a = abs(cents).coerceAtMost(50f)
    val s = if (a <= 10f) 0.5f * a / 10f else 0.5f + 0.5f * (a - 10f) / 40f
    return if (cents < 0f) -s else s
}

private val TICK_CENTS: List<Int> =
    listOf(-50, -40, -30, -20) + (-10..10).toList() + listOf(20, 30, 40, 50)

private val LABELED_CENTS = listOf(-50, -10, 0, 10, 50)
