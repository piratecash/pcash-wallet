package cash.p.terminal.ui_compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparentModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = null,
        sheetState = sheetState,
        containerColor = ComposeAppTheme.colors.transparent,
        scrimColor = ComposeAppTheme.colors.modalOverlay,
        // Only the top inset: it keeps a fully expanded sheet below the status bar and is consumed
        // by the sheet itself while it stays lower. The bottom one is handled by BottomSheetHeader,
        // so that the sheet background reaches under the navigation bar.
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
    ) {
        // Runs in the modal's own window: report its focus so the main screen keeps the content
        // behind it visible while foreground, yet hides it once the window loses focus for recents.
        ModalOverlayTracker.TrackForeground()
        content()
    }
}
