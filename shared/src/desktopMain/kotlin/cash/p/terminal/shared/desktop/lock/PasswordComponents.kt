package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Understand
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Unlock_AttemptsLeft
import cash.p.terminal.resources.Unlock_WalletDisabledUntil
import cash.p.terminal.resources.desktop_password_caps_lock
import cash.p.terminal.resources.desktop_password_requirement_digit
import cash.p.terminal.resources.desktop_password_requirement_length
import cash.p.terminal.resources.desktop_password_requirement_lowercase
import cash.p.terminal.resources.desktop_password_requirement_special
import cash.p.terminal.resources.desktop_password_requirement_uppercase
import cash.p.terminal.resources.desktop_password_switch_layout
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.components.subhead2_remus
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.PasswordRequirement
import io.horizontalsystems.core.helpers.DateHelper
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.resources.Password
import cash.p.terminal.resources.desktop_unlock_data_unreadable
import cash.p.terminal.resources.desktop_unlock_failed
import cash.p.terminal.resources.desktop_unlock_wrong_password
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow

/** The lock screens cover the whole window: an opaque sheet with one centered column on it. */
@Composable
internal fun LockScreenLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.backgroundBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PasswordFormMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            title3_leah(text = title)
            VSpacer(16.dp)
            content()
        }
    }
}

/** Masked single-line field; Caps Lock is re-read on every key stroke, the only way to notice it. */
@Composable
internal fun DesktopPasswordField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    focusRequester: FocusRequester? = null,
) {
    val capsLock = rememberDesktopCapsLockState()
    val state = remember(error) { error?.let { DataState.Error(Exception(it)) } }
    Column(modifier) {
        Box(
            Modifier
                .focusGroup()
                .let { if (focusRequester == null) it else it.focusRequester(focusRequester) }
                .onPreviewKeyEvent { event ->
                    capsLock.refresh()
                    event.isSubmit().also { if (it) onSubmit() }
                }
        ) {
            FormsInput(
                hint = hint,
                initial = value,
                enabled = enabled,
                singleLine = true,
                pasteEnabled = false,
                showDeleteButton = false,
                state = state,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                onValueChange = onValueChange,
            )
        }
        if (capsLock.isOn) {
            subhead2_lucian(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                text = stringResource(Res.string.desktop_password_caps_lock),
            )
        }
    }
}

/** The password, what the shared lockout reports after a failed attempt, and the submit button. */
@Composable
internal fun PasswordCheckForm(
    uiState: PasswordCheckUiState,
    submitTitle: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxWidth()) {
        DesktopPasswordField(
            value = uiState.password,
            hint = stringResource(Res.string.Password),
            onValueChange = onPasswordChange,
            onSubmit = onSubmit,
            enabled = !uiState.inProgress,
            error = uiState.errorMessage(),
            focusRequester = focusRequester,
        )
        uiState.attemptsLeft?.let {
            VSpacer(8.dp)
            subhead2_grey(text = stringResource(Res.string.Unlock_AttemptsLeft, it))
        }
        uiState.lockedOut?.let {
            VSpacer(8.dp)
            val until = DateHelper.getOnlyTime(it.until)
            subhead2_grey(text = stringResource(Res.string.Unlock_WalletDisabledUntil, until))
        }
        VSpacer(24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = submitTitle,
            onClick = onSubmit,
            enabled = uiState.canSubmit,
            loadingIndicator = uiState.inProgress,
        )
    }
}

@Composable
private fun PasswordCheckUiState.errorMessage(): String? = when (outcome) {
    is UnlockOutcome.WrongPassword -> stringResource(Res.string.desktop_unlock_wrong_password)
    UnlockOutcome.Failed -> stringResource(Res.string.desktop_unlock_failed)
    UnlockOutcome.Corrupted,
    UnlockOutcome.StorageUnavailable -> stringResource(Res.string.desktop_unlock_data_unreadable)
    else -> null
}

private fun KeyEvent.isSubmit(): Boolean =
    type == KeyEventType.KeyDown && (key == Key.Enter || key == Key.NumPadEnter)

@Composable
internal fun PasswordRequirements(
    unmet: List<PasswordRequirement>,
    invalidCharacters: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PasswordRequirement.entries.forEach { requirement ->
            RequirementRow(requirement.title(), met = requirement !in unmet)
        }
        if (invalidCharacters) {
            VSpacer(8.dp)
            subhead2_lucian(text = stringResource(Res.string.desktop_password_switch_layout))
        }
    }
}

@Composable
internal fun UnderstandCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HsCheckbox(checked = checked, onCheckedChange = null)
        HSpacer(8.dp)
        body_leah(text = stringResource(Res.string.Button_Understand))
    }
}

@Composable
private fun RequirementRow(title: StringResource, met: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(Res.drawable.ic_checkmark_20),
            contentDescription = null,
            tint = if (met) ComposeAppTheme.colors.statusSuccess else ComposeAppTheme.colors.iconDisabled,
        )
        HSpacer(8.dp)
        if (met) {
            subhead2_remus(text = stringResource(title))
        } else {
            subhead2_grey(text = stringResource(title))
        }
    }
}

internal val PasswordFormMaxWidth = 420.dp

/** Scrolls across the whole window but keeps the fields as narrow as on the lock screens. */
internal fun Modifier.centeredForm(scrollState: ScrollState): Modifier = fillMaxWidth()
    .verticalScroll(scrollState)
    .wrapContentWidth()
    .widthIn(max = PasswordFormMaxWidth)
    .padding(horizontal = 16.dp)

private fun PasswordRequirement.title(): StringResource = when (this) {
    PasswordRequirement.MIN_LENGTH -> Res.string.desktop_password_requirement_length
    PasswordRequirement.UPPERCASE -> Res.string.desktop_password_requirement_uppercase
    PasswordRequirement.LOWERCASE -> Res.string.desktop_password_requirement_lowercase
    PasswordRequirement.DIGIT -> Res.string.desktop_password_requirement_digit
    PasswordRequirement.SPECIAL -> Res.string.desktop_password_requirement_special
}
