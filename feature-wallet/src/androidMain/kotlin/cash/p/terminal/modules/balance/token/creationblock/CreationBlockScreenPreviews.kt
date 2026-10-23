package cash.p.terminal.modules.balance.token.creationblock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType

@Preview
@Composable
internal fun CreationBlockScreenPreview() {
    ComposeAppTheme {
        CreationBlockScreen(
            uiState = CreationBlockUiState(
                blockchainType = BlockchainType.Monero,
                heightText = "2975499",
                blockDateText = "9 августа 2024 г.",
                changed = true,
            ),
            onHeightChange = {},
            onDatePick = {},
            onRescanClick = {},
            onClose = {},
            onRescanStart = {},
        )
    }
}
