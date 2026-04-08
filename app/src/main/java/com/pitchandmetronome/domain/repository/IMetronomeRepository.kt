package com.pitchandmetronome.domain.repository

import com.pitchandmetronome.domain.model.metronome.MetronomeState
import com.pitchandmetronome.domain.model.metronome.SoundProfile
import com.pitchandmetronome.domain.model.metronome.TimeSignature
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de acesso ao estado e preferências do metrônomo.
 *
 * A camada domain programa para esta interface — nunca para a implementação
 * concreta em [data]. Isso garante que os use cases sejam testáveis sem
 * qualquer dependência de Android ou DataStore.
 */
interface IMetronomeRepository {

    /**
     * Emite o [MetronomeState] atual e todas as atualizações subsequentes.
     * Nunca completa enquanto o app estiver em foreground.
     */
    fun observeState(): Flow<MetronomeState>

    /** Retorna o estado atual sem observar mudanças futuras. */
    suspend fun getState(): MetronomeState

    /** Atualiza o BPM e persiste o valor. */
    suspend fun updateBpm(bpm: Int)

    /** Atualiza a fórmula de compasso e persiste o valor. */
    suspend fun updateTimeSignature(timeSignature: TimeSignature)

    /** Atualiza o perfil sonoro e persiste o valor. */
    suspend fun updateSoundProfile(soundProfile: SoundProfile)

    /** Atualiza o flag de acento no primeiro beat. */
    suspend fun updateAccentFirstBeat(accent: Boolean)

    /** Marca o metrônomo como em reprodução ou parado. */
    suspend fun setPlaying(isPlaying: Boolean)
}
