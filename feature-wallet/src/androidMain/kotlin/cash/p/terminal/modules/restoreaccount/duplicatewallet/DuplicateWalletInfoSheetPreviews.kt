package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(showBackground = true)
@Composable
private fun DuplicateWalletInfoScreenPreview() {
    ComposeAppTheme {
        DuplicateWalletInfoScreen(navigation = HSNavigation(NavBackStack()))
    }
}
