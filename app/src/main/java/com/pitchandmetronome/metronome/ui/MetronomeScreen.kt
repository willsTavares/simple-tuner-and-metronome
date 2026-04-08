package com.pitchandmetronome.metronome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitchandmetronome.metronome.MetronomeViewModel

/** Espaço reservado para a cápsula de navegação flutuante + margem. */
private val NAV_CAPSULE_RESERVED = 80.dp

@Composable
fun MetronomeScreen(
    viewModel: MetronomeViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    // Ref atualizada a cada recomposição para o onDispose capturar o valor corrente
    val currentIsPlaying = rememberUpdatedState(uiState.isPlaying)

    // Auto-stop metrônomo ao sair da tela
    DisposableEffect(Unit) {
        onDispose {
            if (currentIsPlaying.value) {
                viewModel.onPlayPause()
            }
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(bgColor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = NAV_CAPSULE_RESERVED),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title — fixo no topo
            Text(
                text = "Metronome",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Conteúdo centralizado — mais para o topo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.weight(0.3f))

                // ── Time Signature picker ────────────────────────────────
                TimeSignaturePickerComponent(
                    selected = uiState.timeSignature,
                    onBeatsPerMeasureChange = viewModel::onBeatsPerMeasureChange,
                    onBeatUnitChange = viewModel::onBeatUnitChange,
                    onTimeSignatureChange = viewModel::onTimeSignatureChange
                )

                Spacer(Modifier.height(16.dp))

                // ── Beat visualizer ──────────────────────────────────────
                BeatVisualizerComponent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    beatsPerMeasure = uiState.timeSignature.beatsPerMeasure,
                    lastBeatEvent = uiState.lastBeatEvent,
                    isPlaying = uiState.isPlaying
                )

                Spacer(Modifier.height(20.dp))

                // ── BPM Dial ─────────────────────────────────────────────
                BpmDialComponent(
                    bpm = uiState.bpm,
                    onBpmChange = viewModel::onBpmChange
                )

                Spacer(Modifier.height(28.dp))

                // ── Bottom controls: Play | — BPM + | TAP ───────────────
                BpmControlBar(
                    modifier = Modifier.fillMaxWidth(),
                    bpm = uiState.bpm,
                    isPlaying = uiState.isPlaying,
                    isLoading = uiState.isLoading,
                    onBpmChange = viewModel::onBpmChange,
                    onPlayPause = viewModel::onPlayPause,
                    onTapTempo = viewModel::onTapTempo
                )

                Spacer(Modifier.weight(0.85f))
            }
        }
    }
}
