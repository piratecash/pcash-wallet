package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun AddSourceScreenPreview() {
    ComposeAppTheme {
        AddSourceScreen(title = "Add source", onClose = {}, onAdd = {}) {
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                hint = "",
                onValueChange = {}
            )
        }
    }
}
