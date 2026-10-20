package cash.p.terminal.modules.zcashnetwork.addserver

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.Caution
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun AddZcashServerScreenPreview() {
    ComposeAppTheme {
        AddZcashServerScreen(
            uiState = AddZcashServerViewModel.UiState(
                url = "https://zec.rocks/path",
                caution = Caution("Enter https://host:port", Caution.Type.Error),
            ),
            onUrlChange = {},
            onAddClick = {},
            onClose = {},
        )
    }
}
