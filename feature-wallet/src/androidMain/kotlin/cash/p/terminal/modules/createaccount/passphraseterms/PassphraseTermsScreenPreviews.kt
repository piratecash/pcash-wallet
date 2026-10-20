package cash.p.terminal.modules.createaccount.passphraseterms

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.passphrase_terms_checkboxes
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringArrayResource

@Preview
@Composable
private fun PassphraseTermsScreenPreview() {
    val termTitles = stringArrayResource(Res.array.passphrase_terms_checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }
    ComposeAppTheme {
        PassphraseTermsScreen(
            uiState = PassphraseTermsUiState(
                terms = terms,
                agreeEnabled = false,
                alreadyAgreed = false
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onBackClick = {}
        )
    }
}

@Preview
@Composable
private fun PassphraseTermsScreenAlreadyAgreedPreview() {
    val termTitles = stringArrayResource(Res.array.passphrase_terms_checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = true
        )
    }
    ComposeAppTheme {
        PassphraseTermsScreen(
            uiState = PassphraseTermsUiState(
                terms = terms,
                agreeEnabled = true,
                alreadyAgreed = true
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onBackClick = {}
        )
    }
}
