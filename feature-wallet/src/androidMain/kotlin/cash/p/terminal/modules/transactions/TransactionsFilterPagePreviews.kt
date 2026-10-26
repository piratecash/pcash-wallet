package cash.p.terminal.modules.transactions

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun FilterScreenPreview() {
    ComposeAppTheme {
        FilterScreen(
            uiState = FilterScreenUiState(
                resetEnabled = true,
                selectedCoinTitle = "BTC",
                selectedBlockchainName = "Ethereum",
                selectedContactName = null,
                hideSuspiciousTx = true,
                amlCheckEnabled = false
            ),
            onReset = {},
            onBlockchainClick = {},
            onCoinClick = {},
            onContactClick = {},
            onHideSuspiciousTxChange = {},
            onAmlCheckChange = {},
            onAmlInfoClick = {},
            onApply = {},
            onBack = {}
        )
    }
}
