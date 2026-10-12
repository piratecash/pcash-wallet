package cash.p.terminal.ui_compose.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.ui_compose.ModalOverlayTracker
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class TransparentModalBottomSheetDesktopTest {

    @Test
    fun sheet_opensAndCloses_foregroundFlagFollowsAfterGrace() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            ComposeAppTheme {
                if (open) {
                    TransparentModalBottomSheet(
                        onDismissRequest = { open = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        Text("Sheet body")
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Sheet body").assertExists()
        assertTrue(ModalOverlayTracker.hasForegroundModal)

        open = false
        waitForIdle()

        onNodeWithText("Sheet body").assertDoesNotExist()
        assertTrue(ModalOverlayTracker.hasForegroundModal, "grace period must keep the flag raised")
        waitUntil(timeoutMillis = 5_000) { !ModalOverlayTracker.hasForegroundModal }
        assertFalse(ModalOverlayTracker.hasForegroundModal)
    }
}
