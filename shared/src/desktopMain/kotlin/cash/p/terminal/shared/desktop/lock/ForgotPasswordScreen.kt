package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_forgot_password_action
import cash.p.terminal.resources.desktop_forgot_password_title
import cash.p.terminal.resources.desktop_forgot_password_warning
import cash.p.terminal.resources.desktop_unlock_data_unreadable
import cash.p.terminal.resources.desktop_unlock_legacy_format
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ForgotPasswordScreen(
    dataUnreadable: Boolean,
    onCancelClick: () -> Unit,
    legacyFormat: Boolean = false,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    ForgotPasswordContent(
        uiState = viewModel.uiState,
        dataUnreadable = dataUnreadable,
        legacyFormat = legacyFormat,
        onUnderstoodChange = viewModel::onUnderstoodChange,
        onDeleteClick = viewModel::onDeleteClick,
        onCancelClick = onCancelClick,
    )
}

@Composable
internal fun ForgotPasswordContent(
    uiState: ForgotPasswordUiState,
    dataUnreadable: Boolean,
    legacyFormat: Boolean,
    onUnderstoodChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    LockScreenLayout(title = stringResource(Res.string.desktop_forgot_password_title)) {
        if (dataUnreadable || legacyFormat) {
            val notice =
                if (legacyFormat) Res.string.desktop_unlock_legacy_format else Res.string.desktop_unlock_data_unreadable
            subhead2_lucian(text = stringResource(notice))
            VSpacer(16.dp)
        }
        TextImportantError(text = stringResource(Res.string.desktop_forgot_password_warning))
        VSpacer(16.dp)
        UnderstandCheckRow(checked = uiState.understood, onCheckedChange = onUnderstoodChange)
        VSpacer(24.dp)
        ButtonPrimaryRed(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.desktop_forgot_password_action),
            onClick = onDeleteClick,
            enabled = uiState.canSubmit,
        )
        VSpacer(8.dp)
        ButtonPrimaryTransparent(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Button_Cancel),
            onClick = onCancelClick,
            enabled = !uiState.inProgress,
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordContentPreview() {
    ComposeAppTheme {
        ForgotPasswordContent(
            uiState = ForgotPasswordUiState(understood = true),
            dataUnreadable = true,
            legacyFormat = false,
            onUnderstoodChange = {},
            onDeleteClick = {},
            onCancelClick = {},
        )
    }
}
