package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
@Preview(showBackground = true)
private fun RowWithArrowPreview() {
    ComposeAppTheme {
        RowWithArrow(
            text = LoremIpsum(20).values.joinToString(),
            showAlert = true,
            onClick = {}
        )
    }
}
