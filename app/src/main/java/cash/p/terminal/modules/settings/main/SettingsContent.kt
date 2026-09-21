package cash.p.terminal.modules.settings.main

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.SectionPremiumUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun SettingsContent(
    uiState: MainSettingUiState,
    appVersion: String,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(12.dp))
        DonateAndMiniAppSections(onAction)
        WalletConnectionsSection(uiState, onAction)
        GeneralSettingsSection(uiState, onAction)
        ToolsSettingsSections(onAction)
        PremiumSettingsSection(uiState, onAction)
        ApplicationSettingsSection(uiState, onAction)
        SettingsFooter(appVersion, onAction)
    }
}

@Composable
private fun DonateAndMiniAppSections(onAction: (SettingsAction) -> Unit) {
    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                title = R.string.Settings_Donate,
                icon = R.drawable.ic_heart_filled_24,
                iconTint = ComposeAppTheme.colors.jacob,
                onClick = onAction.callbackFor(SettingsAction.Donate),
            )
        }
    )
    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                R.string.settings_mini_app,
                R.drawable.ic_uwt2_24,
                onClick = onAction.callbackFor(SettingsAction.MiniApp),
            )
        }
    )
    VSpacer(32.dp)
}

@Composable
private fun WalletConnectionsSection(
    uiState: MainSettingUiState,
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    R.string.SettingsSecurity_ManageKeys,
                    R.drawable.ic_wallet_20,
                    showAlert = uiState.manageWalletShowAlert,
                    onClick = onAction.callbackFor(SettingsAction.ManageWallets),
                )
            },
            {
                HsSettingCell(
                    R.string.BlockchainSettings_Title,
                    R.drawable.ic_blocks_20,
                    onClick = onAction.callbackFor(SettingsAction.BlockchainSettings),
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_WalletConnect,
                    R.drawable.ic_wallet_connect_20,
                    value = (uiState.wcCounterType as? MainSettingsModule.CounterType.SessionCounter)
                        ?.number?.toString(),
                    counterBadge = (uiState.wcCounterType as?
                            MainSettingsModule.CounterType.PendingRequestCounter)
                        ?.number?.toString(),
                    onClick = onAction.callbackFor(SettingsAction.WalletConnect),
                )
            },
            {
                HsSettingCell(
                    title = R.string.Settings_TonConnect,
                    icon = R.drawable.ic_ton_connect_24,
                    value = null,
                    counterBadge = null,
                    onClick = onAction.callbackFor(SettingsAction.TonConnect),
                )
            },
            {
                HsSettingCell(
                    R.string.BackupManager_Title,
                    R.drawable.ic_file_24,
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
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    R.string.Settings_SecurityCenter,
                    R.drawable.ic_security,
                    newBadgeText = if (uiState.securityCenterShowNewBadge) {
                        stringResource(R.string.badge_new)
                    } else {
                        null
                    },
                    showAlert = uiState.securityCenterShowAlert,
                    onClick = onAction.callbackFor(SettingsAction.SecurityCenter),
                )
            },
            {
                HsSettingCell(
                    R.string.Contacts,
                    R.drawable.ic_user_20,
                    onClick = onAction.callbackFor(SettingsAction.Contacts),
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_Appearance,
                    R.drawable.ic_brush_20,
                    onClick = onAction.callbackFor(SettingsAction.Appearance),
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_BaseCurrency,
                    R.drawable.ic_currency,
                    value = uiState.baseCurrencyCode,
                    onClick = onAction.callbackFor(SettingsAction.BaseCurrency),
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_Language,
                    R.drawable.ic_language,
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
                HsSettingCell(
                    title = R.string.address_checker_title,
                    icon = R.drawable.ic_radar_24,
                    onClick = onAction.callbackFor(SettingsAction.AddressChecker),
                )
            },
            {
                HsSettingCell(
                    title = R.string.swap_providers_title,
                    icon = R.drawable.ic_swap_24,
                    onClick = onAction.callbackFor(SettingsAction.SwapProviders),
                )
            },
        )
    )
    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                title = R.string.offline_broadcast_title,
                icon = R.drawable.ic_send_24,
                onClick = onAction.callbackFor(SettingsAction.OfflineBroadcast),
            )
        }
    )
    VSpacer(24.dp)
}

@Composable
private fun PremiumSettingsSection(
    uiState: MainSettingUiState,
    onAction: (SettingsAction) -> Unit,
) {
    PremiumHeader()
    SectionPremiumUniversalLawrence {
        HsSettingCell(
            title = R.string.about_premium,
            icon = R.drawable.ic_info_20,
            iconTint = ComposeAppTheme.colors.jacob,
            onClick = onAction.callbackFor(SettingsAction.AboutPremium),
        )
        HsSettingCell(
            title = R.string.premium_settings,
            icon = R.drawable.ic_settings,
            iconTint = ComposeAppTheme.colors.jacob,
            showAlert = uiState.premiumSettingsShowAlert,
            onClick = onAction.callbackFor(SettingsAction.PremiumSettings),
        )
        HsSettingCell(
            title = R.string.advanced_security,
            icon = R.drawable.ic_shield_24,
            iconTint = ComposeAppTheme.colors.jacob,
            onClick = onAction.callbackFor(SettingsAction.AdvancedSecurity),
        )
    }
    VSpacer(32.dp)
}

@Composable
private fun ApplicationSettingsSection(
    uiState: MainSettingUiState,
    onAction: (SettingsAction) -> Unit,
) {
    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    R.string.software_update_title,
                    R.drawable.ic_refresh,
                    showAlert = uiState.isUpdateAvailable,
                    onClick = onAction.callbackFor(SettingsAction.SoftwareUpdate),
                )
            },
            {
                HsSettingCell(
                    R.string.SettingsAboutApp_Title,
                    R.drawable.ic_about_app_20,
                    showAlert = uiState.aboutAppShowAlert,
                    onClick = onAction.callbackFor(SettingsAction.AboutApp),
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_RateUs,
                    R.drawable.ic_star_20,
                    onClick = onAction.callbackFor(SettingsAction.RateApp),
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_ShareThisWallet,
                    R.drawable.ic_share_20,
                    onClick = onAction.callbackFor(SettingsAction.ShareApp),
                )
            },
            {
                HsSettingCell(
                    R.string.SettingsContact_Title,
                    R.drawable.ic_mail_24,
                    onClick = onAction.callbackFor(SettingsAction.Contact(uiState.isPayCoreEnabled)),
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
            text = stringResource(R.string.Settings_InfoTitleWithVersion, appVersion).uppercase()
        )
        HsDivider(
            modifier = Modifier
                .width(100.dp)
                .padding(top = 8.dp, bottom = 4.5.dp),
            color = ComposeAppTheme.colors.steel20,
        )
        Text(
            text = stringResource(R.string.Settings_InfoSubtitle),
            style = ComposeAppTheme.typography.micro,
            color = ComposeAppTheme.colors.grey,
        )
        Image(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(32.dp)
                .clickable(onClick = onAction.callbackFor(SettingsAction.CompanyWebsite)),
            painter = painterResource(id = R.drawable.ic_company_logo),
            contentDescription = null,
        )
        caption_grey(
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
            text = stringResource(R.string.Settings_CompanyName),
        )
    }
}

private fun ((SettingsAction) -> Unit).callbackFor(action: SettingsAction): () -> Unit = {
    invoke(action)
}
