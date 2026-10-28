package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.modules.multiswap.action.ActionActivateStellarAsset
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.Network
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.stellarkit.room.StellarAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class StellarTrustlineActionResolver(
    private val walletUseCase: WalletUseCase,
    private val adapterManager: IAdapterManager,
    private val stellarKitManager: StellarKitManager,
    private val offlineOperationGate: OfflineOperationGate,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("StellarTrustlineActionResolver")

    /** Step the local wallet needs before any provider can pay the final token into it; null when none or unknown. */
    suspend fun resolve(provider: IMultiSwapProvider, tokenOut: Token): ISwapProviderAction? {
        if (tokenOut.blockchainType != BlockchainType.Stellar) return null
        val asset = tokenOut.type as? TokenType.Asset ?: return null
        provider.getCreateTokenActionRequired(listOf(tokenOut))?.let { return it }
        val wallet = walletUseCase.getWallet(tokenOut) ?: return null
        if (offlineOperationGate.isBlocked(wallet)) return null
        return ActionActivateStellarAsset(wallet).takeIf { isTrustlineMissing(wallet, asset) }
    }

    private suspend fun isTrustlineMissing(wallet: Wallet, asset: TokenType.Asset): Boolean = try {
        !isTrustlineEstablished(wallet, asset)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.w(e) { "Trustline check failed, not blocking the swap" }
        false
    }

    private suspend fun isTrustlineEstablished(wallet: Wallet, asset: TokenType.Asset): Boolean {
        val adapter = adapterManager.getAdapterForWallet<StellarAssetAdapter>(wallet)
        if (adapter != null) return adapter.isTrustlineEstablished()
        return withContext(dispatcherProvider.io) {
            StellarKit.isAssetEnabled(
                Network.MainNet,
                StellarAsset.Asset(asset.code, asset.issuer),
                stellarKitManager.getAddress(wallet.account),
            )
        }
    }
}
