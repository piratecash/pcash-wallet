package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

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
