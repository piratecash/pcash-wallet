package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cash.p.terminal.resources.Alert_CustomKeyboardIsUsed
import cash.p.terminal.resources.Alert_Select
import cash.p.terminal.resources.Alert_Skip
import cash.p.terminal.resources.Alert_TitleWarning
import cash.p.terminal.resources.Keyboard
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_24_warning_2
import cash.p.terminal.ui.compose.rememberInputMethodPicker
import cash.p.terminal.ui.compose.rememberThirdPartyKeyboardCheck
import cash.p.terminal.ui_compose.components.AppDialog
import io.horizontalsystems.core.IThirdKeyboard
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Warns when a secret is typed with a third-party keyboard the user has not allowed. */
@Stable
class ThirdPartyKeyboardWarningState(
    private val consent: IThirdKeyboard,
    private val isThirdPartyKeyboard: () -> Boolean,
) {
    var isVisible by mutableStateOf(false)
        private set

    fun onSecretInput() {
        isVisible = !consent.isThirdPartyKeyboardAllowed && isThirdPartyKeyboard()
    }

    fun allow() {
        consent.isThirdPartyKeyboardAllowed = true
        dismiss()
    }

    fun dismiss() {
        isVisible = false
    }
}

@Composable
fun rememberThirdPartyKeyboardWarningState(consent: IThirdKeyboard): ThirdPartyKeyboardWarningState {
    val isThirdPartyKeyboard = rememberThirdPartyKeyboardCheck()
    return remember(consent, isThirdPartyKeyboard) { ThirdPartyKeyboardWarningState(consent, isThirdPartyKeyboard) }
}

@Composable
fun ThirdPartyKeyboardWarning(state: ThirdPartyKeyboardWarningState) {
    val showInputMethodPicker = rememberInputMethodPicker()
    if (state.isVisible) {
        CustomKeyboardWarningDialog(
            onSelect = {
                showInputMethodPicker()
                state.dismiss()
            },
            onSkip = state::allow,
            onCancel = state::dismiss
        )
    }
}

@Composable
private fun CustomKeyboardWarningDialog(
    onSelect: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AppDialog(onDismissRequest = onCancel) {
        BottomSheetsElementsHeader(
            icon = painterResource(Res.drawable.icon_24_warning_2),
            title = stringResource(Res.string.Alert_TitleWarning),
            subtitle = stringResource(Res.string.Keyboard),
            onClickClose = onCancel
        )
        BottomSheetsElementsText(
            text = stringResource(Res.string.Alert_CustomKeyboardIsUsed)
        )
        BottomSheetsElementsButtons(
            buttonPrimaryText = stringResource(Res.string.Alert_Select),
            onClickPrimary = onSelect,
            buttonDefaultText = stringResource(Res.string.Alert_Skip),
            onClickDefault = onSkip
        )
    }
}
