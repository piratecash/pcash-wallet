package cash.p.terminal.shared.desktop.journal

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.settings.advancedsecurity.terms.ChecklistTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsUiState
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.AdvancedSecurity_Terms_Title
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_delete_contacts_terms
import cash.p.terminal.resources.desktop_delete_contacts_terms_warning
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The checklist is the one the hidden wallet's terms use; only its texts differ. */
class DesktopDeleteContactsTermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val termTitles = stringArrayResource(Res.array.desktop_delete_contacts_terms).toTypedArray()
        val viewModel: HiddenWalletTermsViewModel = koinViewModel { parametersOf(termTitles) }

        DeleteContactsTermsContent(
            uiState = viewModel.uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = { navigation.slideFromRight(SetJournalSecretPage(JournalSecretKind.DELETE_CONTACTS)) },
            onNavigateBack = navigation::navigateUpSafely,
        )
    }
}

@Composable
private fun DeleteContactsTermsContent(
    uiState: HiddenWalletTermsUiState,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    ChecklistTermsScreen(
        title = stringResource(Res.string.AdvancedSecurity_Terms_Title),
        terms = uiState.terms,
        buttonTitle = stringResource(Res.string.Button_IAgree),
        buttonEnabled = uiState.agreeEnabled,
        onCheckboxToggle = onCheckboxToggle,
        onAgreeClick = onAgreeClick,
        onNavigateBack = onNavigateBack,
        warningContent = {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.desktop_delete_contacts_terms_warning),
            )
        },
    )
}

@Preview
@Composable
private fun DeleteContactsTermsContentPreview() {
    val terms = stringArrayResource(Res.array.desktop_delete_contacts_terms)
        .mapIndexed { index, title -> TermItem(id = index, title = title, checked = index == 0) }
    ComposeAppTheme {
        DeleteContactsTermsContent(
            uiState = HiddenWalletTermsUiState(terms = terms, agreeEnabled = false),
            onCheckboxToggle = {},
            onAgreeClick = {},
            onNavigateBack = {},
        )
    }
}
