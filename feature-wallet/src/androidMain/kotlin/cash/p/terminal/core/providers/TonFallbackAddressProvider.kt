package cash.p.terminal.core.providers

import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.tonAsset
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class TonFallbackAddressProvider(
    private val tonKitManager: TonKitManager,
    private val dispatcherProvider: DispatcherProvider,
) : FallbackAddressProvider {

    override suspend fun getAddress(wallet: Wallet): String? {
        val version = wallet.token.tonAsset?.version ?: return null
        return tryOrNull {
            withContext(dispatcherProvider.io) { tonKitManager.getReceiveAddress(wallet.account, version) }
        }
    }
}
