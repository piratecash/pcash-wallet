package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Confirm
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Unlock_Title
import cash.p.terminal.resources.desktop_unlock_title
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The desktop counterpart of Android's ConfirmPinPage: the app password again, under the unlock lockout. */
class ConfirmPasswordPage : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: ConfirmPasswordViewModel = koinViewModel()
        LaunchedEffect(viewModel.confirmed) {
            // A page already popped by Back was cancelled, even if the check finishes while it animates out.
            if (viewModel.confirmed && navigation.navigateUpFrom(this@ConfirmPasswordPage)) {
                navigation.setResult(this@ConfirmPasswordPage, Result(success = true))
            }
        }
        ConfirmPasswordContent(
            uiState = viewModel.uiState,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmClick = viewModel::onSubmitClick,
            onBack = navigation::navigateUpSafely,
        )
    }

    data class Result(val success: Boolean)
}

@Composable
internal fun ConfirmPasswordContent(
    uiState: PasswordCheckUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Unlock_Title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .centeredForm(rememberScrollState()),
        ) {
            VSpacer(24.dp)
            subhead2_grey(text = stringResource(Res.string.desktop_unlock_title))
            VSpacer(16.dp)
            PasswordCheckForm(
                uiState = uiState,
                submitTitle = stringResource(Res.string.Button_Confirm),
                onPasswordChange = onPasswordChange,
                onSubmit = onConfirmClick,
            )
            VSpacer(24.dp)
        }
    }
}

@Preview
@Composable
private fun ConfirmPasswordContentPreview() {
    ComposeAppTheme {
        ConfirmPasswordContent(
            uiState = PasswordCheckUiState(
                password = "secret",
                outcome = UnlockOutcome.WrongPassword(attemptsLeft = 3),
            ),
            onPasswordChange = {},
            onConfirmClick = {},
            onBack = {},
        )
    }
}
