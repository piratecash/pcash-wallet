package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(showBackground = true)
@Composable
private fun InfoBottomSheetPreview() {
    ComposeAppTheme {
        InfoBottomSheet(
            title = "Network Fee",
            text = "The fee paid to the blockchain miners for processing the transaction.",
            onDismiss = {}
        )
    }
}
