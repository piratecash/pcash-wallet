package cash.p.terminal.modules.settings.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.managers.RateAppManager
import cash.p.terminal.feature.miniapp.ui.miniapp.MiniAppPage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsPage
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsPage
import cash.p.terminal.modules.contacts.ContactsPage
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredSheet
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.send.offline.OfflineBroadcastPage
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.modules.settings.about.ContactOptionsSheet
import cash.p.terminal.modules.settings.about.ContactUsPage
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerPage
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityPage
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.modules.settings.donate.DonateTokenSelectPage
import cash.p.terminal.modules.settings.language.LanguageSettingsPage
import cash.p.terminal.modules.settings.security.SecuritySettingsPage
import cash.p.terminal.modules.softwareupdate.SoftwareUpdatePage
import cash.p.terminal.modules.tonconnect.TonConnectMainPage
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedSheet
import cash.p.terminal.modules.walletconnect.WCErrorNoAccountSheet
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.list.WCListPage
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.shared.settings.SettingsAction
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.HsSettingCell as SharedHsSettingCell
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.launchExternalActivity
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val slideFromRightDestinations: Map<SettingsAction, () -> HSPage> = mapOf(
    SettingsAction.Donate to ::DonateTokenSelectPage,
    SettingsAction.MiniApp to ::MiniAppPage,
    SettingsAction.BlockchainSettings to ::BlockchainSettingsPage,
    SettingsAction.BackupManager to ::BackupManagerPage,
    SettingsAction.SecurityCenter to ::SecuritySettingsPage,
    SettingsAction.Appearance to ::AppearancePage,
    SettingsAction.BaseCurrency to ::BaseCurrencySettingsPage,
    SettingsAction.Language to ::LanguageSettingsPage,
    SettingsAction.AddressChecker to ::AddressCheckerPage,
    SettingsAction.SwapProviders to ::SwapProvidersSettingsPage,
    SettingsAction.PremiumSettings to ::PremiumSettingsPage,
    SettingsAction.AdvancedSecurity to ::AdvancedSecurityPage,
    SettingsAction.SoftwareUpdate to ::SoftwareUpdatePage,
    SettingsAction.AboutApp to ::AboutPage,
)

@Composable
fun SettingsScreen(
    navigation: HSNavigation,
    paddingValues: PaddingValues,
    viewModel: MainSettingsViewModel = koinViewModel(),
) {
    PageResumeEffect(onResume = viewModel::refresh, onPause = {})
    val context = LocalContext.current
    val appPages: AppPages = koinInject()
    val view = LocalView.current
    val pinComponent: IPinComponent = koinInject()
    val rawTxScanTitle = stringResource(R.string.offline_broadcast_title)
    val walletConnectTitle = stringResource(R.string.WalletConnect_Title)
    val tonConnectTitle = stringResource(R.string.TonConnect_Title)
    val transactionFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            navigation.slideFromRight(
                OfflineBroadcastPage(OfflineBroadcastPage.Input(fileUri = it.toString()))
            )
        }
    }
    val importTransactionFile = {
        try {
            pinComponent.launchExternalActivity {
                transactionFileLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
            }
        } catch (_: ActivityNotFoundException) {
            HudHelper.showErrorMessage(view, R.string.offline_broadcast_file_read_failed)
        }
    }

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(stringResource(R.string.Settings_Title))
            SettingsContent(
                uiState = viewModel.uiState,
                appVersion = viewModel.appVersion,
                onAction = { action ->
                    handleSettingsAction(
                        action = action,
                        navigation = navigation,
                        appPages = appPages,
                        viewModel = viewModel,
                        context = context,
                        rawTxScanTitle = rawTxScanTitle,
                        walletConnectTitle = walletConnectTitle,
                        tonConnectTitle = tonConnectTitle,
                        importTransactionFile = importTransactionFile,
                    )
                },
                alertPainter = painterResource(R.drawable.ic_attention_red_20),
                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
            )
        }
    }
}

internal fun handleSettingsAction(
    action: SettingsAction,
    navigation: HSNavigation,
    appPages: AppPages,
    viewModel: MainSettingsViewModel,
    context: Context,
    rawTxScanTitle: String,
    walletConnectTitle: String,
    tonConnectTitle: String,
    importTransactionFile: () -> Unit,
) {
    slideFromRightDestinations[action]?.let { destination ->
        navigation.slideFromRight(destination())
        return
    }

    when (action) {
        SettingsAction.ManageWallets -> navigation.slideFromRight(
            ManageAccountsPage(ManageAccountsModule.Mode.Manage)
        )
        SettingsAction.WalletConnect -> openWalletConnect(
            navigation,
            viewModel.walletConnectSupportState,
            walletConnectTitle,
        )
        SettingsAction.TonConnect -> openTonConnect(
            navigation,
            viewModel.currentAccountSupportsTonConnect,
            tonConnectTitle,
        )
        SettingsAction.Contacts -> navigation.slideFromRight(ContactsPage(ContactsPage.Input(Mode.Full)))
        SettingsAction.OfflineBroadcast -> openOfflineBroadcastScanner(navigation, appPages, rawTxScanTitle)
        SettingsAction.ImportTransactionFile -> importTransactionFile()
        SettingsAction.AboutPremium -> navigation.slideFromBottom(AboutPremiumPage(null))
        SettingsAction.RateApp -> RateAppManager.openPlayMarket(context)
        SettingsAction.ShareApp -> shareAppLink(viewModel.uiState.appWebPageLink, context)
        is SettingsAction.Contact -> navigation.slideFromContact(action.isPayCoreEnabled)
        SettingsAction.CompanyWebsite -> LinkHelper.openLinkInAppBrowser(context, viewModel.companyWebPage)
        else -> Unit
    }
}

private fun openWalletConnect(
    navigation: HSNavigation,
    supportState: WCManager.SupportState,
    walletConnectTitle: String,
) {
    when (supportState) {
        WCManager.SupportState.Supported -> navigation.slideFromRight(WCListPage(null))
        WCManager.SupportState.NotSupportedDueToNoActiveAccount -> {
            navigation.slideFromBottom(WCErrorNoAccountSheet())
        }
        is WCManager.SupportState.NotSupportedDueToNonBackedUpAccount -> {
            val text = Translator.getString(R.string.WalletConnect_Error_NeedBackup)
            navigation.slideFromBottom(
                BackupRequiredSheet(BackupRequiredSheet.Input(supportState.account, text))
            )
        }
        is WCManager.SupportState.NotSupported -> navigation.slideFromBottom(
            AccountTypeNotSupportedSheet(
                AccountTypeNotSupportedSheet.Input(
                    iconResId = R.drawable.ic_wallet_connect_24,
                    titleResId = R.string.WalletConnect_Title,
                    connectionLabel = walletConnectTitle,
                )
            )
        )
    }
}

private fun openTonConnect(
    navigation: HSNavigation,
    supported: Boolean,
    tonConnectTitle: String,
) {
    if (supported) {
        navigation.slideFromRight(TonConnectMainPage(null))
    } else {
        navigation.slideFromBottom(
            AccountTypeNotSupportedSheet(
                AccountTypeNotSupportedSheet.Input(
                    iconResId = R.drawable.ic_ton_connect_24,
                    titleResId = R.string.TonConnect_Title,
                    connectionLabel = tonConnectTitle,
                )
            )
        )
    }
}

private fun openOfflineBroadcastScanner(
    navigation: HSNavigation,
    appPages: AppPages,
    rawTxScanTitle: String,
) {
    navigation.openQrScanner(
        appPages = appPages,
        title = rawTxScanTitle,
        showPasteButton = true,
    ) { scannedText ->
        navigation.slideFromRight(
            OfflineBroadcastPage(OfflineBroadcastPage.Input(initialInput = scannedText))
        )
    }
}

private fun HSNavigation.slideFromContact(isPayCoreEnabled: Boolean) {
    if (isPayCoreEnabled) {
        slideFromRight(ContactUsPage())
    } else {
        slideFromBottom(ContactOptionsSheet(null))
    }
}

@Composable
fun HsSettingCell(
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    value: String? = null,
    counterBadge: String? = null,
    newBadgeText: String? = null,
    showAlert: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    SharedHsSettingCell(
        title = stringResource(title),
        arrowPainter = if (onClick == null) null else painterResource(R.drawable.ic_arrow_right),
        leadingPainter = icon?.let { painterResource(it) },
        iconTint = iconTint,
        value = value,
        counterBadge = counterBadge,
        newBadgeText = newBadgeText,
        alertPainter = if (showAlert) painterResource(R.drawable.ic_attention_red_20) else null,
        onClick = onClick,
    )
}

private fun shareAppLink(appLink: String, context: Context) {
    val shareMessage = Translator.getString(R.string.SettingsShare_Text) + "\n" + appLink + "\n"
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            Translator.getString(R.string.SettingsShare_Title)
        )
    )
}

@Preview
@Composable
private fun previewSettingsScreen() {
    cash.p.terminal.ui_compose.theme.ComposeAppTheme {
        Column {
            CellSingleLineLawrenceSection(
                listOf({
                    HsSettingCell(
                        R.string.Settings_Faq,
                        R.drawable.ic_faq_20,
                        showAlert = true,
                        onClick = { }
                    )
                }, {
                    HsSettingCell(
                        R.string.Guides_Title,
                        R.drawable.ic_academy_20,
                        onClick = { }
                    )
                })
            )

            Spacer(Modifier.height(32.dp))

            CellSingleLineLawrenceSection(
                listOf {
                    HsSettingCell(
                        R.string.Settings_WalletConnect,
                        R.drawable.ic_wallet_connect_20,
                        counterBadge = "13",
                        onClick = { }
                    )
                }
            )
        }
    }
}
