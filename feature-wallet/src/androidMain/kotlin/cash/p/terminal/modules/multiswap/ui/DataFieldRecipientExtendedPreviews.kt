package cash.p.terminal.modules.multiswap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.entities.Address
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType

@Preview
@Composable
private fun DataFieldRecipientExtendedPreview() {
    val navigation = remember { HSNavigation(NavBackStack()) }
    ComposeAppTheme {
        DataFieldRecipientExtended(
            Address("0x1234567890abcdef1234567890abcdef12345678"),
            BlockchainType.Bitcoin
        ).GetContent(navigation = navigation, borderTop = true)
    }
}
