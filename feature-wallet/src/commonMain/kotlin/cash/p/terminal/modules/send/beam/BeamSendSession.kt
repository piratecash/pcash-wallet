package cash.p.terminal.modules.send.beam

import cash.p.beam.BeamQuoteRequest
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

internal class BeamSendSession(
    val wallet: Wallet,
    val session: BeamSessionOwner.Session,
    private val adapter: BeamAdapter,
    private val owner: BeamSessionOwner,
    private val coordinator: BeamSendCoordinator,
    private val offlineModeManager: OfflineModeManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val offlineMutex = Mutex()
    private var resumeAfterOffline: Boolean? = null
    val current: Boolean get() = owner.current === session && adapter.accountId == wallet.account.id
    val canSign: Boolean get() = current && isNativeBeamSendWallet(wallet, session, adapter)

    suspend fun quote(request: BeamQuoteRequest): BeamSendCoordinator.Quote =
        withContext(dispatcherProvider.io) { coordinator.quote(session, request) }

    /** Pauses the network until [releaseOffline], like every other offline step. */
    suspend fun quoteOffline(request: BeamQuoteRequest): BeamSendCoordinator.Quote =
        drained { coordinator.quote(session, request) }

    suspend fun confirm(quote: BeamSendCoordinator.Quote) = withContext(dispatcherProvider.io) {
        check(canSign)
        adapter.withCriticalOperation { coordinator.confirmQuote(quote) }
    }

    suspend fun refreshQuote(quote: BeamSendCoordinator.Quote) = withContext(dispatcherProvider.io) {
        coordinator.refreshQuote(quote)
    }

    suspend fun sign(quote: BeamSendCoordinator.Quote) = drained { coordinator.signOffline(quote) }

    suspend fun abort(operationId: String) = drained { coordinator.abortOffline(session, operationId) }

    fun releaseOffline() {
        dispatcherProvider.applicationScope.launch(dispatcherProvider.io) {
            offlineMutex.withLock {
                val shouldResume = resumeAfterOffline == true
                resumeAfterOffline = null
                val key = OfflineKey(wallet.account.id, BlockchainType.Beam)
                if (shouldResume && current && !isNetworkOff(key)) {
                    adapter.resumeNetwork()
                }
            }
        }
    }

    private fun isNetworkOff(key: OfflineKey) =
        offlineModeManager.isNetworkPaused(key) || offlineModeManager.stateFlow.value[key]?.offline == true

    private suspend fun <T> drained(action: suspend () -> T): T = withContext(dispatcherProvider.io) {
        offlineMutex.withLock {
            check(canSign)
            if (resumeAfterOffline == null) resumeAfterOffline = !adapter.isNetworkPaused
            // Keep the saved context stable until the user leaves the offline flow.
            // Never hold a critical network lease while awaiting this stop acknowledgement.
            adapter.pauseNetworkAndAwait()
            check(current)
            action()
        }
    }
}
