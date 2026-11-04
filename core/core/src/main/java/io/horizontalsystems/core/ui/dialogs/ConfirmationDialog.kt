package io.horizontalsystems.core.ui.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationDialogBottomSheet(
    title: String,
    icon: Int?,
    warningTitle: String?,
    warningText: String?,
    actionButtonTitle: String?,
    transparentButtonTitle: String?,
    onCloseClick: () -> Unit,
    onActionButtonClick: () -> Unit,
    onTransparentButtonClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onCloseClick,
        sheetState = sheetState,
    ) {
        ConfirmationDialog(
            title = title,
            iconPainter = icon?.let { painterResource(it) },
            warningTitle = warningTitle,
            warningText = warningText,
            actionButtonTitle = actionButtonTitle,
            transparentButtonTitle = transparentButtonTitle,
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onCloseClick()
                }
            },
            onActionButtonClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onActionButtonClick()
                }
            },
            onTransparentButtonClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onTransparentButtonClick()
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmationDialogPreview() {
    ComposeAppTheme {
        ConfirmationDialog(
            title = "Preview title",
            iconPainter = null,
            warningTitle = "Warning title",
            warningText = "Warning text",
            actionButtonTitle = "Confirm",
            transparentButtonTitle = "Cancel",
            onCloseClick = {},
            onActionButtonClick = {},
            onTransparentButtonClick = {}
        )
    }
}
