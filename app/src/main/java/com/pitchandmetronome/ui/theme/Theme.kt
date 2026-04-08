package com.pitchandmetronome.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// ── Dark colour scheme — primary target is stage / low-light use ──────────────
//
// Background near-black (0x0E1117): reduces eye fatigue on stage; avoids the
// blueish cast of pure black on OLED (pixel shift artifact).
// Primary azul-suave 82AAFF: readable against the dark bg, not too saturated.
private val AppDarkColorScheme = darkColorScheme(
    primary            = Color(0xFF82AAFF),
    onPrimary          = Color(0xFF00224B),
    primaryContainer   = Color(0xFF00337A),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary          = Color(0xFF6BCB77),
    onSecondary        = Color(0xFF003A03),
    secondaryContainer = Color(0xFF00530A),
    onSecondaryContainer = Color(0xFF89FF85),
    tertiary           = Color(0xFFFFB3B3),
    onTertiary         = Color(0xFF680004),
    tertiaryContainer  = Color(0xFF930009),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background         = Color(0xFF010409),  // GitHub dark — near-pure black
    onBackground       = Color(0xFFE6EDF3),
    surface            = Color(0xFF0D1117),  // GitHub dark surface
    onSurface          = Color(0xFFE6EDF3),
    surfaceVariant     = Color(0xFF161B22),  // GitHub dark elevated surface
    onSurfaceVariant   = Color(0xFFC9D1D9),
    outline            = Color(0xFF30363D),  // GitHub dark border
    outlineVariant     = Color(0xFF21262D),  // GitHub dark subtle border
    error              = Color(0xFFCF6679),
    onError            = Color(0xFF680003),
)

// ── Light colour scheme — rehearsal room / bright environment ─────────────────
private val AppLightColorScheme = lightColorScheme(
    primary            = Color(0xFF1A56DB),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001947),
    secondary          = Color(0xFF1A6B1A),
    onSecondary        = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDF7BD),
    onSecondaryContainer = Color(0xFF002200),
    tertiary           = Color(0xFFC11B17),
    onTertiary         = Color(0xFFFFFFFF),
    tertiaryContainer  = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410002),
    background         = Color(0xFFF5F6FC),
    onBackground       = Color(0xFF181B24),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF181B24),
    surfaceVariant     = Color(0xFFE4E7F2),
    onSurfaceVariant   = Color(0xFF44475A),
    outline            = Color(0xFFB4B7CB),
    outlineVariant     = Color(0xFFD6D8E8),
    error              = Color(0xFFB3261E),
    onError            = Color(0xFFFFFFFF),
)

// ── Extended app colours (metronome accent, beat indicators) ──────────────────

/**
 * Cores extras do app que não fazem parte do Material 3 [ColorScheme].
 * Acessadas via [LocalAppColors] dentro do [PitchAndMetronomeTheme].
 */
@Immutable
data class AppColors(
    /** Accent do metrônomo — arco de progresso, botão play ativo, seletor de compasso. */
    val metronomeAccent: Color,
    /** Variante escura do accent — ponta do gradiente do arco. */
    val metronomeAccentDark: Color,
    /** Cor dim dos blocos de beat inativos. */
    val beatDim: Color,
    /** Container sutil para botões selecionados com accent do metrônomo. */
    val metronomeAccentContainer: Color,
)

private val DarkAppColors = AppColors(
    metronomeAccent          = Color(0xFFD4A853),
    metronomeAccentDark      = Color(0xFF8B6914),
    beatDim                  = Color(0xFF3D3220),
    metronomeAccentContainer = Color(0xFFD4A853).copy(alpha = 0.15f),
)

private val LightAppColors = AppColors(
    metronomeAccent          = Color(0xFFB8860B),
    metronomeAccentDark      = Color(0xFF7A5A08),
    beatDim                  = Color(0xFFE8DCC8),
    metronomeAccentContainer = Color(0xFFB8860B).copy(alpha = 0.12f),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// ── Tuning-state colours ───────────────────────────────────────────────────────
//
// These are DOMAIN colours, not theme-dependent — green always means "in tune",
// red always means "out of tune", regardless of light/dark mode. They are used
// directly by TuningNeedle, NoteDisplay and TunerIndicator.
object TuneColors {
    /** Afinado — desvio ≤ 5 cents. */
    val InTune    = Color(0xFF4CAF50)

    /** Próximo — desvio ≤ 20 cents. */
    val Close     = Color(0xFFFF9800)

    /** Fora — desvio > 20 cents. */
    val OutOfTune = Color(0xFFE53935)

    /**
     * Retorna a cor semântica correspondente ao desvio em cents.
     * Função pura — pode ser chamada fora de contexto Composable.
     */
    fun forCents(centsDeviation: Float): Color = when {
        abs(centsDeviation) <= 5f  -> InTune
        abs(centsDeviation) <= 20f -> Close
        else                        -> OutOfTune
    }
}

// ── Theme mode ────────────────────────────────────────────────────────────────

/**
 * Modo de tema do aplicativo.
 *
 * Armazenado no DataStore (via [AppModule]) e lido pelo host que compõe
 * [PitchAndMetronomeTheme]. A UI de alternância pode ser adicionada nas
 * Configurações sem alterar nenhuma outra camada.
 */
enum class AppThemeMode { LIGHT, DARK, SYSTEM }

// ── Root composable ───────────────────────────────────────────────────────────

/**
 * Tema Material 3 raiz do aplicativo.
 *
 * - [AppThemeMode.SYSTEM]: segue `isSystemInDarkTheme()` do dispositivo.
 * - [AppThemeMode.DARK]: força esquema escuro (padrão recomendado para palco).
 * - [AppThemeMode.LIGHT]: força esquema claro (leitura com luz ambiente).
 *
 * @param themeMode Modo de tema requerido — pode ser lido de DataStore.
 * @param forceDark Sobrescrita para screenshots e previews.
 */
@Composable
fun PitchAndMetronomeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = forceDark || when (themeMode) {
        AppThemeMode.LIGHT  -> false
        AppThemeMode.DARK   -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) AppDarkColorScheme else AppLightColorScheme
    val appColors = if (useDark) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
