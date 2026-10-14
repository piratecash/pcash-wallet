package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_24
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    InfoBottomSheet(
        title = title,
        text = AnnotatedString(text),
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    title: String,
    text: AnnotatedString,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_info_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.iconSecondary),
            title = title,
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                }
            }
        ) {
            InfoTextBody(text = text)
            Spacer(modifier = Modifier.height(52.dp))
        }
    }
}
