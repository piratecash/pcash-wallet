package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Password
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Unlock_AttemptsLeft
import cash.p.terminal.resources.Unlock_Title
import cash.p.terminal.resources.Unlock_WalletDisabledUntil
import cash.p.terminal.resources.desktop_unlock_data_unreadable
import cash.p.terminal.resources.desktop_unlock_failed
import cash.p.terminal.resources.desktop_unlock_forgot_password
import cash.p.terminal.resources.desktop_unlock_title
import cash.p.terminal.resources.desktop_unlock_wrong_password
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.helpers.DateHelper
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
        onUnlockClick = viewModel::onUnlockClick,
        onForgotPasswordClick = onForgotPasswordClick,
        onDataUnreadable = {
            viewModel.onDataUnreadableShown()
            onDataUnreadable()
        },
    )
}

@Composable
internal fun UnlockContent(
    uiState: UnlockUiState,
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    LockScreenLayout(title = stringResource(Res.string.desktop_unlock_title)) {
        DesktopPasswordField(
            value = uiState.password,
            hint = stringResource(Res.string.Password),
            onValueChange = onPasswordChange,
            onSubmit = onUnlockClick,
            enabled = !uiState.inProgress,
            error = uiState.errorMessage(),
            focusRequester = focusRequester,
        )
        (uiState.outcome as? UnlockOutcome.WrongPassword)?.attemptsLeft?.let { attemptsLeft ->
            VSpacer(8.dp)
            subhead2_grey(text = stringResource(Res.string.Unlock_AttemptsLeft, attemptsLeft))
        }
        uiState.lockedOut?.let { lockedOut ->
            VSpacer(8.dp)
            subhead2_grey(
                text = stringResource(
                    Res.string.Unlock_WalletDisabledUntil,
                    DateHelper.getOnlyTime(lockedOut.until),
                )
            )
        }
        VSpacer(24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Unlock_Title),
            onClick = onUnlockClick,
            enabled = uiState.canSubmit,
            loadingIndicator = uiState.inProgress,
        )
        VSpacer(8.dp)
        ButtonPrimaryTransparent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.desktop_unlock_forgot_password),
            onClick = onForgotPasswordClick,
        )
    }
}

@Composable
private fun UnlockUiState.errorMessage(): String? = when (outcome) {
    is UnlockOutcome.WrongPassword -> stringResource(Res.string.desktop_unlock_wrong_password)
    UnlockOutcome.Failed -> stringResource(Res.string.desktop_unlock_failed)
    UnlockOutcome.StorageUnavailable -> stringResource(Res.string.desktop_unlock_data_unreadable)
    else -> null
}

@Preview
@Composable
private fun UnlockContentPreview() {
    ComposeAppTheme {
        UnlockContent(
            uiState = UnlockUiState(
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
