package com.pitchandmetronome.metronome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchandmetronome.core.audio.AudioEngineConfig
import com.pitchandmetronome.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

private fun tempoMarking(bpm: Int): String = when {
    bpm < 40  -> "Grave"
    bpm < 45  -> "Largo"
    bpm < 51  -> "Lento"
    bpm < 56  -> "Larghetto"
    bpm < 66  -> "Adagio"
    bpm < 76  -> "Andante"
    bpm < 108 -> "Andantino"
    bpm < 120 -> "Moderato"
    bpm < 140 -> "Allegretto"
    bpm < 168 -> "Allegro"
    bpm < 200 -> "Vivace"
    bpm < 220 -> "Presto"
    else      -> "Prestissimo"
}

/**
 * Converts a touch position to an angle on the arc (0..270), where
 * 0 = arc start (bottom-left, 135° in standard coords) and
 * 270 = arc end (bottom-right, 405° in standard coords).
 * Returns null if the touch is in the dead-zone (the 90° gap at the bottom).
 */
private fun positionToArcAngle(x: Float, y: Float, centerX: Float, centerY: Float): Float? {
    val dx = x - centerX
    val dy = y - centerY
    // atan2 gives angle from positive X axis, clockwise in screen coords
    var degrees = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (degrees < 0f) degrees += 360f // normalize to 0..360

    // The arc starts at 135° and sweeps 270° clockwise (to 405° = 45°).
    // Map standard angle to arc-relative angle:
    //   arc 0   = 135° standard
    //   arc 270 = 405° (= 45°) standard
    // Dead zone: 45° to 135° standard (the bottom gap)
    val arcAngle = degrees - 135f
    val normalized = if (arcAngle < 0f) arcAngle + 360f else arcAngle

    // If normalized > 270, the touch is in the dead zone
    return if (normalized <= 270f) normalized else null
}

/**
 * Circular BPM dial with accent ring and tempo marking.
 *
 * Supports **angular drag** gesture: the user drags along the arc path
 * (clockwise = increase BPM, counter-clockwise = decrease BPM).
 * The touch position is converted to an angle on the arc and mapped
 * directly to BPM.
 */
@Composable
fun BpmDialComponent(
    modifier: Modifier = Modifier,
    bpm: Int,
    onBpmChange: (Int) -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val goldAccent = appColors.metronomeAccent
    val goldDark = appColors.metronomeAccentDark

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val progress = remember(bpm) {
        ((bpm - AudioEngineConfig.METRONOME_BPM_MIN).toFloat() /
            (AudioEngineConfig.METRONOME_BPM_MAX - AudioEngineConfig.METRONOME_BPM_MIN)).coerceIn(0f, 1f)
    }

    // ── Angular drag gesture state ───────────────────────────────────────
    val currentOnBpmChange = rememberUpdatedState(onBpmChange)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(220.dp)
            .pointerInput(Unit) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val bpmRange = AudioEngineConfig.METRONOME_BPM_MAX - AudioEngineConfig.METRONOME_BPM_MIN

                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val arcAngle = positionToArcAngle(
                            change.position.x, change.position.y,
                            centerX, centerY
                        ) ?: return@detectDragGestures

                        val fraction = (arcAngle / 270f).coerceIn(0f, 1f)
                        val newBpm = (AudioEngineConfig.METRONOME_BPM_MIN + (fraction * bpmRange).toInt())
                            .coerceIn(AudioEngineConfig.METRONOME_BPM_MIN, AudioEngineConfig.METRONOME_BPM_MAX)
                        currentOnBpmChange.value(newBpm)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeW = 6.dp.toPx()
            val padding = strokeW / 2f + 4.dp.toPx()
            val arcSize = size.width - padding * 2f

            // Background ring
            drawArc(
                color = surfaceVariant,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Accent progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(goldDark, goldAccent, goldAccent)
                ),
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Inner decorative ring
            val innerPadding = padding + 14.dp.toPx()
            val innerSize = size.width - innerPadding * 2f
            drawArc(
                color = surfaceVariant.copy(alpha = 0.5f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(innerPadding, innerPadding),
                size = androidx.compose.ui.geometry.Size(innerSize, innerSize),
                style = Stroke(width = 1.5f)
            )

            // Tick marks around the arc
            val tickRadius = arcSize / 2f + padding
            val center = size.width / 2f
            for (i in 0..270 step 15) {
                val angle = Math.toRadians((135 + i).toDouble())
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                val isMajor = i % 45 == 0
                val innerR = tickRadius - if (isMajor) 14.dp.toPx() else 8.dp.toPx()
                val outerR = tickRadius - 3.dp.toPx()
                drawLine(
                    color = if (isMajor) onSurfaceVariant.copy(alpha = 0.4f)
                            else onSurfaceVariant.copy(alpha = 0.15f),
                    start = Offset(center + innerR * cos, center + innerR * sin),
                    end = Offset(center + outerR * cos, center + outerR * sin),
                    strokeWidth = if (isMajor) 2f else 1f,
                    cap = StrokeCap.Round
                )
            }
        }

        // BPM text in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$bpm",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                letterSpacing = (-1).sp
            )
            Text(
                text = tempoMarking(bpm),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Bottom control bar: Play | — BPM + | TAP
 *
 * Long-press on +/- triggers continuous BPM change with acceleration.
 */
@Composable
fun BpmControlBar(
    modifier: Modifier = Modifier,
    bpm: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    onBpmChange: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onTapTempo: () -> Unit
) {
    val appColors = LocalAppColors.current
    val goldAccent = appColors.metronomeAccent

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // ── Long-press repeat state ──────────────────────────────────────────
    var holdingDirection by remember { mutableStateOf(0) }
    var holdStartTime by remember { mutableLongStateOf(0L) }

    val bpmState = rememberUpdatedState(bpm)
    val onBpmChangeState = rememberUpdatedState(onBpmChange)

    LaunchedEffect(holdingDirection) {
        if (holdingDirection == 0) return@LaunchedEffect
        holdStartTime = System.currentTimeMillis()
        // Initial change already applied by onPress; start repeat after delay
        delay(400L)
        while (holdingDirection != 0) {
            val elapsed = System.currentTimeMillis() - holdStartTime
            val interval = when {
                elapsed < 1_000L -> 150L
                elapsed < 2_500L -> 80L
                else             -> 50L
            }
            val step = if (elapsed > 3_000L) 5 else 1
            val current = bpmState.value
            val newBpm = (current + holdingDirection * step)
                .coerceIn(AudioEngineConfig.METRONOME_BPM_MIN, AudioEngineConfig.METRONOME_BPM_MAX)
            onBpmChangeState.value(newBpm)
            delay(interval)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        Surface(
            onClick = { if (!isLoading) onPlayPause() },
            shape = RoundedCornerShape(14.dp),
            color = if (isPlaying) appColors.metronomeAccentContainer else surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = goldAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Tocar",
                        modifier = Modifier.size(22.dp),
                        tint = if (isPlaying) goldAccent else onSurface
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // BPM stepper: — BPM +
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = surfaceVariant,
            modifier = Modifier.height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Minus
                Surface(
                    shape = CircleShape,
                    color = surfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val current = bpmState.value
                                    onBpmChangeState.value(
                                        (current - 1).coerceAtLeast(AudioEngineConfig.METRONOME_BPM_MIN)
                                    )
                                    holdingDirection = -1
                                    tryAwaitRelease()
                                    holdingDirection = 0
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Remove, "Diminuir BPM", Modifier.size(18.dp), tint = onSurface)
                    }
                }

                Text(
                    text = "BPM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                // Plus
                Surface(
                    shape = CircleShape,
                    color = surfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val current = bpmState.value
                                    onBpmChangeState.value(
                                        (current + 1).coerceAtMost(AudioEngineConfig.METRONOME_BPM_MAX)
                                    )
                                    holdingDirection = 1
                                    tryAwaitRelease()
                                    holdingDirection = 0
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, "Aumentar BPM", Modifier.size(18.dp), tint = onSurface)
                    }
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // TAP tempo button
        Surface(
            onClick = onTapTempo,
            shape = RoundedCornerShape(14.dp),
            color = surfaceVariant,
            modifier = Modifier.height(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "TAP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
