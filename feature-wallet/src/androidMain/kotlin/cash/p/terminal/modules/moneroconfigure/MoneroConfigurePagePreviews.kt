package cash.p.terminal.modules.moneroconfigure

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.enablecoin.restoresettings.BirthdayHeightConfigUiState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun Preview_MoneroConfigure() {
    ComposeAppTheme(darkTheme = false) {
        MoneroConfigureRoute(
            onCloseClick = {},
            onCloseWithResult = {},
            onModeSelect = {},
            onSetBirthdayHeight = {},
            onDoneClick = {},
            uiState = BirthdayHeightConfigUiState(birthdayHeight = ""),
        )
    }
}
