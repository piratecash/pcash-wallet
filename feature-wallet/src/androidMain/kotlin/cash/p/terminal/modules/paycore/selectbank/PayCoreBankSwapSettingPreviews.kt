package cash.p.terminal.modules.paycore.selectbank

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(showBackground = true)
@Composable
private fun PayCoreBankSwapSettingLoadingPreview() {
    ComposeAppTheme {
        PayCoreBankSwapSettingPreviewContent(
            banks = emptyList(),
            selectedBank = null,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PayCoreBankSwapSettingListPreview() {
    ComposeAppTheme {
        val banks = payCoreBankPreviewItems()
        PayCoreBankSwapSettingPreviewContent(
            banks = banks,
            selectedBank = banks.first(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PayCoreBankSwapSettingEmptyPreview() {
    ComposeAppTheme {
        PayCoreBankSwapSettingPreviewContent(
            banks = emptyList(),
            selectedBank = null,
        )
    }
}
