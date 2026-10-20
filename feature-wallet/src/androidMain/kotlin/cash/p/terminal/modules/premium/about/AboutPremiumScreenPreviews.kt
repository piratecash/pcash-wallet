package cash.p.terminal.modules.premium.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.flow.emptyFlow

@Preview
@Composable
private fun SelectSubscriptionScreenPreview() {
    ComposeAppTheme {
        AboutPremiumScreen(
            uiState = AboutPremiumUiState(),
            uiEvents = emptyFlow(),
            onCloseClick = {},
            onRetryClick = {},
            onUrlClick = {},
            onTryForFreeClick = {}
        )
    }
}
