package com.pitchandmetronome.tuner.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pitchandmetronome.tuner.TunerViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TunerScreen(
    viewModel: TunerViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (micPermission.status.isGranted) viewModel.onPermissionGranted()
    }

    // Auto-start tuner assim que a permissão for concedida
    LaunchedEffect(uiState.hasAudioPermission) {
        if (uiState.hasAudioPermission && !uiState.isListening) {
            viewModel.onStartTuner()
        }
    }

    // Auto-stop ao sair da tela — rememberUpdatedState garante valor atualizado no onDispose
    val currentIsListening by rememberUpdatedState(uiState.isListening)
    DisposableEffect(Unit) {
        onDispose {
            if (currentIsListening) {
                viewModel.onStopTuner()
            }
        }
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(bgColor) }
    ) {
        if (!uiState.hasAudioPermission) {
            // Permission request screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MicOff,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Microfone necessário",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "O afinador precisa do microfone para detectar a frequência do instrumento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { micPermission.launchPermissionRequest() }) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Conceder permissão")
                }
            }
        } else {
            // Main tuner UI: keep header/top controls at top, center only the tuner block
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title at top
                Text(
                    text = "Tuner",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Seletor de referência A4 — abaixo do título, centralizado
                ReferenceA4Selector(
                    referenceA4 = uiState.referenceA4,
                    onReferenceA4Change = viewModel::onReferenceA4Change
                )

                Spacer(Modifier.height(12.dp))

                // Center only the tuner block in the remaining space
                Spacer(Modifier.weight(0.35f))

                // O bloco do afinador agrupado em uma Column para centralizar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Exibição da nota
                    NoteDisplay(
                        noteName = uiState.detectedNote,
                        centsDeviation = uiState.centsDeviation,
                        isListening = uiState.isListening
                    )

                    Spacer(Modifier.height(8.dp))

                    // Texto de status
                    TunerIndicator(
                        isListening = uiState.isListening,
                        confidence = uiState.confidence,
                        centsDeviation = uiState.centsDeviation
                    )

                    Spacer(Modifier.height(4.dp))

                    // Frequência
                    FrequencyDisplay(
                        frequency = uiState.detectedFrequency,
                        centsDeviation = uiState.centsDeviation,
                        isListening = uiState.isListening,
                        confidence = uiState.confidence
                    )

                    Spacer(Modifier.height(24.dp))

                    // Barra vertical
                    TuningNeedle(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        centsDeviation = uiState.centsDeviation,
                        confidence = uiState.confidence
                    )
                }

                Spacer(Modifier.weight(1f))
             }
         }
     }
 }

/**
 * Seletor compacto de frequência de referência A4.
 *
 * Exibe o valor atual (ex: "A4 = 440 Hz") com botões −/+ para ajustar em 1 Hz.
 * Faixa permitida: 420–460 Hz (cobre os padrões mais comuns: 432, 440, 443, etc.)
 */
@Composable
private fun ReferenceA4Selector(
    referenceA4: Float,
    onReferenceA4Change: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayHz = referenceA4.toInt()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onReferenceA4Change((referenceA4 - 1f).coerceAtLeast(420f)) },
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Diminuir referência A4",
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = "A4 = $displayHz Hz",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        IconButton(
            onClick = { onReferenceA4Change((referenceA4 + 1f).coerceAtMost(460f)) },
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Aumentar referência A4",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
