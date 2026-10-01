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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nome da nota em destaque com a oitava menor ao lado.
 *
 * @param color Cor já resolvida pelo chamador (estado de afinação ou inativo).
 */
@Composable
fun NoteDisplay(
    modifier: Modifier = Modifier,
    noteName: String,
    color: Color
) {
    val (pitchName, octave) = remember(noteName) {
        if (noteName == "--") "--" to ""
        else {
            val oct = noteName.takeLastWhile { it.isDigit() }
            noteName.dropLast(oct.length) to oct
        }
    }

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
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = (-2).sp
            )
        }

        if (octave.isNotEmpty()) {
            Text(
                text = octave,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = color.copy(alpha = color.alpha * 0.6f),
                modifier = Modifier.padding(bottom = 14.dp, start = 2.dp)
            )
        }
    }
}

private val NOTE_TWEEN: FiniteAnimationSpec<Float> = tween(120)
