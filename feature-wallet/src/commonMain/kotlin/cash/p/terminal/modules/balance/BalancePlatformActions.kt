package cash.p.terminal.modules.balance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Balance-tab actions backed by services only a platform has: dApp connections, offline broadcast, rating. */
interface BalancePlatformActions {
    /** Whether the app content is hidden behind the lock screen. */
    val appLocked: StateFlow<Boolean>

    val connectionsSupported: Boolean

    /** Opens a WalletConnect or TON Connect link; `false` when [text] is neither. */
    fun openConnectionLink(text: String, scope: CoroutineScope, onPairingResult: (paired: Boolean) -> Unit): Boolean

    fun isOfflineTransaction(text: String): Boolean

    fun onBalancePageActive()
    fun onBalancePageInactive()
}
