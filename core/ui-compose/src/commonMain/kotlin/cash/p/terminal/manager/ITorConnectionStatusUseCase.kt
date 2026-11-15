package cash.p.terminal.manager

import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource

interface ITorConnectionStatusUseCase {

    val torViewState: Flow<TorViewState>

    fun restartTor(): RestartResult

    sealed class RestartResult {
        object Success : RestartResult()
        object NoNetworkConnection : RestartResult()
    }
}

data class TorViewState(
    val stateText: StringResource,
    val showRetryButton: Boolean,
    val torIsActive: Boolean,
    val showNetworkConnectionError: Boolean,
    val bootstrapProgress: Int? = null,
)
