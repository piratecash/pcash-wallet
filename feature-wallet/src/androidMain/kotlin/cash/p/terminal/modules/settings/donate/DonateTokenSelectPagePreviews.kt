package cash.p.terminal.modules.settings.donate

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun DonateHeaderPreview() {
    ComposeAppTheme {
        DonateHeader(navigation = HSNavigation(NavBackStack()))
    }
}
