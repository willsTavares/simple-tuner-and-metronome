package com.pitchandmetronome.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchandmetronome.ui.theme.TuneColors

/**
 * Large note name display with octave — clean minimal style.
 */
@Composable
fun NoteDisplay(
    modifier: Modifier = Modifier,
    noteName: String,
    centsDeviation: Float,
    isListening: Boolean
) {
    val (pitchName, octave) = remember(noteName) {
        if (noteName == "--") "--" to ""
        else {
            val oct = noteName.last().takeIf { it.isDigit() }?.toString() ?: ""
            val name = if (oct.isNotEmpty()) noteName.dropLast(1) else noteName
            name to oct
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val inactiveColor = remember(colorScheme) { colorScheme.onSurface.copy(alpha = 0.20f) }
    val tuneColor = if (isListening && noteName != "--")
        TuneColors.forCents(centsDeviation)
    else
        inactiveColor

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = pitchName,
            transitionSpec = { fadeIn(NOTE_TWEEN) togetherWith fadeOut(NOTE_TWEEN) },
            label = "NoteNameAnim"
        ) { name ->
            Text(
                text = name,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = tuneColor,
                letterSpacing = (-2).sp
            )
        }

        if (octave.isNotEmpty()) {
            Text(
                text = octave,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = tuneColor.copy(alpha = 0.60f),
                modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
            )
        }
    }
}

private val NOTE_TWEEN: FiniteAnimationSpec<Float> = tween(120)
