package cash.p.terminal.modules.settings.advancedsecurity.terms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.AdvancedSecurity_Terms_Title
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SecureReset_Terms_Warning
import cash.p.terminal.resources.delete_all_contacts_terms_checkboxes
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.TermsList
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChecklistTermsScreen(
    title: String,
    terms: List<TermItem>,
    buttonTitle: String,
    buttonEnabled: Boolean,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onNavigateBack: () -> Unit,
    warningContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    checkboxesEnabled: Boolean = true,
) {
    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = title,
                navigationIcon = {
                    HsBackButton(onClick = onNavigateBack)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)
                warningContent()
                VSpacer(12.dp)
                TermsList(
                    terms = terms,
                    onItemClicked = if (checkboxesEnabled) onCheckboxToggle else { _ -> }
                )
            }
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = buttonTitle,
                    onClick = onAgreeClick,
                    enabled = buttonEnabled
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ChecklistTermsScreenPreview() {
    val termTitles = stringArrayResource(Res.array.delete_all_contacts_terms_checkboxes)
    val terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = index == 0
        )
    }

    ComposeAppTheme {
        ChecklistTermsScreen(
            title = stringResource(Res.string.AdvancedSecurity_Terms_Title),
            terms = terms,
            buttonTitle = stringResource(Res.string.Button_IAgree),
            buttonEnabled = false,
            onCheckboxToggle = {},
            onAgreeClick = {},
            onNavigateBack = {},
            warningContent = {
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(Res.string.SecureReset_Terms_Warning)
                )
            }
        )
    }
}
