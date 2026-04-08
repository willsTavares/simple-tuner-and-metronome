package com.pitchandmetronome.domain.model.metronome

/**
 * Perfil sonoro do click do metrônomo.
 *
 * Cada variante corresponde a um arquivo de áudio PCM curto (< 50ms)
 * embutido nos assets nativos e carregado no engine durante a inicialização.
 *
 * @param assetFileName Nome do arquivo PCM raw em `src/main/assets/sounds/`.
 */
enum class SoundProfile(val assetFileName: String) {
    CLICK(assetFileName = "click.raw"),
    BEEP(assetFileName = "beep.raw"),
    WOOD(assetFileName = "wood.raw"),
    COWBELL(assetFileName = "cowbell.raw")
}
