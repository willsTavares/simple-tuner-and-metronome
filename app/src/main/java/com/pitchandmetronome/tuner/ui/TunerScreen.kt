package com.pitchandmetronome.tuner.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
import com.google.accompanist.permissions.shouldShowRationale
import com.pitchandmetronome.tuner.TunerViewModel
import com.pitchandmetronome.ui.theme.TuneColors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TunerScreen(
    viewModel: TunerViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    // Depois de uma negação, se o sistema não mostra mais o diálogo
    // (!shouldShowRationale), a negação é permanente — pedir de novo não faz nada
    // e o único caminho é a tela de configurações do app.
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        permissionRequested = true
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }
    val permanentlyDenied = permissionRequested &&
        !micPermission.status.isGranted &&
        !micPermission.status.shouldShowRationale

    // Reavaliado quando o status muda — inclusive ao voltar das configurações
    // com a permissão concedida.
    LaunchedEffect(micPermission.status.isGranted) {
        if (micPermission.status.isGranted) viewModel.onPermissionGranted()
    }

    // O microfone só fica ativo com a tela visível: para ao ir para segundo
    // plano (tela desligada, outro app) e volta ao retornar.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inForeground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(uiState.hasAudioPermission, inForeground) {
        if (uiState.hasAudioPermission && inForeground) {
            viewModel.onStartTuner()
        } else {
            viewModel.onStopTuner()
        }
    }

    // Para ao sair da aba do afinador.
    DisposableEffect(Unit) {
        onDispose { viewModel.onStopTuner() }
    }

    val context = LocalContext.current

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
                    text = "O afinador usa o microfone para identificar a nota que você toca. " +
                        "O som é analisado no próprio aparelho e não é gravado nem enviado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (permanentlyDenied) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "A permissão foi negada. Ative o microfone nas configurações do app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(24.dp))
                if (permanentlyDenied) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir configurações")
                    }
                } else {
                    Button(onClick = { micPermission.launchPermissionRequest() }) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Permitir microfone")
                    }
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
                    text = "Afinador",
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

                uiState.errorMessage?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                // Center only the tuner block in the remaining space
                Spacer(Modifier.weight(0.35f))

                // Cor única do estado de afinação — nota, cents, texto e agulha
                // sempre concordam. Sem sinal, a última nota fica esmaecida.
                val onSurface = MaterialTheme.colorScheme.onSurface
                val tuneColor = when {
                    !uiState.isListening || uiState.detectedNote == "--" -> onSurface.copy(alpha = 0.20f)
                    !uiState.hasSignal -> onSurface.copy(alpha = 0.35f)
                    else -> TuneColors.forTuning(uiState.isInTune, uiState.centsDeviation)
                }
                val animatedTuneColor by animateColorAsState(
                    targetValue = tuneColor,
                    animationSpec = tween(200),
                    label = "TuneColor"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NoteDisplay(
                        noteName = uiState.detectedNote,
                        color = animatedTuneColor
                    )

                    CentsDisplay(
                        cents = uiState.centsDeviation,
                        hasSignal = uiState.hasSignal,
                        color = animatedTuneColor
                    )

                    Spacer(Modifier.height(6.dp))

                    TunerIndicator(
                        isListening = uiState.isListening,
                        hasSignal = uiState.hasSignal,
                        isInTune = uiState.isInTune,
                        centsDeviation = uiState.centsDeviation
                    )

                    Spacer(Modifier.height(28.dp))

                    TuningNeedle(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        centsDeviation = uiState.centsDeviation,
                        hasSignal = uiState.hasSignal,
                        isInTune = uiState.isInTune,
                        color = animatedTuneColor
                    )

                    Spacer(Modifier.height(4.dp))

                    FrequencyDisplay(
                        frequency = uiState.detectedFrequency,
                        hasSignal = uiState.hasSignal
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
