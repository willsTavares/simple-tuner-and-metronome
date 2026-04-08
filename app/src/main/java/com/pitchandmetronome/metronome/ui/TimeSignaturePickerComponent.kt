package com.pitchandmetronome.metronome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import com.pitchandmetronome.ui.theme.LocalAppColors

/**
 * Compact time signature button that opens a centered dialog with presets
 * and stepper controls for free editing of numerator/denominator.
 */
@Composable
fun TimeSignaturePickerComponent(
    modifier: Modifier = Modifier,
    selected: TimeSignature,
    onBeatsPerMeasureChange: (Int) -> Unit,
    onBeatUnitChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit
) {
    val appColors = LocalAppColors.current
    val goldAccent = appColors.metronomeAccent

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Compact pill button showing current time signature
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.height(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(64.dp)
                    .height(40.dp)
            ) {
                Text(
                    text = selected.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Dialog centralizado na tela
        if (expanded) {
            Dialog(onDismissRequest = { expanded = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Compasso",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            StepperControl(
                                value = selected.beatsPerMeasure,
                                onDecrement = {
                                    if (selected.beatsPerMeasure > TimeSignature.BEATS_RANGE.first) {
                                        onBeatsPerMeasureChange(selected.beatsPerMeasure - 1)
                                    }
                                },
                                onIncrement = {
                                    if (selected.beatsPerMeasure < TimeSignature.BEATS_RANGE.last) {
                                        onBeatsPerMeasureChange(selected.beatsPerMeasure + 1)
                                    }
                                }
                            )

                            Text(
                                text = "/",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            val validUnits = TimeSignature.VALID_BEAT_UNITS
                            val currentUnitIndex = validUnits.indexOf(selected.beatUnit)

                            StepperControl(
                                value = selected.beatUnit,
                                onDecrement = {
                                    if (currentUnitIndex > 0) {
                                        onBeatUnitChange(validUnits[currentUnitIndex - 1])
                                    }
                                },
                                onIncrement = {
                                    if (currentUnitIndex < validUnits.lastIndex) {
                                        onBeatUnitChange(validUnits[currentUnitIndex + 1])
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Preset shortcuts as a grid row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeSignature.COMMON_PRESETS.forEach { preset ->
                                val isSelected = preset == selected
                                Surface(
                                    onClick = {
                                        onTimeSignatureChange(preset)
                                        expanded = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected)
                                        appColors.metronomeAccentContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(30.dp)
                                    ) {
                                        Text(
                                            text = preset.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) goldAccent
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperControl(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onDecrement,
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Diminuir",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = onIncrement,
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Aumentar",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
