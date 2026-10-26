package cash.p.terminal.modules.premium.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRightSafely
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowWithArrow
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.alpha_aml_title
import cash.p.terminal.resources.authorization_information
import cash.p.terminal.resources.login_logging_title
import cash.p.terminal.resources.premium_settings
import cash.p.terminal.resources.push_notification
import cash.p.terminal.resources.settings_smart_contract_check
import cash.p.terminal.resources.settings_smart_contract_check_description

class PremiumSettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: PremiumSettingsViewModel = koinViewModel()
        val platformSettings = viewModel.platformSettings
        val openLoginLogging = platformSettings?.rememberLoginLoggingAction(navigation)
        val openAuthorizationInfo = platformSettings?.rememberAuthorizationInfoAction(navigation)
        val openPushNotifications = platformSettings?.let { settings ->
            navigation.rememberPremiumAction { navigation.slideFromRightSafely(settings.pushNotificationsPage()) }
        }

        PremiumSettingsScreen(
            uiState = viewModel.uiState,
            onCheckAddressContractClick = navigation.rememberPremiumAction(viewModel::setAddressContractChecking),
            onAmlCheckReceivedClick = navigation.rememberPremiumAction(viewModel::setAmlCheckReceivedEnabled),
            onLoginLoggingClick = { openLoginLogging?.invoke() },
            onAuthorizationInfoClick = { openAuthorizationInfo?.invoke() },
            onPushNotificationsClick = { openPushNotifications?.invoke() },
            onClose = { navigation.navigateUpSafely() }
        )
    }
}

@Composable
internal fun PremiumSettingsScreen(
    uiState: PremiumSettingsUiState,
    onCheckAddressContractClick: (Boolean) -> Unit,
    onAmlCheckReceivedClick: (Boolean) -> Unit,
    onLoginLoggingClick: () -> Unit,
    onAuthorizationInfoClick: () -> Unit,
    onPushNotificationsClick: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.premium_settings),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(Res.string.settings_smart_contract_check),
                    checked = uiState.checkEnabled,
                    onCheckedChange = onCheckAddressContractClick
                )
            }
            InfoText(
                text = stringResource(Res.string.settings_smart_contract_check_description),
            )
            VSpacer(12.dp)
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(Res.string.alpha_aml_title),
                    checked = uiState.amlCheckReceivedEnabled,
                    onCheckedChange = onAmlCheckReceivedClick
                )
            }
            if (uiState.platformSettingsAvailable) {
                PlatformSettingsSections(
                    uiState = uiState,
                    onLoginLoggingClick = onLoginLoggingClick,
                    onAuthorizationInfoClick = onAuthorizationInfoClick,
                    onPushNotificationsClick = onPushNotificationsClick,
                )
            }
        }
    }
}

@Composable
private fun PlatformSettingsSections(
    uiState: PremiumSettingsUiState,
    onLoginLoggingClick: () -> Unit,
    onAuthorizationInfoClick: () -> Unit,
    onPushNotificationsClick: () -> Unit,
) {
    VSpacer(12.dp)
    CellUniversalLawrenceSection(
        listOf(
            {
                RowWithArrow(
                    text = stringResource(Res.string.login_logging_title),
                    showAlert = uiState.showAlertIcon,
                    onClick = onLoginLoggingClick
                )
            },
            {
                RowWithArrow(
                    text = stringResource(Res.string.authorization_information),
                    onClick = onAuthorizationInfoClick
                )
            }
        )
    )
    VSpacer(12.dp)
    CellUniversalLawrenceSection(
        listOf(
            {
                RowWithArrow(
                    text = stringResource(Res.string.push_notification),
                    showAlert = uiState.notificationNotAvailable,
                    onClick = onPushNotificationsClick
                )
            }
        )
    )
}

