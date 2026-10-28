package cash.p.terminal.modules.balance

import cash.p.terminal.core.IRateAppManager
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.modules.walletconnect.list.WalletConnectListModule
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AndroidBalancePlatformActions(
    private val tonConnectManager: TonConnectManager,
    private val rateAppManager: IRateAppManager,
    pinComponent: IPinComponent,
) : BalancePlatformActions {

    override val appLocked: StateFlow<Boolean> = pinComponent.isLockedFlow

    override val connectionsSupported = true

    override fun openConnectionLink(
        text: String,
        scope: CoroutineScope,
        onPairingResult: (paired: Boolean) -> Unit,
    ): Boolean = when {
        WalletConnectListModule.getVersionFromUri(text) == 2 -> {
            WalletKit.pair(
                Wallet.Params.Pair(text.trim()),
                onSuccess = { onPairingResult(true) },
                onError = { onPairingResult(false) },
            )
            true
        }

        text.startsWith("tc://") -> {
            scope.launch { tonConnectManager.handle(text, false) }
            true
        }

        else -> false
    }

    override fun isOfflineTransaction(text: String): Boolean =
        OfflineTransactionPayloadEncoder.isOfflineTransactionPayload(text) ||
            OfflineTransactionPayloadEncoder.isRawTransactionHex(text)

    override fun onBalancePageActive() = rateAppManager.onBalancePageActive()

    override fun onBalancePageInactive() = rateAppManager.onBalancePageInactive()
}
