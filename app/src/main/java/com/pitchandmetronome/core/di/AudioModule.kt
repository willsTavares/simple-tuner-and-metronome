package com.pitchandmetronome.core.di

import com.pitchandmetronome.audio.metronome.IMetronomeEngine
import com.pitchandmetronome.audio.metronome.MetronomeEngineImpl
import com.pitchandmetronome.audio.tuner.AudioCaptureEngine
import com.pitchandmetronome.audio.tuner.IPitchDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para as engines de áudio.
 *
 * Ambas as engines são Singleton porque cada uma gerencia um único stream de
 * áudio — múltiplos streams concorrentes aumentariam latência e consumo de energia.
 *
 * ### Implementações disponíveis para [IPitchDetector]:
 * - [AudioCaptureEngine] — ativa; usa `AudioRecord` (API Java), sem NDK. Funciona
 *   em qualquer dispositivo Android ≥ API 26 com permissão RECORD_AUDIO.
 * - `PitchDetectorImpl` — futura; usa AAudio via Oboe (NDK). Oferece latência
 *   menor. Trocar o binding abaixo quando o NDK estiver integrado.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    /** Liga a interface [IMetronomeEngine] à implementação JNI [MetronomeEngineImpl]. */
    @Binds
    @Singleton
    abstract fun bindMetronomeEngine(impl: MetronomeEngineImpl): IMetronomeEngine

    /**
     * Liga [IPitchDetector] à implementação [AudioCaptureEngine] (AudioRecord + YIN Kotlin).
     * Para trocar para a implementação NDK futura, substitua o parâmetro por `PitchDetectorImpl`.
     */
    @Binds
    @Singleton
    abstract fun bindPitchDetector(impl: AudioCaptureEngine): IPitchDetector
}
