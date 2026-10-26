package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
@Preview(showBackground = true)
private fun SwitchWithTextPreview() {
    ComposeAppTheme {
        Column {
            SwitchWithText(
                text = LoremIpsum(20).values.joinToString(),
                checked = true,
                onCheckedChange = {}
            )
            SwitchWithTextWarning(
                text = LoremIpsum(20).values.joinToString(),
                checked = false,
                onWarningIconClick = {},
                onCheckedChange = {}
            )
        }
    }
}
