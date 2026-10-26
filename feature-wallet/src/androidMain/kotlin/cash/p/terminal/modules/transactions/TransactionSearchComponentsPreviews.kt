package cash.p.terminal.modules.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(showBackground = true)
@Composable
private fun SearchEmptyResultsPreview() {
    ComposeAppTheme {
        SearchEmptyResultsView()
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchInProgressPreview() {
    ComposeAppTheme {
        SearchInProgressView()
    }
}
