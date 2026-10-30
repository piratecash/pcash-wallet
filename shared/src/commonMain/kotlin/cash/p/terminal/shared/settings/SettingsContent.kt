package cash.p.terminal.shared.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.BackupManager_Title
import cash.p.terminal.resources.BlockchainSettings_Title
import cash.p.terminal.resources.Contacts
import cash.p.terminal.modules.settings.main.SettingsCell
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsAboutApp_Title
import cash.p.terminal.resources.SettingsContact_Title
import cash.p.terminal.resources.SettingsSecurity_ManageKeys
import cash.p.terminal.resources.Settings_Appearance
import cash.p.terminal.resources.Settings_BaseCurrency
import cash.p.terminal.resources.Settings_CompanyName
import cash.p.terminal.resources.Settings_Donate
import cash.p.terminal.resources.Settings_InfoSubtitle
import cash.p.terminal.resources.Settings_InfoTitleWithVersion
import cash.p.terminal.resources.Settings_Language
import cash.p.terminal.resources.Settings_RateUs
import cash.p.terminal.resources.Settings_SecurityCenter
import cash.p.terminal.resources.Settings_ShareThisWallet
import cash.p.terminal.resources.Settings_TonConnect
import cash.p.terminal.resources.Settings_WalletConnect
import cash.p.terminal.resources.about_premium
import cash.p.terminal.resources.address_checker_title
import cash.p.terminal.resources.advanced_security
import cash.p.terminal.resources.badge_new
import cash.p.terminal.resources.ic_about_app_20
import cash.p.terminal.resources.ic_blocks_20
import cash.p.terminal.resources.ic_brush_20
import cash.p.terminal.resources.ic_company_logo
import cash.p.terminal.resources.ic_currency
import cash.p.terminal.resources.ic_file_24
import cash.p.terminal.resources.ic_heart_filled_24
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_language
import cash.p.terminal.resources.ic_mail_24
import cash.p.terminal.resources.ic_radar_24
import cash.p.terminal.resources.ic_refresh
import cash.p.terminal.resources.ic_security
import cash.p.terminal.resources.ic_send_24
import cash.p.terminal.resources.ic_settings
import cash.p.terminal.resources.ic_share_20
import cash.p.terminal.resources.ic_shield_24
import cash.p.terminal.resources.ic_star_20
import cash.p.terminal.resources.ic_swap_24
import cash.p.terminal.resources.ic_ton_connect_24
import cash.p.terminal.resources.ic_user_20
import cash.p.terminal.resources.ic_uwt2_24
import cash.p.terminal.resources.ic_wallet_20
import cash.p.terminal.resources.ic_wallet_connect_20
import cash.p.terminal.resources.offline_broadcast_import_file
import cash.p.terminal.resources.offline_broadcast_title
import cash.p.terminal.resources.premium_settings
import cash.p.terminal.resources.settings_mini_app
import cash.p.terminal.resources.software_update_title
import cash.p.terminal.resources.star_filled_yellow_16
import cash.p.terminal.resources.swap_providers_title
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.SectionPremiumUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsContent(
    uiState: MainSettingUiState,
    appVersion: String,
    onAction: (SettingsAction) -> Unit,
    alertPainter: Painter?,
    modifier: Modifier = Modifier,
    hiddenActions: Set<SettingsAction> = emptySet(),
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(12.dp))
        DonateAndMiniAppSections(hiddenActions, onAction)
        WalletConnectionsSection(uiState, alertPainter, hiddenActions, onAction)
        GeneralSettingsSection(uiState, alertPainter, onAction)
        ToolsSettingsSections(onAction)
        PremiumSettingsSection(uiState, alertPainter, hiddenActions, onAction)
        ApplicationSettingsSection(uiState, alertPainter, hiddenActions, onAction)
        SettingsFooter(appVersion, onAction)
    }
}

@Composable
private fun DonateAndMiniAppSections(
    hiddenActions: Set<SettingsAction>,
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection {
        SettingsCell(
            title = Res.string.Settings_Donate,
            icon = Res.drawable.ic_heart_filled_24,
            iconTint = ComposeAppTheme.colors.brandDefault,
            onClick = onAction.callbackFor(SettingsAction.Donate),
        )
    }
    VSpacer(32.dp)
    if (SettingsAction.MiniApp !in hiddenActions) {
        CellUniversalLawrenceSection {
            SettingsCell(
                title = Res.string.settings_mini_app,
                icon = Res.drawable.ic_uwt2_24,
                onClick = onAction.callbackFor(SettingsAction.MiniApp),
            )
        }
        VSpacer(32.dp)
    }
}

@Composable
private fun WalletConnectionsSection(
    uiState: MainSettingUiState,
    alertPainter: Painter?,
    hiddenActions: Set<SettingsAction>,
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection(
        hiddenActions.visibleRows(
            SettingsAction.ManageWallets to {
                SettingsCell(
                    title = Res.string.SettingsSecurity_ManageKeys,
                    icon = Res.drawable.ic_wallet_20,
                    alertPainter = alertPainter.takeIf { uiState.manageWalletShowAlert },
                    onClick = onAction.callbackFor(SettingsAction.ManageWallets),
                )
            },
            SettingsAction.BlockchainSettings to {
                SettingsCell(
                    title = Res.string.BlockchainSettings_Title,
                    icon = Res.drawable.ic_blocks_20,
                    onClick = onAction.callbackFor(SettingsAction.BlockchainSettings),
                )
            },
            SettingsAction.WalletConnect to {
                SettingsCell(
                    title = Res.string.Settings_WalletConnect,
                    icon = Res.drawable.ic_wallet_connect_20,
                    value = (uiState.wcCounterType as? CounterType.SessionCounter)
                        ?.number?.toString(),
                    counterBadge = (uiState.wcCounterType as? CounterType.PendingRequestCounter)
                        ?.number?.toString(),
                    onClick = onAction.callbackFor(SettingsAction.WalletConnect),
                )
            },
            SettingsAction.TonConnect to {
                SettingsCell(
                    title = Res.string.Settings_TonConnect,
                    icon = Res.drawable.ic_ton_connect_24,
                    onClick = onAction.callbackFor(SettingsAction.TonConnect),
                )
            },
            SettingsAction.BackupManager to {
                SettingsCell(
                    title = Res.string.BackupManager_Title,
                    icon = Res.drawable.ic_file_24,
                    onClick = onAction.callbackFor(SettingsAction.BackupManager),
                )
            },
        )
    )
    VSpacer(32.dp)
}

@Composable
private fun GeneralSettingsSection(
    uiState: MainSettingUiState,
    alertPainter: Painter?,
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection(
        listOf(
            {
                SettingsCell(
                    title = Res.string.Settings_SecurityCenter,
                    icon = Res.drawable.ic_security,
                    newBadgeText = if (uiState.securityCenterShowNewBadge) {
                        stringResource(Res.string.badge_new)
                    } else {
                        null
                    },
                    alertPainter = alertPainter.takeIf { uiState.securityCenterShowAlert },
                    onClick = onAction.callbackFor(SettingsAction.SecurityCenter),
                )
            },
            {
                SettingsCell(
                    title = Res.string.Contacts,
                    icon = Res.drawable.ic_user_20,
                    onClick = onAction.callbackFor(SettingsAction.Contacts),
                )
            },
            {
                SettingsCell(
                    title = Res.string.Settings_Appearance,
                    icon = Res.drawable.ic_brush_20,
                    onClick = onAction.callbackFor(SettingsAction.Appearance),
                )
            },
            {
                SettingsCell(
                    title = Res.string.Settings_BaseCurrency,
                    icon = Res.drawable.ic_currency,
                    value = uiState.baseCurrencyCode,
                    onClick = onAction.callbackFor(SettingsAction.BaseCurrency),
                )
            },
            {
                SettingsCell(
                    title = Res.string.Settings_Language,
                    icon = Res.drawable.ic_language,
                    value = uiState.currentLanguage,
                    onClick = onAction.callbackFor(SettingsAction.Language),
                )
            },
        )
    )
    VSpacer(32.dp)
}

@Composable
private fun ToolsSettingsSections(onAction: (SettingsAction) -> Unit) {
    CellUniversalLawrenceSection(
        listOf(
            {
                SettingsCell(
                    title = Res.string.address_checker_title,
                    icon = Res.drawable.ic_radar_24,
                    onClick = onAction.callbackFor(SettingsAction.AddressChecker),
                )
            },
            {
                SettingsCell(
                    title = Res.string.swap_providers_title,
                    icon = Res.drawable.ic_swap_24,
                    onClick = onAction.callbackFor(SettingsAction.SwapProviders),
                )
            },
        )
    )
    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf(
            {
                SettingsCell(
                    title = Res.string.offline_broadcast_title,
                    icon = Res.drawable.ic_send_24,
                    onClick = onAction.callbackFor(SettingsAction.OfflineBroadcast),
                )
            },
            {
                SettingsCell(
                    title = Res.string.offline_broadcast_import_file,
                    icon = Res.drawable.ic_file_24,
                    onClick = onAction.callbackFor(SettingsAction.ImportTransactionFile),
                )
            },
        )
    )
    VSpacer(24.dp)
}

@Composable
private fun PremiumSettingsSection(
    uiState: MainSettingUiState,
    alertPainter: Painter?,
    hiddenActions: Set<SettingsAction>,
    onAction: (SettingsAction) -> Unit,
) {
    PremiumHeader(starPainter = painterResource(Res.drawable.star_filled_yellow_16))
    SectionPremiumUniversalLawrence {
        SettingsCell(
            title = Res.string.about_premium,
            icon = Res.drawable.ic_info_20,
            iconTint = ComposeAppTheme.colors.brandDefault,
            onClick = onAction.callbackFor(SettingsAction.AboutPremium),
        )
        SettingsCell(
            title = Res.string.premium_settings,
            icon = Res.drawable.ic_settings,
            iconTint = ComposeAppTheme.colors.brandDefault,
            alertPainter = alertPainter.takeIf { uiState.premiumSettingsShowAlert },
            onClick = onAction.callbackFor(SettingsAction.PremiumSettings),
        )
        if (SettingsAction.AdvancedSecurity !in hiddenActions) {
            SettingsCell(
                title = Res.string.advanced_security,
                icon = Res.drawable.ic_shield_24,
                iconTint = ComposeAppTheme.colors.brandDefault,
                onClick = onAction.callbackFor(SettingsAction.AdvancedSecurity),
            )
        }
    }
    VSpacer(32.dp)
}

@Composable
private fun ApplicationSettingsSection(
    uiState: MainSettingUiState,
    alertPainter: Painter?,
    hiddenActions: Set<SettingsAction>,
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection(
        hiddenActions.visibleRows(
            SettingsAction.SoftwareUpdate to {
                SettingsCell(
                    title = Res.string.software_update_title,
                    icon = Res.drawable.ic_refresh,
                    alertPainter = alertPainter.takeIf { uiState.isUpdateAvailable },
                    onClick = onAction.callbackFor(SettingsAction.SoftwareUpdate),
                )
            },
            SettingsAction.AboutApp to {
                SettingsCell(
                    title = Res.string.SettingsAboutApp_Title,
                    icon = Res.drawable.ic_about_app_20,
                    alertPainter = alertPainter.takeIf { uiState.aboutAppShowAlert },
                    onClick = onAction.callbackFor(SettingsAction.AboutApp),
                )
            },
            SettingsAction.RateApp to {
                SettingsCell(
                    title = Res.string.Settings_RateUs,
                    icon = Res.drawable.ic_star_20,
                    onClick = onAction.callbackFor(SettingsAction.RateApp),
                )
            },
            SettingsAction.ShareApp to {
                SettingsCell(
                    title = Res.string.Settings_ShareThisWallet,
                    icon = Res.drawable.ic_share_20,
                    onClick = onAction.callbackFor(SettingsAction.ShareApp),
                )
            },
            SettingsAction.Contact to {
                SettingsCell(
                    title = Res.string.SettingsContact_Title,
                    icon = Res.drawable.ic_mail_24,
                    onClick = onAction.callbackFor(SettingsAction.Contact),
                )
            },
        )
    )
    VSpacer(32.dp)
}

@Composable
private fun SettingsFooter(
    appVersion: String,
    onAction: (SettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        caption_grey(
            text = stringResource(
                Res.string.Settings_InfoTitleWithVersion,
                appVersion,
            ).uppercase()
        )
        HsDivider(
            modifier = Modifier
                .width(100.dp)
                .padding(top = 8.dp, bottom = 4.5.dp),
            color = ComposeAppTheme.colors.borderDivider,
        )
        Text(
            text = stringResource(Res.string.Settings_InfoSubtitle),
            style = ComposeAppTheme.typography.micro,
            color = ComposeAppTheme.colors.textSecondary,
        )
        Image(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(32.dp)
                .clickable(onClick = onAction.callbackFor(SettingsAction.CompanyWebsite)),
            painter = painterResource(Res.drawable.ic_company_logo),
            contentDescription = null,
        )
        caption_grey(
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
            text = stringResource(Res.string.Settings_CompanyName),
        )
    }
}

private fun Set<SettingsAction>.visibleRows(
    vararg rows: Pair<SettingsAction, @Composable () -> Unit>,
): List<@Composable () -> Unit> = rows.filter { (action, _) -> action !in this }.map { it.second }

/** The text the app is shared with; the same on every platform. */
fun appShareText(intro: String, appLink: String): String = intro + "\n" + appLink + "\n"

private fun ((SettingsAction) -> Unit).callbackFor(action: SettingsAction): () -> Unit = {
    invoke(action)
}
