package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
fun TermsListPreview() {
    val terms = listOf(
        TermItem(
            0,
            "First term description goes here.",
            "Additional details about the first term.",
            false
        ),
        TermItem(
            1,
            "Second term description goes here.",
            "Additional details about the second term.",
            true
        ),
        TermItem(
            2,
            "Third term description goes here.",
            null,
            false
        )
    )
    ComposeAppTheme {
        TermsList(
            terms = terms,
            onItemClicked = {}
        )
    }
}
