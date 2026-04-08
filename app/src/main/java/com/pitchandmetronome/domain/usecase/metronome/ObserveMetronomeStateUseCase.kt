package com.pitchandmetronome.domain.usecase.metronome

import com.pitchandmetronome.domain.model.metronome.MetronomeState
import com.pitchandmetronome.domain.repository.IMetronomeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Expõe o [MetronomeState] como um [Flow] observável.
 *
 * Encapsula o acesso ao repositório para que o ViewModel não precise
 * depender diretamente de [IMetronomeRepository].
 */
class ObserveMetronomeStateUseCase @Inject constructor(
    private val repository: IMetronomeRepository
) {
    operator fun invoke(): Flow<MetronomeState> = repository.observeState()
}
