package cash.p.terminal.core.managers

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.DispatcherProvider

class ThorchainAccountManagers(
    thorchainKitManagers: ThorchainKitManagers,
    accountManager: IAccountManager,
    walletManager: IWalletManager,
    tokenAutoEnableManager: TokenAutoEnableManager,
    userDeletedWalletManager: UserDeletedWalletManager,
    marketKit: MarketKitWrapper,
    dispatcherProvider: DispatcherProvider,
) {
    val all = thorchainKitManagers.all.map { thorchainKitManager ->
        ThorchainAccountManager(
            accountManager = accountManager,
            walletManager = walletManager,
            thorchainKitManager = thorchainKitManager,
            tokenAutoEnableManager = tokenAutoEnableManager,
            userDeletedWalletManager = userDeletedWalletManager,
            marketKit = marketKit,
            dispatcherProvider = dispatcherProvider,
        )
    }
}
