package cash.p.terminal.modules.contacts.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantWarning

@Composable
fun ConfirmationBottomSheet(
    title: String,
    text: String,
    iconPainter: Painter,
    iconTint: ColorFilter,
    confirmText: String,
    cautionType: Caution.Type,
    cancelText: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    BottomSheetHeader(
        iconPainter = iconPainter,
        iconTint = iconTint,
        title = title,
        onCloseClick = onClose
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = text
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (cautionType) {
            Caution.Type.Error -> {
                ButtonPrimaryRed(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    title = confirmText,
                    onClick = {
                        onConfirm()
                    }
                )
            }

            Caution.Type.Warning -> {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    title = confirmText,
                    onClick = {
                        onConfirm()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = cancelText,
            onClick = onClose
        )
        Spacer(Modifier.height(32.dp))
    }
}
