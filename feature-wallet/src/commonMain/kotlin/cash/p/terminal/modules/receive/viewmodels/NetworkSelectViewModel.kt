package cash.p.terminal.modules.receive.viewmodels

import cash.p.terminal.wallet.IAdapterManager
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.eligibleTokens
import cash.p.terminal.core.utils.Utils
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.useCases.WalletUseCase
import org.koin.java.KoinJavaComponent.inject

class NetworkSelectViewModel(
    val activeAccount: Account,
    val fullCoin: FullCoin,
    private val adapterManager: IAdapterManager,
) : ViewModel() {
    val eligibleTokens = fullCoin.eligibleTokens(activeAccount.type)

    private val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)
    private val chainSupport: PlatformChainSupport by inject(PlatformChainSupport::class.java)

    fun isSupported(token: Token) = chainSupport.isSupported(token, activeAccount)

    suspend fun getOrCreateWallet(token: Token): Wallet? {
        return walletUseCase.getWallet(token)
            ?: createWallet(token)
    }

    private suspend fun createWallet(token: Token): Wallet? {
        val wallet = walletUseCase.createWalletIfNotExists(token) ?: return null

        Utils.waitUntil(1000L, 100L) {
            adapterManager.getReceiveAdapterForWallet(wallet) != null
        }

        return wallet
    }
}
