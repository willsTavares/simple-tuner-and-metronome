package com.pitchandmetronome.core.utils

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Wrapper sobre os Dispatchers do Kotlin Coroutines.
 *
 * Injetado via Hilt, essa classe permite que ViewModels e Use Cases recebam
 * dispatchers falsos em testes unitários (ex: [kotlinx.coroutines.test.UnconfinedTestDispatcher]),
 * eliminando a necessidade de `Dispatchers.setMain()` global nos testes.
 */
data class CoroutineDispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher
)
