package com.pitchandmetronome.domain.repository

import com.pitchandmetronome.domain.model.tuner.TunerConfig

/**
 * Contrato de acesso às preferências do afinador.
 *
 * Mantém separado do [IMetronomeRepository] pelo Princípio da Responsabilidade
 * Única — features diferentes, repositórios diferentes.
 */
interface ITunerRepository {

    /** Retorna a configuração salva do afinador, ou os valores padrão. */
    suspend fun getConfig(): TunerConfig

    /** Persiste a configuração do afinador. */
    suspend fun saveConfig(config: TunerConfig)
}
