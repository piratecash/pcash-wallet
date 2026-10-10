package cash.p.terminal.modules.zcashnetwork.addserver

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.ui.compose.components.AddSourceScreen
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.toInputState
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun AddZcashServerScreen(
    uiState: AddZcashServerViewModel.UiState,
    onUrlChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    AddSourceScreen(
        title = stringResource(R.string.zcash_network_add_server),
        onClose = onClose,
        onAdd = onAddClick,
        windowInsets = windowInsets
    ) {
        HeaderText(stringResource(R.string.zcash_network_server_url))
        FormsInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            initial = uiState.url,
            singleLine = true,
            hint = "",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            state = uiState.caution.toInputState(),
            onValueChange = onUrlChange
        )
    }
}

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
