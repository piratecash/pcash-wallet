package cash.p.terminal.modules.tor

import cash.p.terminal.core.ITorManager
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.manager.ITorConnectionStatusUseCase
import cash.p.terminal.manager.ITorConnectionStatusUseCase.*
import cash.p.terminal.manager.TorViewState
import cash.p.terminal.modules.settings.security.tor.TorStatus
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TorPage_Connecting
import cash.p.terminal.resources.TorPage_Failed
import cash.p.terminal.resources.Tor_TorIsActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TorConnectionStatusUseCase(
    private val torManager: ITorManager,
    private val connectivityManager: IConnectivityManager
): ITorConnectionStatusUseCase {

    override val torViewState: Flow<TorViewState> =
        combine(torManager.torStatusFlow, torManager.bootstrapProgressFlow, ::createTorViewState)

    override fun restartTor(): RestartResult {
        return if (connectivityManager.isConnected.value) {
            torManager.start()
            RestartResult.Success
        } else {
            RestartResult.NoNetworkConnection
        }
    }

    private fun createTorViewState(torStatus: TorStatus, bootstrapProgress: Int?): TorViewState {
        val isConnected = connectivityManager.isConnected.value
        val torIsActive = torStatus == TorStatus.Connected
        val showRetryButton = torStatus == TorStatus.Failed
        val showNoNetworkConnectionError = torStatus != TorStatus.Connected && !isConnected

        val stateText = when (torStatus) {
            TorStatus.Connected -> Res.string.Tor_TorIsActive
            TorStatus.Failed -> Res.string.TorPage_Failed
            else -> Res.string.TorPage_Connecting
        }

        return TorViewState(
            stateText = stateText,
            showRetryButton = showRetryButton,
            torIsActive = torIsActive,
            showNetworkConnectionError = showNoNetworkConnectionError,
            bootstrapProgress = bootstrapProgress
        )
    }
}

