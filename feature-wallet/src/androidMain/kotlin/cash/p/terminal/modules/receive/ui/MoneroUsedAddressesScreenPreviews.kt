package cash.p.terminal.modules.receive.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.receive.viewmodels.MoneroSubaddressParcelable
import cash.p.terminal.modules.receive.viewmodels.MoneroUsedAddressesParams
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun MoneroUsedAddressesScreenPreview() {
    ComposeAppTheme {
        MoneroUsedAddressesScreen(
            params = MoneroUsedAddressesParams(
                subaddresses = listOf(
                    MoneroSubaddressParcelable(0, "48ZznXR8cxefi7b88Yf6MsYkENAhA1Aty", 150000000000L),
                    MoneroSubaddressParcelable(1, "89ABcDEF1234567890abcdef12345678ab", 0L),
                )
            ),
            onBackPress = {}
        )
    }
}
