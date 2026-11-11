package cash.p.terminal.core.tor

import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.modules.settings.security.tor.TorStatus
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.isAdapterStopped
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hides sync errors while Tor (re)connects, then refreshes the wallets that failed meanwhile.
 * Wallet dependencies are lazy: this is created at Koin start, before the wallet stack may be built.
 */
class TorSyncCoordinator(
    private val torManager: ITorManager,
    private val adapterManager: Lazy<IAdapterManager>,
    private val walletManager: Lazy<IWalletManager>,
    private val offlineModeManager: Lazy<OfflineModeManager>,
    dispatcherProvider: DispatcherProvider,
) : TorSyncState {

    private val logger = Logger.withTag("TorSyncCoordinator")
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    private val _syncErrorsHidden = MutableStateFlow(false)
    override val syncErrorsHidden: StateFlow<Boolean> = _syncErrorsHidden.asStateFlow()

    init {
        if (torManager.isTorEnabled) {
            scope.launch { torManager.torStatusFlow.collectLatest(::onTorStatus) }
        }
    }

    override suspend fun requestNewCircuits() = torManager.newCircuits()

    private suspend fun onTorStatus(status: TorStatus) {
        try {
            when (status) {
                TorStatus.Connecting -> _syncErrorsHidden.value = true
                TorStatus.Connected -> recoverAfterConnect()
                TorStatus.Failed, TorStatus.Closed -> _syncErrorsHidden.value = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Recovery after Tor status $status failed" }
            _syncErrorsHidden.value = false
        }
    }

    private suspend fun recoverAfterConnect() = coroutineScope {
        _syncErrorsHidden.value = true
        // Not awaited: a hardware Monero sync can last the whole scan, and the warm-up must not wait for it
        launch { adapterManager.value.refresh(attended = false) }
        delay(WARM_UP_MILLIS)
        val failed = walletManager.value.activeWallets.filter(::needsRetry)
        if (failed.isNotEmpty()) {
            // A kit can keep failing on an exit that refuses it, so it retries on fresh circuits
            torManager.newCircuits()
            failed.forEach { adapterManager.value.refreshByWallet(it, attended = false) }
            delay(RETRY_SETTLE_MILLIS)
        }
        _syncErrorsHidden.value = false
    }

    private fun needsRetry(wallet: Wallet): Boolean {
        val state = adapterManager.value.getBalanceAdapterForWallet(wallet)?.balanceState
        return state is AdapterState.NotSynced &&
            !state.isAdapterStopped &&
            !offlineModeManager.value.isNetworkPaused(wallet.account, wallet.token.blockchainType)
    }

    private companion object {
        const val WARM_UP_MILLIS = 20_000L
        const val RETRY_SETTLE_MILLIS = 10_000L
    }
}
