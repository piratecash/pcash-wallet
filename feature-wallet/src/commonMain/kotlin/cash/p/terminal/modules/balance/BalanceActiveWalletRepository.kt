package cash.p.terminal.modules.balance

import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.isLegacyZcash
import cash.p.terminal.wallet.isZcashAddressSpec
import cash.p.terminal.wallet.zcashDisableTokenQueryIds
import io.reactivex.Observable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx2.asObservable

class BalanceActiveWalletRepository(
    private val walletManager: IWalletManager,
    private val accountManager: IAccountManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val offlineModeUseCase: OfflineModeUseCase,
    evmSyncSourceManager: EvmSyncSourceManager
) {

    val itemsObservable: Observable<List<Wallet>> =
        Observable
            .merge(
                Observable.just(Unit),
                walletManager.activeWalletsFlow.asObservable(),
                walletManager.activeWalletsAccountIdFlow.map { }.asObservable(),
                evmSyncSourceManager.syncSourceObservable
            )
            // Until the active account's wallets are loaded an empty list would read as "no coins".
            .filter { walletManager.activeWalletsAccountIdFlow.value == accountManager.activeAccount?.id }
            .map {
                walletManager.activeWallets
            }

    suspend fun disable(wallet: Wallet) {
        if (wallet.token.isZcashAddressSpec || wallet.token.tokenQuery.isLegacyZcash) {
            userDeletedWalletManager.markAsDeleted(wallet.account.id, zcashDisableTokenQueryIds)
            walletManager.deleteByTokenQueryIds(wallet.account.id, zcashDisableTokenQueryIds)
        } else {
            userDeletedWalletManager.markAsDeleted(wallet)
            walletManager.deleteByWallet(wallet)
        }
        offlineModeUseCase.resetIfBlockchainRemoved(wallet.account, wallet.token.blockchainType)
    }

}
