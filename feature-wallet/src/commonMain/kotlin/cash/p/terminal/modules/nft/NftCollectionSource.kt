package cash.p.terminal.modules.nft

import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Decides which wallets a collections page shows and which eye it follows. */
sealed interface NftCollectionSource {

    fun walletsFlow(walletManager: IWalletManager): Flow<List<Wallet>>

    fun hiddenFlow(manager: IBalanceHiddenManager): StateFlow<Boolean>

    fun toggleHidden(manager: IBalanceHiddenManager)

    data object AllWallets : NftCollectionSource {
        override fun walletsFlow(walletManager: IWalletManager) = walletManager.activeWalletsFlow

        override fun hiddenFlow(manager: IBalanceHiddenManager) = manager.balanceHiddenFlow

        override fun toggleHidden(manager: IBalanceHiddenManager) = manager.toggleBalanceHidden()
    }

    // Not a data class: Wallet.toString() would print key material.
    class SingleWallet(val wallet: Wallet) : NftCollectionSource {
        override fun walletsFlow(walletManager: IWalletManager) = flowOf(listOf(wallet))

        override fun hiddenFlow(manager: IBalanceHiddenManager) =
            manager.walletBalanceHiddenFlow(wallet.tokenQueryId)

        override fun toggleHidden(manager: IBalanceHiddenManager) =
            manager.toggleWalletBalanceHidden(wallet.tokenQueryId)
    }
}
