package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Unlock_Title
import cash.p.terminal.resources.desktop_unlock_forgot_password
import cash.p.terminal.resources.desktop_unlock_title
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun UnlockScreen(
    onForgotPasswordClick: () -> Unit,
    onDataUnreadable: () -> Unit,
    viewModel: UnlockViewModel = koinViewModel(),
) {
    UnlockContent(
        uiState = viewModel.uiState,
        onPasswordChange = viewModel::onPasswordChange,
        onUnlockClick = viewModel::onSubmitClick,
        onForgotPasswordClick = onForgotPasswordClick,
        onDataUnreadable = {
            viewModel.onDataUnreadableShown()
            onDataUnreadable()
        },
    )
}

@Composable
internal fun UnlockContent(
    uiState: PasswordCheckUiState,
    onPasswordChange: (String) -> Unit,
    onUnlockClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onDataUnreadable: () -> Unit,
) {
    // Unreadable data cannot be unlocked by any password: the only way out is the reset screen.
    val reportDataUnreadable by rememberUpdatedState(onDataUnreadable)
    LaunchedEffect(uiState.outcome) {
        if (uiState.outcome == UnlockOutcome.Corrupted) reportDataUnreadable()
    }
    LockScreenLayout(title = stringResource(Res.string.desktop_unlock_title)) {
        PasswordCheckForm(
            uiState = uiState,
            submitTitle = stringResource(Res.string.Unlock_Title),
            onPasswordChange = onPasswordChange,
            onSubmit = onUnlockClick,
        )
        VSpacer(8.dp)
        ButtonPrimaryTransparent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.desktop_unlock_forgot_password),
            onClick = onForgotPasswordClick,
        )
    }
}

@Preview
@Composable
private fun UnlockContentPreview() {
    ComposeAppTheme {
        UnlockContent(
            uiState = PasswordCheckUiState(
                password = "secret",
                outcome = UnlockOutcome.WrongPassword(attemptsLeft = 3),
            ),
            onPasswordChange = {},
            onUnlockClick = {},
            onForgotPasswordClick = {},
            onDataUnreadable = {},
        )
    }
}
