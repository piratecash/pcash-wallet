package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_wallet_48
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun InfoErrorMessageDefaultPreview() {
    ComposeAppTheme(darkTheme = true) {
        InfoErrorMessageDefault(
            painter = painterResource(Res.drawable.ic_wallet_48),
            text = "You don't have any wallets"
        )
    }
}
