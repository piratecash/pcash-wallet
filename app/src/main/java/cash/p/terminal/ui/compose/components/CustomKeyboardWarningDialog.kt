package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cash.p.terminal.ui_compose.components.AppDialog
import cash.p.terminal.R

@Composable
fun CustomKeyboardWarningDialog(
    onSelect: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AppDialog(onDismissRequest = onCancel) {
        BottomSheetsElementsHeader(
            icon = painterResource(R.drawable.icon_24_warning_2),
            title = stringResource(R.string.Alert_TitleWarning),
            subtitle = stringResource(R.string.Keyboard),
            onClickClose = onCancel
        )
        BottomSheetsElementsText(
            text = stringResource(R.string.Alert_CustomKeyboardIsUsed)
        )
        BottomSheetsElementsButtons(
            buttonPrimaryText = stringResource(id = R.string.Alert_Select),
            onClickPrimary = onSelect,
            buttonDefaultText = stringResource(id = R.string.Alert_Skip),
            onClickDefault = onSkip
        )
    }
}
