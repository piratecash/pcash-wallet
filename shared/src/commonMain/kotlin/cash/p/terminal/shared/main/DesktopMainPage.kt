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
import androidx.compose.runtime.LaunchedEffect
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
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.managers.AppVersionManager
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.core.providers.SharedAppConfig
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
import cash.p.terminal.modules.releasenotes.ReleaseNotesPage
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerPage
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.modules.transactionInfo.TransactionInfoPage
import cash.p.terminal.modules.transactions.TransactionsScreen
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsShare_Text
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.shared.settings.SettingsAction
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.modules.settings.about.ContactOptionsSheet
import cash.p.terminal.modules.settings.main.settingsAlertPainter
import cash.p.terminal.shared.settings.appShareText
import cash.p.terminal.shared.settings.SettingsPages
import cash.p.terminal.shared.settings.currentLanguageName
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.components.showNotSupportedOnPlatform
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Screens that exist only on Android; the rows would be dead taps here. */
private val DESKTOP_HIDDEN_SETTINGS = setOf(
    SettingsAction.MiniApp,
    SettingsAction.WalletConnect,
    SettingsAction.TonConnect,
    SettingsAction.SoftwareUpdate,
    SettingsAction.AdvancedSecurity,
    SettingsAction.RateApp,
)

/** Root of the desktop back stack: the main tabs; pages opened from them stack above it. */
class DesktopMainPage : HSPage() {

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
        ShowReleaseNotesAfterUpdate(navigation)
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
                MainDestination.Settings -> SettingsPane(navigation)
            }
        }
    }
}

/** Composed only while unlocked; the back stack outlives a lock, so an already open page is not pushed again. */
@Composable
private fun ShowReleaseNotesAfterUpdate(navigation: HSNavigation) {
    val releaseNotesManager = koinInject<ReleaseNotesManager>()
    val appVersionManager = koinInject<AppVersionManager>()
    LaunchedEffect(Unit) {
        appVersionManager.storeAppVersion()
        if (navigation.lastOrNull() !is ReleaseNotesPage && releaseNotesManager.shouldShowChangeLog()) {
            navigation.slideFromBottom(ReleaseNotesPage(ReleaseNotesPage.Input(true)))
        }
    }
}

@Composable
private fun BalancePane(navigation: HSNavigation) {
    val transactionsViewModel = rememberMainPageTransactionsViewModel(navigation)
    BalanceScreen(
        navigation = navigation,
        paddingValues = PaddingValues(),
        onOpenTransactionInfo = { item ->
            transactionsViewModel.tmpItemToShow = item
            navigation.slideFromBottom(TransactionInfoPage())
        },
    )
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
private fun SettingsPane(navigation: HSNavigation) {
    val uiState = rememberSettingsUiState()
    val settingsPages = koinInject<SettingsPages>()
    val appVersion = koinInject<ISystemInfoManager>().appVersionDisplay
    val urlOpener = LocalUrlOpener.current
    val shareApp = rememberShareAppToClipboard(uiState.appWebPageLink)
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
                    else -> openAboutSetting(action, navigation, urlOpener, shareApp)
                }
            },
            alertPainter = settingsAlertPainter(),
            hiddenActions = DESKTOP_HIDDEN_SETTINGS,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberShareAppToClipboard(appLink: String): () -> Unit {
    val clipboard = koinInject<IClipboardManager>()
    val hud = LocalHudMessenger.current
    val shareIntro = stringResource(Res.string.SettingsShare_Text)
    val copiedText = stringResource(Res.string.Hud_Text_Copied)
    return {
        clipboard.copyText(appShareText(shareIntro, appLink))
        hud.showSuccess(copiedText)
    }
}

private fun openAboutSetting(
    action: SettingsAction,
    navigation: HSNavigation,
    urlOpener: UrlOpener,
    shareApp: () -> Unit,
) {
    when (action) {
        SettingsAction.AboutApp -> navigation.slideFromRight(AboutPage())
        is SettingsAction.Contact -> navigation.slideFromBottom(ContactOptionsSheet(null))
        SettingsAction.ShareApp -> shareApp()
        SettingsAction.CompanyWebsite -> urlOpener.open(SharedAppConfig.companyWebPageLink)
        else -> Unit
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
    val termsManager = koinInject<ITermsManager>()
    val allTermsAccepted by remember(termsManager) {
        termsManager.termsAcceptedSignalFlow.map { termsManager.allTermsAccepted }
    }.collectAsStateWithLifecycle(termsManager.allTermsAccepted)
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
        appWebPageLink = SharedAppConfig.appWebPageLink,
        hasNonStandardAccount = hasNonStandardAccount,
        allBackedUp = allBackedUp,
        pendingRequestCount = 0,
        walletConnectSessionCount = 0,
        securityCenterShowAlert = false,
        securityCenterShowNewBadge = false,
        aboutAppShowAlert = !allTermsAccepted,
        wcCounterType = null,
        premiumSettingsShowAlert = false,
        isPayCoreEnabled = false,
    )
}
