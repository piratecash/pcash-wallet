package cash.p.terminal.modules.multiswap.providersettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.uniswap
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun SwapProvidersSettingsScreenPreview() {
    ComposeAppTheme {
        SwapProvidersSettingsScreen(
            uiState = SwapProvidersSettingsUiState(
                items = listOf(
                    SwapProviderItem("uniswap", "Uniswap", Res.drawable.uniswap, enabled = true, mandatory = false),
                    SwapProviderItem("pancake", "PancakeSwap", Res.drawable.uniswap, enabled = true, mandatory = true),
                    SwapProviderItem("oneinch", "1inch", Res.drawable.uniswap, enabled = false, mandatory = false),
                )
            ),
            onToggle = { _, _ -> },
            onClose = {},
        )
    }
}
