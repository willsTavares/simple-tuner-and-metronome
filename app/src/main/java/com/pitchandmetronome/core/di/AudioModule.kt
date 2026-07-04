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
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    /** Liga a interface [IMetronomeEngine] à implementação [MetronomeEngineImpl]. */
    @Binds
    @Singleton
    abstract fun bindMetronomeEngine(impl: MetronomeEngineImpl): IMetronomeEngine

    /** Liga [IPitchDetector] à implementação [AudioCaptureEngine] (AudioRecord + YIN Kotlin). */
    @Binds
    @Singleton
    abstract fun bindPitchDetector(impl: AudioCaptureEngine): IPitchDetector
}
