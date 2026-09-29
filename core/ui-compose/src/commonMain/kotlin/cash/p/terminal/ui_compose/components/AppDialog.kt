package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

internal expect fun appDialogProperties(): DialogProperties

/** The platform dims the window behind a dialog on its own; the overlay is drawn by [AppDialog] instead. */
@Composable
internal expect fun DisablePlatformDialogDim()

val AppDialogDefaultModifier: Modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = AppDialogDefaultModifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = appDialogProperties()) {
        DisablePlatformDialogDim()
        CompositionLocalProvider(LocalOnElevatedSurface provides true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeAppTheme.colors.backgroundOverlay)
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .then(modifier)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ComposeAppTheme.colors.surfaceElevated)
                        .pointerInput(Unit) { detectTapGestures { } },
                    content = content,
                )
            }
        }
    }
}
