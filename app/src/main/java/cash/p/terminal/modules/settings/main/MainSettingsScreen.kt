package cash.p.terminal.modules.settings.main

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.managers.RateAppManager
import cash.p.terminal.modules.contacts.ContactsFragment
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.send.offline.OfflineBroadcastFragment
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedDialog
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui.compose.components.BadgeText
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.viewmodel.koinViewModel

private val slideFromRightDestinations = mapOf(
    SettingsAction.Donate to R.id.donateTokenSelectFragment,
    SettingsAction.MiniApp to R.id.miniAppFragment,
    SettingsAction.BlockchainSettings to R.id.blockchainSettingsFragment,
    SettingsAction.BackupManager to R.id.backupManagerFragment,
    SettingsAction.SecurityCenter to R.id.securitySettingsFragment,
    SettingsAction.Appearance to R.id.appearanceFragment,
    SettingsAction.BaseCurrency to R.id.baseCurrencySettingsFragment,
    SettingsAction.Language to R.id.languageSettingsFragment,
    SettingsAction.AddressChecker to R.id.addressCheckerFragment,
    SettingsAction.SwapProviders to R.id.swapProvidersSettingsFragment,
    SettingsAction.PremiumSettings to R.id.premiumSettingsFragment,
    SettingsAction.AdvancedSecurity to R.id.advancedSecurityFragment,
    SettingsAction.SoftwareUpdate to R.id.softwareUpdateFragment,
    SettingsAction.AboutApp to R.id.aboutAppFragment,
)

@Composable
fun SettingsScreen(
    fragmentNavController: NavController,
    paddingValues: PaddingValues,
    viewModel: MainSettingsViewModel = koinViewModel(),
) {
    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    val context = LocalContext.current
    val rawTxScanTitle = stringResource(R.string.offline_broadcast_title)
    val walletConnectTitle = stringResource(R.string.WalletConnect_Title)
    val tonConnectTitle = stringResource(R.string.TonConnect_Title)

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(stringResource(R.string.Settings_Title))
            SettingsContent(
                uiState = viewModel.uiState,
                appVersion = viewModel.appVersion,
                onAction = { action ->
                    handleSettingsAction(
                        action = action,
                        navController = fragmentNavController,
                        viewModel = viewModel,
                        context = context,
                        rawTxScanTitle = rawTxScanTitle,
                        walletConnectTitle = walletConnectTitle,
                        tonConnectTitle = tonConnectTitle,
                    )
                },
                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
            )
        }
    }
}

internal fun handleSettingsAction(
    action: SettingsAction,
    navController: NavController,
    viewModel: MainSettingsViewModel,
    context: Context,
    rawTxScanTitle: String,
    walletConnectTitle: String,
    tonConnectTitle: String,
) {
    slideFromRightDestinations[action]?.let { destination ->
        navController.slideFromRight(destination)
        return
    }

    when (action) {
        SettingsAction.ManageWallets -> navController.slideFromRight(
            R.id.manageAccountsFragment,
            ManageAccountsModule.Mode.Manage,
        )
        SettingsAction.WalletConnect -> openWalletConnect(
            navController,
            viewModel.walletConnectSupportState,
            walletConnectTitle,
        )
        SettingsAction.TonConnect -> openTonConnect(
            navController,
            viewModel.currentAccountSupportsTonConnect,
            tonConnectTitle,
        )
        SettingsAction.Contacts -> navController.slideFromRight(
            R.id.contactsFragment,
            ContactsFragment.Input(Mode.Full),
        )
        SettingsAction.OfflineBroadcast -> openOfflineBroadcastScanner(navController, rawTxScanTitle)
        SettingsAction.AboutPremium -> navController.slideFromBottom(R.id.aboutPremiumFragment)
        SettingsAction.RateApp -> RateAppManager.openPlayMarket(context)
        SettingsAction.ShareApp -> shareAppLink(viewModel.uiState.appWebPageLink, context)
        is SettingsAction.Contact -> navController.slideFromContact(action.isPayCoreEnabled)
        SettingsAction.CompanyWebsite -> LinkHelper.openLinkInAppBrowser(context, viewModel.companyWebPage)
        else -> Unit
    }
}

private fun openWalletConnect(
    navController: NavController,
    supportState: WCManager.SupportState,
    walletConnectTitle: String,
) {
    when (supportState) {
        WCManager.SupportState.Supported -> navController.slideFromRight(R.id.wcListFragment)
        WCManager.SupportState.NotSupportedDueToNoActiveAccount -> {
            navController.slideFromBottom(R.id.wcErrorNoAccountFragment)
        }
        is WCManager.SupportState.NotSupportedDueToNonBackedUpAccount -> {
            val text = Translator.getString(R.string.WalletConnect_Error_NeedBackup)
            navController.slideFromBottom(
                R.id.backupRequiredDialog,
                BackupRequiredDialog.Input(supportState.account, text),
            )
        }
        is WCManager.SupportState.NotSupported -> navController.slideFromBottom(
            MainGraphDirections.actionGlobalToAccountTypeNotSupportedDialog(
                AccountTypeNotSupportedDialog.Input(
                    iconResId = R.drawable.ic_wallet_connect_24,
                    titleResId = R.string.WalletConnect_Title,
                    connectionLabel = walletConnectTitle,
                )
            )
        )
    }
}

private fun openTonConnect(
    navController: NavController,
    supported: Boolean,
    tonConnectTitle: String,
) {
    if (supported) {
        navController.slideFromRight(R.id.tcListFragment)
    } else {
        navController.slideFromBottom(
            MainGraphDirections.actionGlobalToAccountTypeNotSupportedDialog(
                AccountTypeNotSupportedDialog.Input(
                    iconResId = R.drawable.ic_ton_connect_24,
                    titleResId = R.string.TonConnect_Title,
                    connectionLabel = tonConnectTitle,
                )
            )
        )
    }
}

private fun openOfflineBroadcastScanner(navController: NavController, rawTxScanTitle: String) {
    navController.openQrScanner(
        title = rawTxScanTitle,
        showPasteButton = true,
    ) { scannedText ->
        navController.slideFromRight(
            MainGraphDirections.actionGlobalToOfflineBroadcastFragment(
                OfflineBroadcastFragment.Input(initialInput = scannedText)
            )
        )
    }
}

private fun NavController.slideFromContact(isPayCoreEnabled: Boolean) {
    if (isPayCoreEnabled) {
        slideFromRight(R.id.contactUsFragment)
    } else {
        slideFromBottom(R.id.contactOptionsDialog)
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
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconTint ?: ComposeAppTheme.colors.grey
            )
        }
        body_leah(
            text = stringResource(title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (icon != null) 16.dp else 0.dp, end = 16.dp)
        )
        Spacer(Modifier.weight(1f))

        if (counterBadge != null) {
            BadgeText(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = counterBadge
            )
        } else if (value != null) {
            subhead1_grey(
                text = value,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal =
                        if (onClick != null) 8.dp else 0.dp
                )
            )
        }

        if (newBadgeText != null) {
            BadgeText(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = newBadgeText,
                background = ComposeAppTheme.colors.issykBlue,
            )
        }

        if (showAlert) {
            Image(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_attention_red_20),
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
        }
        if (onClick != null) {
            Image(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = null,
            )
        }
    }
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
