package cash.p.terminal.modules.settings.advancedsecurity.terms

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.AdvancedSecurity_CreateHiddenWallet
import cash.p.terminal.resources.AdvancedSecurity_Terms_Checkboxes
import cash.p.terminal.resources.AdvancedSecurity_Terms_Title
import cash.p.terminal.resources.AdvancedSecurity_Terms_Warning
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HiddenWalletTermsScreen(
    uiState: HiddenWalletTermsUiState,
    warning: StringResource,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    ChecklistTermsScreen(
        title = stringResource(Res.string.AdvancedSecurity_Terms_Title),
        terms = uiState.terms,
        buttonTitle = if (uiState.agreeEnabled) {
            stringResource(Res.string.AdvancedSecurity_CreateHiddenWallet)
        } else {
            stringResource(Res.string.Button_IAgree)
        },
        buttonEnabled = uiState.agreeEnabled,
        onCheckboxToggle = onCheckboxToggle,
        onAgreeClick = onAgreeClick,
        onNavigateBack = onNavigateBack,
        warningContent = {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(warning)
            )
        }
    )
}

@Preview
@Composable
private fun HiddenWalletTermsScreenPreview() {
    val termTitles = stringArrayResource(Res.array.AdvancedSecurity_Terms_Checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }
    ComposeAppTheme {
        HiddenWalletTermsScreen(
            uiState = HiddenWalletTermsUiState(
                terms = terms,
                agreeEnabled = false
            ),
            warning = Res.string.AdvancedSecurity_Terms_Warning,
            onCheckboxToggle = {},
            onAgreeClick = {},
            onNavigateBack = {}
        )
    }
}
