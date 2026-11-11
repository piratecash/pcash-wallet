package cash.p.terminal.modules.pin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.AdvancedSecurity_Terms_Checkboxes
import cash.p.terminal.resources.AdvancedSecurity_Terms_Warning
import cash.p.terminal.resources.Button_Continue
import cash.p.terminal.resources.DuressPin_Description
import cash.p.terminal.resources.DuressPin_Notes
import cash.p.terminal.resources.DuressPin_Notes_PasscodeChange_Description
import cash.p.terminal.resources.DuressPin_Notes_PasscodeChange_Title
import cash.p.terminal.resources.DuressPin_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_edit_24
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class SetDuressPinIntroPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<SetDuressPinIntroViewModel>()
        val pages = koinInject<DuressSecretPages>()

        SetDuressPinIntroScreen(
            wording = pages.wording,
            onContinueClick = {
                navigation.slideFromRight(
                    if (viewModel.shouldShowSelectAccounts) SetDuressPinSelectAccountsPage() else pages.setDuress(null)
                )
            },
            onBack = navigation::navigateUpSafely,
        )
    }
}

@Composable
internal fun SetDuressPinIntroScreen(
    wording: DuressWording,
    onContinueClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.DuressPin_Title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            InfoText(
                text = stringResource(wording.introDescription),
                paddingBottom = 32.dp
            )
            HeaderText(text = stringResource(Res.string.DuressPin_Notes))

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(12.dp))
            ) {
                wording.introNotes.forEachIndexed { index, note ->
                    NotesCell(note, borderTop = index > 0)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    title = stringResource(Res.string.Button_Continue),
                    onClick = onContinueClick,
                )
            }
        }
    }
}

@Composable
private fun NotesCell(note: DuressNote, borderTop: Boolean) {
    Box {
        if (borderTop) {
            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(note.icon),
                tint = ComposeAppTheme.colors.brandDefault,
                contentDescription = null,
            )
            HSpacer(width = 16.dp)
            Column {
                body_leah(text = stringResource(note.title))
                VSpacer(height = 1.dp)
                subhead2_grey(text = stringResource(note.description))
            }
        }
    }
}

@Preview
@Composable
private fun SetDuressPinIntroScreenPreview() {
    ComposeAppTheme {
        SetDuressPinIntroScreen(
            wording = DuressWording(
                introDescription = Res.string.DuressPin_Description,
                introNotes = listOf(
                    DuressNote(
                        Res.drawable.ic_edit_24,
                        Res.string.DuressPin_Notes_PasscodeChange_Title,
                        Res.string.DuressPin_Notes_PasscodeChange_Description,
                    )
                ),
                termsWarning = Res.string.AdvancedSecurity_Terms_Warning,
                termsCheckboxes = Res.array.AdvancedSecurity_Terms_Checkboxes,
            ),
            onContinueClick = {},
            onBack = {},
        )
    }
}
