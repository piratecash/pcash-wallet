package cash.p.terminal.modules.premium.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRightSafely
import cash.p.terminal.core.authorizedLoggingAction
import cash.p.terminal.core.premiumAction
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

class PremiumSettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: PremiumSettingsViewModel = koinViewModel()

        PremiumSettingsScreen(
            uiState = viewModel.uiState,
            onCheckAddressContractClick = {
                navigation.premiumAction {
                    viewModel.setAddressContractChecking(it)
                }
            },
            onAmlCheckReceivedClick = {
                navigation.premiumAction {
                    viewModel.setAmlCheckReceivedEnabled(it)
                }
            },
            onLoginLoggingClick = {
                navigation.authorizedLoggingAction {
                    navigation.slideFromRight(LoginLoggingPage())
                }
            },
            onAuthorizationInfoClick = {
                navigation.authorizedLoggingAction {
                    navigation.slideFromRight(AuthorizationInfoPage())
                }
            },
            onPushNotificationsClick = {
                navigation.premiumAction {
                    navigation.slideFromRightSafely(PushNotificationsPage())
                }
            },
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
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.premium_settings),
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
                    text = stringResource(R.string.settings_smart_contract_check),
                    checked = uiState.checkEnabled,
                    onCheckedChange = onCheckAddressContractClick
                )
            }
            InfoText(
                text = stringResource(R.string.settings_smart_contract_check_description),
            )
            VSpacer(12.dp)
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(R.string.alpha_aml_title),
                    checked = uiState.amlCheckReceivedEnabled,
                    onCheckedChange = onAmlCheckReceivedClick
                )
            }
            VSpacer(12.dp)
            CellUniversalLawrenceSection(
                listOf(
                    {
                        RowWithArrow(
                            text = stringResource(R.string.login_logging_title),
                            showAlert = uiState.showAlertIcon,
                            onClick = onLoginLoggingClick
                        )
                    },
                    {
                        RowWithArrow(
                            text = stringResource(R.string.authorization_information),
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
                            text = stringResource(R.string.push_notification),
                            showAlert = uiState.notificationNotAvailable,
                            onClick = onPushNotificationsClick
                        )
                    }
                )
            )
        }
    }
}

@Preview
@Composable
private fun PremiumSettingsScreenPreview() {
    ComposeAppTheme {
        PremiumSettingsScreen(
            uiState = PremiumSettingsUiState(
                checkEnabled = true,
                amlCheckReceivedEnabled = false,
                notificationNotAvailable = true
            ),
            onCheckAddressContractClick = {},
            onAmlCheckReceivedClick = {},
            onLoginLoggingClick = {},
            onAuthorizationInfoClick = {},
            onPushNotificationsClick = {},
            onClose = {}
        )
    }
}
