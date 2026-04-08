package com.pitchandmetronome.core.di

import com.pitchandmetronome.data.repository.MetronomeRepositoryImpl
import com.pitchandmetronome.data.repository.TunerRepositoryImpl
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import com.pitchandmetronome.domain.repository.ITunerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt que liga as interfaces de repositório (domain) às suas
 * implementações concretas (data), mantendo o domain desacoplado do Android.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMetronomeRepository(impl: MetronomeRepositoryImpl): IMetronomeRepository

    @Binds
    @Singleton
    abstract fun bindTunerRepository(impl: TunerRepositoryImpl): ITunerRepository
}
