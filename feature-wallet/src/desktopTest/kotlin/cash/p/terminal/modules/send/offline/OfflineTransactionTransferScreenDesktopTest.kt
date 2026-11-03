package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OfflineTransactionTransferScreenDesktopTest {

    @Test
    fun transferHeader_wideWindow_titleIsCentered() = runDesktopComposeUiTest {
        setContent {
            ComposeAppTheme {
                CompositionLocalProvider(LocalHudMessenger provides mockk(relaxed = true)) {
                    OfflineTransactionTransferScreen(
                        transaction = OfflineSignedTransaction("00", "pcash:tx:v1:dogecoin:x", "hash", 0),
                        selectedFormat = OfflineTransactionFormat.Pcash,
                        onBackClick = {},
                        onDoneClick = {},
                        files = mockk(relaxed = true),
                        qrImageSaver = mockk(relaxed = true),
                        clipboardManager = mockk(relaxed = true),
                    )
                }
            }
        }

        val root = onRoot().getBoundsInRoot()
        val title = onNodeWithText("P.cash Transaction").getBoundsInRoot()
        assertEquals((root.left + root.right).value / 2, (title.left + title.right).value / 2, absoluteTolerance = 1f)
    }
}
