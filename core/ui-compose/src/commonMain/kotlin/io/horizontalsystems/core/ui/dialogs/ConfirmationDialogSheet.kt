package io.horizontalsystems.core.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.BottomSheetDismissHandler
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

class ConfirmationDialogSheet(
    private val title: String,
    private val icon: DrawableResource?,
    private val warningTitle: String?,
    private val warningText: String?,
    private val actionButtonTitle: String?,
    private val transparentButtonTitle: String?,
    private val listener: Listener,
) : HSBottomSheet() {

    interface Listener {
        fun onActionButtonClick() {}
        fun onTransparentButtonClick() {}
        fun onCancelButtonClick() {}
    }

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        BottomSheetDismissHandler {
            listener.onCancelButtonClick()
            navigation.navigateUpSafely()
        }
        ConfirmationDialog(
            title = title,
            iconPainter = icon?.let { painterResource(it) },
            warningTitle = warningTitle,
            warningText = warningText,
            actionButtonTitle = actionButtonTitle,
            transparentButtonTitle = transparentButtonTitle,
            onCloseClick = {
                listener.onCancelButtonClick()
                navigation.navigateUpSafely()
            },
            onActionButtonClick = {
                listener.onActionButtonClick()
                navigation.navigateUpSafely()
            },
            onTransparentButtonClick = {
                listener.onTransparentButtonClick()
                navigation.navigateUpSafely()
            }
        )
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    iconPainter: Painter?,
    warningTitle: String?,
    warningText: String?,
    actionButtonTitle: String?,
    transparentButtonTitle: String?,
    onCloseClick: () -> Unit,
    onActionButtonClick: () -> Unit,
    onTransparentButtonClick: () -> Unit,
) {
    BottomSheetHeader(
        iconPainter = iconPainter ?: painterResource(Res.drawable.ic_attention_24),
        iconTint = ColorFilter.Companion.tint(ComposeAppTheme.colors.statusWarning),
        title = title,
        onCloseClick = onCloseClick
    ) {

        warningText?.let {
            TextImportantWarning(
                title = warningTitle,
                modifier = Modifier.Companion.padding(horizontal = 16.dp, vertical = 12.dp),
                text = it
            )
            Spacer(Modifier.Companion.height(8.dp))
        }
        actionButtonTitle?.let {
            ButtonPrimaryYellow(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp),
                title = actionButtonTitle,
                onClick = onActionButtonClick
            )
        }
        transparentButtonTitle?.let {
            ButtonPrimaryTransparent(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp),
                title = transparentButtonTitle,
                onClick = onTransparentButtonClick
            )
        }
        Spacer(Modifier.Companion.height(32.dp))
    }
}
