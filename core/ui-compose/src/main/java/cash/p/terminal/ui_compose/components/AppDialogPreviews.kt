package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun AppDialogPreview() = AppDialogPreviewContent(darkTheme = false)

@Preview
@Composable
private fun AppDialogDarkPreview() = AppDialogPreviewContent(darkTheme = true)

@Composable
private fun AppDialogPreviewContent(darkTheme: Boolean) {
    ComposeAppTheme(darkTheme = darkTheme) {
        AppDialog(onDismissRequest = {}) {
            Column(Modifier.padding(24.dp)) {
                Text("Title", color = ComposeAppTheme.colors.textPrimary)
            }
        }
    }
}

@Preview
@Composable
private fun AppSelectorDialogPreview() = AppSelectorDialogPreviewContent(darkTheme = false)

@Preview
@Composable
private fun AppSelectorDialogDarkPreview() = AppSelectorDialogPreviewContent(darkTheme = true)

@Composable
private fun AppSelectorDialogPreviewContent(darkTheme: Boolean) {
    val items = List(30) { AppSelectorItem("Item $it", it == 1, it, if (it == 2) "Subtitle" else null) }
    ComposeAppTheme(darkTheme = darkTheme) {
        AppSelectorDialog(title = "Title", items = items, onSelect = {}, onDismiss = {})
    }
}
