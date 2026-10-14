package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.currentYear
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
fun CellFooterPreview() {
    ComposeAppTheme {
        CellFooter(text = stringResource(R.string.footer_text, currentYear()))
    }
}
