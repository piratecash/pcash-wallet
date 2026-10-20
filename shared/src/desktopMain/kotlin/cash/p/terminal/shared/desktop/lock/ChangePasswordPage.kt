package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Change
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_change_password_corrupted
import cash.p.terminal.resources.desktop_change_password_current_hint
import cash.p.terminal.resources.desktop_change_password_failed
import cash.p.terminal.resources.desktop_change_password_new_hint
import cash.p.terminal.resources.desktop_change_password_success
import cash.p.terminal.resources.desktop_change_password_title
import cash.p.terminal.resources.desktop_password_mismatch
import cash.p.terminal.resources.desktop_password_repeat_hint
import cash.p.terminal.resources.desktop_unlock_wrong_password
import cash.p.terminal.shared.desktop.vault.ChangePasswordOutcome
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

class ChangePasswordPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: ChangePasswordViewModel = koinViewModel()
        val hudMessenger = LocalHudMessenger.current
        val successMessage = stringResource(Res.string.desktop_change_password_success)
        LaunchedEffect(viewModel.uiState.outcome) {
            if (viewModel.uiState.outcome == ChangePasswordOutcome.SUCCESS) {
                hudMessenger.showSuccess(successMessage)
                navigation.navigateUpFrom(this@ChangePasswordPage)
            }
        }
        ChangePasswordContent(
            uiState = viewModel.uiState,
            onCurrentPasswordChange = viewModel::onCurrentPasswordChange,
            onNewPasswordChange = viewModel::onNewPasswordChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onChangeClick = viewModel::onChangeClick,
            onBack = navigation::navigateUpSafely,
        )
    }
}

@Composable
internal fun ChangePasswordContent(
    uiState: ChangePasswordUiState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onChangeClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.desktop_change_password_title),
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
            DesktopPasswordField(
                value = uiState.currentPassword,
                hint = stringResource(Res.string.desktop_change_password_current_hint),
                onValueChange = onCurrentPasswordChange,
                onSubmit = onChangeClick,
                enabled = !uiState.inProgress,
                error = stringResource(Res.string.desktop_unlock_wrong_password)
                    .takeIf { uiState.outcome == ChangePasswordOutcome.WRONG_PASSWORD },
            )
            VSpacer(16.dp)
            DesktopPasswordField(
                value = uiState.newPassword,
                hint = stringResource(Res.string.desktop_change_password_new_hint),
                onValueChange = onNewPasswordChange,
                onSubmit = onChangeClick,
                enabled = !uiState.inProgress,
            )
            VSpacer(16.dp)
            DesktopPasswordField(
                value = uiState.confirmation,
                hint = stringResource(Res.string.desktop_password_repeat_hint),
                onValueChange = onConfirmationChange,
                onSubmit = onChangeClick,
                enabled = !uiState.inProgress,
                error = stringResource(Res.string.desktop_password_mismatch).takeIf { uiState.mismatch },
            )
            VSpacer(16.dp)
            PasswordRequirements(uiState.unmetRequirements, uiState.invalidCharacters)
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Change),
                onClick = onChangeClick,
                enabled = uiState.canSubmit,
                loadingIndicator = uiState.inProgress,
            )
            OutcomeMessage(uiState.outcome)
            VSpacer(24.dp)
        }
    }
}

@Composable
private fun OutcomeMessage(outcome: ChangePasswordOutcome?) {
    when (outcome) {
        ChangePasswordOutcome.FAILED -> {
            VSpacer(12.dp)
            subhead2_lucian(text = stringResource(Res.string.desktop_change_password_failed))
        }
        ChangePasswordOutcome.CORRUPTED -> {
            VSpacer(12.dp)
            TextImportantWarning(text = stringResource(Res.string.desktop_change_password_corrupted))
        }
        else -> Unit
    }
}

@Preview
@Composable
private fun ChangePasswordContentPreview() {
    ComposeAppTheme {
        ChangePasswordContent(
            uiState = ChangePasswordUiState(
                currentPassword = "old",
                outcome = ChangePasswordOutcome.WRONG_PASSWORD,
            ),
            onCurrentPasswordChange = {},
            onNewPasswordChange = {},
            onConfirmationChange = {},
            onChangeClick = {},
            onBack = {},
        )
    }
}
