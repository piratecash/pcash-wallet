package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.Alert_CustomKeyboardIsUsed
import cash.p.terminal.resources.Alert_Select
import cash.p.terminal.resources.Alert_Skip
import cash.p.terminal.resources.Alert_TitleWarning
import cash.p.terminal.resources.Keyboard
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_24_warning_2
import cash.p.terminal.ui_compose.components.AppDialog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CustomKeyboardWarningDialog(
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
