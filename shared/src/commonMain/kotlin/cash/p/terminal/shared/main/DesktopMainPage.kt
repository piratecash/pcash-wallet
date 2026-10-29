package cash.p.terminal.shared.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.balance.ui.BalanceScreen
import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsPage
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsPage
import cash.p.terminal.modules.contacts.ContactsPage
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.modules.market.MarketScreen
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerPage
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.modules.transactions.TransactionsScreen
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.shared.settings.SettingsAction
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.shared.settings.settingsAlertPainter
import cash.p.terminal.shared.settings.SettingsPages
import cash.p.terminal.shared.settings.currentLanguageName
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.showNotSupportedOnPlatform
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.ILanguageManager
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Root of the desktop back stack: the main tabs; pages opened from them stack above it. */
class DesktopMainPage(private val appVersion: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val appearanceStore = koinInject<AppearanceSettingsStore>()
        val mainTabStorage = koinInject<MainTabStorage>()
        val marketsTabEnabled by appearanceStore.marketsTabEnabledFlow.collectAsStateWithLifecycle()
        val destinations = remember(marketsTabEnabled) {
            MainDestination.entries.filter { it != MainDestination.Market || marketsTabEnabled }
        }
        var selected by rememberSaveable {
            mutableStateOf(
                launchDestination(
                    launchPage = appearanceStore.launchPage ?: LaunchPage.Auto,
                    marketsTabEnabled = marketsTabEnabled,
                    lastTab = mainTabStorage.mainTab ?: MainDestination.Balance,
                )
            )
        }
        val selectedDestination = selected.takeIf { it in destinations } ?: MainDestination.Balance
        MainNavigation(
            destinations = destinations,
            selectedDestination = selectedDestination,
            onDestinationSelect = {
                rememberedMainTab(it)?.let { tab -> mainTabStorage.mainTab = tab }
                selected = it
            },
            modifier = Modifier.fillMaxSize(),
        ) { destination ->
            when (destination) {
                MainDestination.Balance -> BalancePane(navigation)
                MainDestination.Market -> MarketScreen(navigation, PaddingValues())
                MainDestination.Transactions -> TransactionsPane(navigation)
                MainDestination.Settings -> SettingsPane(navigation, appVersion)
            }
        }
    }
}

@Composable
private fun BalancePane(navigation: HSNavigation) {
    BalanceScreen(navigation = navigation, paddingValues = PaddingValues())
}

@Composable
private fun TransactionsPane(navigation: HSNavigation) {
    val hud = LocalHudMessenger.current
    TransactionsScreen(
        navigation = navigation,
        paddingValues = PaddingValues(),
        viewModel = rememberMainPageTransactionsViewModel(navigation),
        onShowAllTransactionsClicked = hud::showNotSupportedOnPlatform,
    )
}

@Composable
private fun rememberMainPageTransactionsViewModel(navigation: HSNavigation): TransactionsViewModel =
    // As Android: owned by the main page, which the transaction info page reads it from.
    koinViewModel<TransactionsViewModel>(
        viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(DesktopMainPage::class)
    )

@Composable
private fun SettingsPane(navigation: HSNavigation, appVersion: String) {
    val uiState = rememberSettingsUiState()
    val settingsPages = koinInject<SettingsPages>()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.backgroundBase),
    ) {
        Text(
            text = MainDestinationTitle(MainDestination.Settings),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            style = ComposeAppTheme.typography.title3,
            color = ComposeAppTheme.colors.textPrimary,
        )
        SettingsContent(
            uiState = uiState,
            appVersion = appVersion,
            onAction = { action ->
                when (action) {
                    SettingsAction.ManageWallets ->
                        navigation.slideFromRight(ManageAccountsPage(ManageAccountsModule.Mode.Manage))
                    SettingsAction.BackupManager -> navigation.slideFromRight(BackupManagerPage())
                    SettingsAction.BaseCurrency -> navigation.slideFromRight(BaseCurrencySettingsPage())
                    SettingsAction.SecurityCenter -> navigation.slideFromRight(settingsPages.security())
                    SettingsAction.Language -> navigation.slideFromRight(settingsPages.language())
                    SettingsAction.AboutPremium -> navigation.slideFromBottom(AboutPremiumPage(null))
                    SettingsAction.Appearance -> navigation.slideFromRight(AppearancePage())
                    SettingsAction.BlockchainSettings -> navigation.slideFromRight(BlockchainSettingsPage())
                    SettingsAction.Contacts -> navigation.slideFromRight(ContactsPage(ContactsPage.Input(Mode.Full)))
                    SettingsAction.AddressChecker -> navigation.slideFromRight(AddressCheckerPage())
                    SettingsAction.PremiumSettings -> navigation.slideFromRight(PremiumSettingsPage())
                    else -> Unit
                }
            },
            alertPainter = settingsAlertPainter(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberSettingsUiState(): MainSettingUiState {
    val currencyManager = koinInject<CurrencyManager>()
    val languageManager = koinInject<ILanguageManager>()
    val currentLanguage = remember(languageManager.currentLocaleTag) { currentLanguageName(languageManager) }
    val baseCurrencyCode by remember(currencyManager) {
        currencyManager.baseCurrencyUpdatedFlow.map { currencyManager.baseCurrency.code }
    }.collectAsStateWithLifecycle(currencyManager.baseCurrency.code)
    val backupManager = koinInject<IBackupManager>()
    val accountManager = koinInject<IAccountManager>()
    // As Android: both are re-read whenever the accounts change.
    val (allBackedUp, hasNonStandardAccount) = remember(backupManager, accountManager) {
        backupManager.allBackedUpFlow.map { backupManager.allBackedUp to accountManager.hasNonStandardAccount }
    }.collectAsStateWithLifecycle(backupManager.allBackedUp to accountManager.hasNonStandardAccount).value
    return MainSettingUiState(
        isUpdateAvailable = false,
        currentLanguage = currentLanguage,
        baseCurrencyCode = baseCurrencyCode,
        appWebPageLink = "",
        hasNonStandardAccount = hasNonStandardAccount,
        allBackedUp = allBackedUp,
        pendingRequestCount = 0,
        walletConnectSessionCount = 0,
        securityCenterShowAlert = false,
        securityCenterShowNewBadge = false,
        aboutAppShowAlert = false,
        wcCounterType = null,
        premiumSettingsShowAlert = false,
    )
}
