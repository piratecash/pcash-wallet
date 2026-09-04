package cash.p.terminal.modules.watchaddress.selectblockchains

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.modules.watchaddress.WatchAddressService
import org.koin.java.KoinJavaComponent.inject

object SelectBlockchainsModule {
    class Factory(val accountType: cash.p.terminal.wallet.AccountType, val accountName: String?) :
        ViewModelProvider.Factory {
        private val restoreSettingsManager: RestoreSettingsManager by inject(RestoreSettingsManager::class.java)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val service = WatchAddressService(
                App.accountManager, App.walletActivator, App.accountFactory, App.marketKit,
                App.evmBlockchainManager, restoreSettingsManager
            )
            return SelectBlockchainsViewModel(accountType, accountName, service) as T
        }
    }
}
