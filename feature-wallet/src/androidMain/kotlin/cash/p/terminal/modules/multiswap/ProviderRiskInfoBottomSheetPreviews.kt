package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun ProviderRiskInfoContentPreview() {
    ComposeAppTheme {
        ProviderRiskInfoContent(onClose = {})
    }
}
