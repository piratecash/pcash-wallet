package cash.p.terminal.modules.manageaccount.safetyrules

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun SafetyRulesScreenAgreePreview() {
    val termTitles = listOf(
        "I understand that the seed phrase is confidential and should not be disclosed to third parties.",
        "I understand that I cannot store my seed phrase in digital form.",
        "I understand that the p.cash team does not have access to my keys and cannot recover them."
    )
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }
    ComposeAppTheme {
        SafetyRulesScreen(
            uiState = SafetyRulesUiState(
                terms = terms,
                agreeEnabled = false,
                mode = SafetyRulesModule.SafetyRulesMode.AGREE,
                alreadyAgreed = false
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onRiskItClick = {},
            onCancelClick = {}
        )
    }
}

@Preview
@Composable
private fun SafetyRulesScreenCopyConfirmPreview() {
    val termTitles = listOf(
        "I understand that the seed phrase is confidential and should not be disclosed to third parties.",
        "I understand that I cannot store my seed phrase in digital form.",
        "I understand that the p.cash team does not have access to my keys and cannot recover them."
    )
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = true
        )
    }
    ComposeAppTheme {
        SafetyRulesScreen(
            uiState = SafetyRulesUiState(
                terms = terms,
                agreeEnabled = true,
                mode = SafetyRulesModule.SafetyRulesMode.COPY_CONFIRM,
                alreadyAgreed = true
            ),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onRiskItClick = {},
            onCancelClick = {}
        )
    }
}
