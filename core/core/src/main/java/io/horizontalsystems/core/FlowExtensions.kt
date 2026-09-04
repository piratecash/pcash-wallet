package io.horizontalsystems.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Each call gets its own coroutine, so several flows collected in one scope run in parallel. */
fun <T> Flow<T>.collectWith(scope: CoroutineScope, block: (T) -> Unit) {
    scope.launch { collect { block(it) } }
}

fun <T> Flow<T>.onFirstWith(scope: CoroutineScope, block: suspend (T) -> Unit) {
    scope.launch { onEach { block(it) }.first() }
}
