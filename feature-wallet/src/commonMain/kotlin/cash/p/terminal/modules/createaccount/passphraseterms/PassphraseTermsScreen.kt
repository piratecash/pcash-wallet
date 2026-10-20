package cash.p.terminal.modules.createaccount.passphraseterms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TermsList
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.passphrase_terms_title
import cash.p.terminal.resources.passphrase_terms_warning

@Composable
fun PassphraseTermsScreen(
    uiState: PassphraseTermsUiState,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.passphrase_terms_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
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
                if (!uiState.alreadyAgreed) {
                    VSpacer(12.dp)
                    TextImportantWarning(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = stringResource(Res.string.passphrase_terms_warning)
                    )
                }
                VSpacer(12.dp)
                TermsList(
                    terms = uiState.terms,
                    onItemClicked = onCheckboxToggle
                )
            }
            if (!uiState.alreadyAgreed) {
                ButtonsGroupWithShade {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        title = stringResource(Res.string.Button_IAgree),
                        enabled = uiState.agreeEnabled,
                        onClick = onAgreeClick,
                    )
                }
            }
        }
    }
}
