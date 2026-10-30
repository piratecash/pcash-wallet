package cash.p.terminal.modules.multiswap.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_changelly
import cash.p.terminal.resources.uniswap
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(showBackground = true)
@Composable
private fun SwapProviderFieldPreview() {
    ComposeAppTheme {
        SwapProviderField(
            title = "Uniswap",
            iconId = Res.drawable.uniswap
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SwapProviderFieldRemoteIconPreview() {
    ComposeAppTheme {
        SwapProviderField(
            title = "Changelly",
            iconId = Res.drawable.ic_changelly,
            iconUrl = "https://example.com/icon.png",
        )
    }
}
