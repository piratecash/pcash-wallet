package cash.p.terminal.modules.transactionInfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun AmlAddressSelectionBottomSheetPreview() {
    val sampleAddresses = listOf(
        "0x1c6EAa67452C34C95206f5F7C7a6f76Ad81f51" to AmlStatus.Low,
        "0x2d7FBb78563D45D06317g8G8D87bBe92g62" to AmlStatus.Medium,
        "0x3e8GCc89674E56E17428h9H9E98cCf03h73" to AmlStatus.High,
    )
    ComposeAppTheme {
        AmlAddressSelectionContent(
            addresses = sampleAddresses,
            selectedAddress = sampleAddresses.first().first,
            onAddressClick = {},
            onCheckClick = {},
            onLaterClick = {},
            onCloseClick = {}
        )
    }
}
