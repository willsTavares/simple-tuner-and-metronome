package com.pitchandmetronome.tuner.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.pitchandmetronome.ui.theme.TuneColors

/**
 * Vertical-bar pitch indicator — clean, minimal design.
 *
 * Displays thin vertical tick marks spread across the width with a single
 * colored indicator bar that slides horizontally based on cents deviation.
 */
@Composable
fun TuningNeedle(
    modifier: Modifier = Modifier,
    centsDeviation: Float,
    confidence: Float
) {
    val animatedDeviation by animateFloatAsState(
        targetValue = centsDeviation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NeedleDeviation"
    )

    val tuneColor = TuneColors.forCents(centsDeviation)
    val tickBaseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleAlpha = (confidence * 0.8f + 0.2f).coerceIn(0f, 1f)

    val tickColor = remember(tickBaseColor) { tickBaseColor.copy(alpha = 0.18f) }
    val tickColorMid = remember(tickBaseColor) { tickBaseColor.copy(alpha = 0.30f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val totalTicks = 41
        val tickSpacing = size.width / (totalTicks + 1)
        val barHeight = size.height * 0.55f
        val shortBarHeight = size.height * 0.30f

        // Tick marks
        for (i in 1..totalTicks) {
            val x = tickSpacing * i
            val distFromCenter = i - (totalTicks + 1) / 2
            val isMajor = distFromCenter % 10 == 0
            val isMid = distFromCenter % 5 == 0
            val h = when {
                isMajor -> barHeight
                isMid -> barHeight * 0.7f
                else -> shortBarHeight
            }
            val color = if (isMajor || isMid) tickColorMid else tickColor
            val sw = if (isMajor) 2.5f else 1.5f

            drawLine(
                color = color,
                start = Offset(x, cy - h / 2f),
                end = Offset(x, cy + h / 2f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }

        // Center marker (thin white line)
        drawLine(
            color = tickBaseColor.copy(alpha = 0.35f),
            start = Offset(cx, cy - barHeight * 0.65f),
            end = Offset(cx, cy + barHeight * 0.65f),
            strokeWidth = 1f,
            cap = StrokeCap.Round
        )

        // Animated needle position: maps -50..+50 cents to width
        val needleX = cx + (animatedDeviation / 50f) * (size.width * 0.42f)
        val needleH = barHeight * 0.85f
        val glowColor = tuneColor.copy(alpha = 0.25f * needleAlpha)
        val solidColor = tuneColor.copy(alpha = needleAlpha)

        // Glow
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, glowColor, glowColor, Color.Transparent),
                startY = cy - needleH / 2f,
                endY = cy + needleH / 2f
            ),
            start = Offset(needleX, cy - needleH / 2f),
            end = Offset(needleX, cy + needleH / 2f),
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )

        // Main needle bar
        drawLine(
            color = solidColor,
            start = Offset(needleX, cy - needleH / 2f),
            end = Offset(needleX, cy + needleH / 2f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
    }
}
