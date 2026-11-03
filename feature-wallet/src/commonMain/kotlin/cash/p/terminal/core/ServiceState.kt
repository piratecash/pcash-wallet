package cash.p.terminal.core

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

abstract class ServiceState<T> {

    private val _stateFlow = MutableSharedFlow<T>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Emit initial state to avoid exception in ServiceStateFlow.value. The flow itself is not lazy:
    // createState may run an event loop (runBlocking) that re-enters this initializer on the same thread.
    private val initialStatePublished by lazy { _stateFlow.tryEmit(createState()) }

    val stateFlow: ServiceStateFlow<T>
        get() {
            initialStatePublished
            return ServiceStateFlow(_stateFlow.asSharedFlow())
        }

    protected abstract fun createState(): T

    protected fun emitState() {
        initialStatePublished
        _stateFlow.tryEmit(createState())
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class ServiceStateFlow<T>(
    private val sharedFlow: SharedFlow<T>
) : SharedFlow<T> by sharedFlow {
    val value: T
        get() = sharedFlow.replayCache.first()
}
