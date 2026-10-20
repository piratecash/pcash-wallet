package cash.p.terminal.modules.restoreaccount.restoreblockchains

import cash.p.terminal.core.getKoinInstance
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import kotlin.reflect.KClass
import cash.p.terminal.core.managers.LitecoinBirthdayProvider
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.modules.enablecoin.blockchaintokens.BlockchainTokensService
import cash.p.terminal.modules.enablecoin.blockchaintokens.BlockchainTokensViewModel
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.DispatcherProvider

object RestoreBlockchainsModule {

    class Factory(
        private val accountName: String,
        private val accountType: AccountType,
        private val manualBackup: Boolean,
        private val fileBackup: Boolean
    ) : ViewModelProvider.Factory {

        private val restoreSettingsManager: RestoreSettingsManager by lazy { getKoinInstance<RestoreSettingsManager>() }
        private val zcashBirthdayProvider: ZcashBirthdayProvider by lazy { getKoinInstance<ZcashBirthdayProvider>() }
        private val litecoinBirthdayProvider: LitecoinBirthdayProvider by lazy {
            getKoinInstance<LitecoinBirthdayProvider>()
        }
        private val dispatcherProvider: DispatcherProvider by lazy { getKoinInstance<DispatcherProvider>() }
        private val restoreSettingsService by lazy {
            RestoreSettingsService(restoreSettingsManager, zcashBirthdayProvider, litecoinBirthdayProvider)
        }
        private val blockchainTokensService by lazy {
            BlockchainTokensService()
        }

        private val restoreSelectCoinsService by lazy {
            RestoreBlockchainsService(
                accountName,
                accountType,
                manualBackup,
                fileBackup,
                getKoinInstance<IAccountFactory>(),
                getKoinInstance<IAccountManager>(),
                getKoinInstance<IWalletManager>(),
                getKoinInstance<MarketKitWrapper>(),
                getKoinInstance<TokenAutoEnableManager>(),
                blockchainTokensService,
                restoreSettingsService,
                dispatcherProvider
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            return when (modelClass) {
                RestoreSettingsViewModel::class -> {
                    RestoreSettingsViewModel(
                        restoreSettingsService,
                        listOf(restoreSettingsService)
                    ) as T
                }
                RestoreBlockchainsViewModel::class -> {
                    RestoreBlockchainsViewModel(
                        restoreSelectCoinsService,
                        listOf(restoreSelectCoinsService)
                    ) as T
                }
                BlockchainTokensViewModel::class -> {
                    BlockchainTokensViewModel(blockchainTokensService) as T
                }
                else -> throw IllegalArgumentException()
            }
        }
    }
}

data class CoinViewItem<T>(
    val item: T,
    val imageSource: ImageSource,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val hasSettings: Boolean = false,
    val hasInfo: Boolean = false,
    val label: String? = null,
)
