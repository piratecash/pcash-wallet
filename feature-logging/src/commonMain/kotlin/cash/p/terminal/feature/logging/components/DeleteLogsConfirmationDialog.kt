package cash.p.terminal.feature.logging.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Delete
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.login_logging_delete_data_confirmation
import cash.p.terminal.resources.login_logging_delete_data_title
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteLogsConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String = stringResource(Res.string.login_logging_delete_data_confirmation),
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_delete_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
            title = stringResource(Res.string.login_logging_delete_data_title),
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
        ) {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = text
            )
            Spacer(Modifier.height(20.dp))
            ButtonPrimaryRed(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.Button_Delete),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm() }
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
