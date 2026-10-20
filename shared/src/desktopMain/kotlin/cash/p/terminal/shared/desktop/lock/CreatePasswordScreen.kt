package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Create
import cash.p.terminal.resources.Password
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_create_password_description
import cash.p.terminal.resources.desktop_create_password_failed
import cash.p.terminal.resources.desktop_create_password_title
import cash.p.terminal.resources.desktop_create_password_warning
import cash.p.terminal.resources.desktop_password_mismatch
import cash.p.terminal.resources.desktop_password_repeat_hint
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CreatePasswordScreen(viewModel: CreatePasswordViewModel = koinViewModel()) {
    CreatePasswordContent(
        uiState = viewModel.uiState,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmationChange = viewModel::onConfirmationChange,
        onUnderstoodChange = viewModel::onUnderstoodChange,
        onCreateClick = viewModel::onCreateClick,
    )
}

@Composable
internal fun CreatePasswordContent(
    uiState: CreatePasswordUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onUnderstoodChange: (Boolean) -> Unit,
    onCreateClick: () -> Unit,
) {
    LockScreenLayout(title = stringResource(Res.string.desktop_create_password_title)) {
        subhead2_grey(text = stringResource(Res.string.desktop_create_password_description))
        VSpacer(24.dp)
        DesktopPasswordField(
            value = uiState.password,
            hint = stringResource(Res.string.Password),
            onValueChange = onPasswordChange,
            onSubmit = onCreateClick,
            enabled = !uiState.inProgress,
        )
        VSpacer(16.dp)
        DesktopPasswordField(
            value = uiState.confirmation,
            hint = stringResource(Res.string.desktop_password_repeat_hint),
            onValueChange = onConfirmationChange,
            onSubmit = onCreateClick,
            enabled = !uiState.inProgress,
            error = stringResource(Res.string.desktop_password_mismatch).takeIf { uiState.mismatch },
        )
        VSpacer(16.dp)
        PasswordRequirements(uiState.unmetRequirements, uiState.invalidCharacters)
        VSpacer(24.dp)
        TextImportantWarning(text = stringResource(Res.string.desktop_create_password_warning))
        VSpacer(16.dp)
        UnderstandCheckRow(checked = uiState.understood, onCheckedChange = onUnderstoodChange)
        VSpacer(24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Button_Create),
            onClick = onCreateClick,
            enabled = uiState.canSubmit,
            loadingIndicator = uiState.inProgress,
        )
        if (uiState.failed) {
            VSpacer(12.dp)
            subhead2_lucian(text = stringResource(Res.string.desktop_create_password_failed))
        }
    }
}

@Preview
@Composable
private fun CreatePasswordContentPreview() {
    ComposeAppTheme {
        CreatePasswordContent(
            uiState = CreatePasswordUiState(password = "Battery-sta", confirmation = "Battery"),
            onPasswordChange = {},
            onConfirmationChange = {},
            onUnderstoodChange = {},
            onCreateClick = {},
        )
    }
}
