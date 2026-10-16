package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "Choose wallet type")
@Composable
private fun RestoreHeightScreenPreview() {
    ComposeAppTheme {
        RestoreHeightScreen(
            mode = null,
            onModeSelect = {},
            doneEnabled = false,
            onDoneClick = {},
            topBar = {
                AppBar(title = stringResource(R.string.restore_monero))
            },
        )
    }
}

@Preview(name = "Existing wallet")
@Composable
private fun RestoreHeightExistingWalletPreview() {
    ComposeAppTheme {
        RestoreHeightScreen(
            mode = RestoreHeightMode.ExistingWallet,
            onModeSelect = {},
            doneEnabled = true,
            onDoneClick = {},
            topBar = {
                AppBar(title = stringResource(R.string.restore_monero))
            },
            existingWalletContent = {
                Spacer(Modifier.height(16.dp))
                HeaderText(stringResource(R.string.restoreheight_title))
                InputField(
                    value = "2024-01-01",
                    onValueChange = {},
                    placeholderText = stringResource(R.string.restoreheight_hint),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    keyboardType = KeyboardType.Ascii,
                )
            },
        )
    }
}
