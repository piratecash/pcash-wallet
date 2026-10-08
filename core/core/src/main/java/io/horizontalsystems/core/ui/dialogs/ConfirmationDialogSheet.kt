package io.horizontalsystems.core.ui.dialogs

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.BottomSheetDismissHandler
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely

class ConfirmationDialogSheet(
    private val title: String,
    private val icon: Int?,
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
            icon = icon,
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
