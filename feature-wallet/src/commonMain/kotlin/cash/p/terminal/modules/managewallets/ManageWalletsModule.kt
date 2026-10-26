package cash.p.terminal.modules.managewallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.LitecoinBirthdayProvider
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import kotlin.reflect.KClass

object ManageWalletsModule {

    class Factory : ViewModelProvider.Factory {

        private val restoreSettingsManager: RestoreSettingsManager by lazy { getKoinInstance<RestoreSettingsManager>() }
        private val zcashBirthdayProvider: ZcashBirthdayProvider by lazy { getKoinInstance<ZcashBirthdayProvider>() }
        private val litecoinBirthdayProvider: LitecoinBirthdayProvider by lazy {
            getKoinInstance<LitecoinBirthdayProvider>()
        }
        private val userDeletedWalletManager: UserDeletedWalletManager by lazy {
            getKoinInstance<UserDeletedWalletManager>()
        }
        private val rescanMoneroUseCase: RescanMoneroUseCase by lazy { getKoinInstance<RescanMoneroUseCase>() }
        private val accountManager: IAccountManager by lazy { getKoinInstance<IAccountManager>() }
        private val restoreSettingsService by lazy {
            RestoreSettingsService(restoreSettingsManager, zcashBirthdayProvider, litecoinBirthdayProvider)
        }

        private val manageWalletsService by lazy {
            val activeAccount = accountManager.activeAccount
            ManageWalletsService(
                walletManager = getKoinInstance<IWalletManager>(),
                restoreSettingsService = restoreSettingsService,
                fullCoinsProvider = accountManager.activeAccount?.let { account ->
                    FullCoinsProvider(getKoinInstance<MarketKitWrapper>(), account)
                },
                account = activeAccount,
                userDeletedWalletManager = userDeletedWalletManager,
                rescanMoneroUseCase = rescanMoneroUseCase,
                accountManager = accountManager,
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            return when (modelClass) {
                RestoreSettingsViewModel::class -> {
                    RestoreSettingsViewModel(restoreSettingsService, listOf(restoreSettingsService)) as T
                }
                ManageWalletsViewModel::class -> {
                    ManageWalletsViewModel(manageWalletsService, listOf(manageWalletsService)) as T
                }
                else -> throw IllegalArgumentException()
            }
        }
    }
}
