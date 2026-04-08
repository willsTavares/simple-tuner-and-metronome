package com.pitchandmetronome.metronome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.pitchandmetronome.domain.model.metronome.BeatEvent
import com.pitchandmetronome.ui.theme.LocalAppColors

private val BeatShape = RoundedCornerShape(4.dp)
private val TWEEN_IN = tween<Float>(durationMillis = 50)
private val TWEEN_OUT = tween<Float>(durationMillis = 200)

/**
 * Rectangular beat blocks — accent-colored when active, dim when inactive.
 * Uses [LocalAppColors] for adaptive colors in light/dark themes.
 */
@Composable
fun BeatVisualizerComponent(
    modifier: Modifier = Modifier,
    beatsPerMeasure: Int,
    lastBeatEvent: BeatEvent?,
    isPlaying: Boolean
) {
    val appColors = LocalAppColors.current
    val goldAccent = appColors.metronomeAccent
    val goldDim = appColors.beatDim

    val alphas = remember(beatsPerMeasure) {
        List(beatsPerMeasure) { Animatable(0f) }
    }

    LaunchedEffect(lastBeatEvent) {
        if (lastBeatEvent == null || !isPlaying) return@LaunchedEffect
        val beatIndex = (lastBeatEvent.beatNumber - 1) % beatsPerMeasure
        alphas[beatIndex].animateTo(1f, TWEEN_IN)
        alphas[beatIndex].animateTo(0f, TWEEN_OUT)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(beatsPerMeasure) { index ->
            key(index) {
                val isAccent = index == 0
                val activeAlpha = alphas[index].value
                val baseColor = if (activeAlpha > 0f) {
                    if (isAccent) goldAccent else goldAccent.copy(alpha = 0.85f)
                } else {
                    goldDim
                }

                Box(
                    modifier = Modifier
                        .width(if (beatsPerMeasure <= 4) 40.dp else 28.dp)
                        .height(if (isAccent) 24.dp else 20.dp)
                        .alpha(if (activeAlpha > 0f) 0.5f + activeAlpha * 0.5f else 0.4f)
                        .background(color = baseColor, shape = BeatShape)
                )
            }
        }
    }
}
