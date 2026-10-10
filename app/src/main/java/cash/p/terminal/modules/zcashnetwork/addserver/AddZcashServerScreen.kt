package cash.p.terminal.modules.zcashnetwork.addserver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.FormsInputStateWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun AddZcashServerScreen(
    uiState: AddZcashServerViewModel.UiState,
    onUrlChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        AppBar(
            title = stringResource(R.string.zcash_network_add_server),
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(R.string.Button_Close),
                    icon = R.drawable.ic_close_24,
                    onClick = onClose
                )
            )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
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
            Spacer(Modifier.height(60.dp))
        }

        ButtonsGroupWithShade {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.Button_Add),
                onClick = onAddClick,
            )
        }
    }
}

private fun Caution?.toInputState() = when (this?.type) {
    Caution.Type.Error -> DataState.Error(Exception(text))
    Caution.Type.Warning -> DataState.Error(FormsInputStateWarning(text))
    null -> null
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
