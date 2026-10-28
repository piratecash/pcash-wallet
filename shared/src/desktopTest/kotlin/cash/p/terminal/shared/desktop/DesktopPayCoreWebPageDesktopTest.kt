package cash.p.terminal.shared.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopPayCoreWebPageDesktopTest {

    private val openedUrls = mutableListOf<String>()
    private val urlOpener = object : UrlOpener {
        override fun open(url: String) {
            openedUrls += url
        }
    }
    private var completed = 0
    private var closed = 0

    @Test
    fun content_shown_opensTheUrlInTheBrowser() = runDesktopComposeUiTest {
        showPage()

        runOnIdle { assertEquals(listOf(URL), openedUrls) }
    }

    @Test
    fun completeButton_clicked_callsTheBackUrlCallback() = runDesktopComposeUiTest {
        showPage()

        onNodeWithText(COMPLETE_TITLE).performClick()

        assertEquals(1, completed)
        assertEquals(0, closed)
    }

    @Test
    fun openAgainButton_clicked_opensTheUrlAgain() = runDesktopComposeUiTest {
        showPage()

        onNodeWithText("Open again").performClick()

        assertEquals(listOf(URL, URL), openedUrls)
        assertEquals(0, completed)
    }

    private fun ComposeUiTest.showPage() = setContent {
        CompositionLocalProvider(LocalUrlOpener provides urlOpener) {
            ComposeAppTheme {
                DesktopPayCoreWebPage.Content(
                    url = URL,
                    title = "KYC",
                    completeTitle = COMPLETE_TITLE,
                    onClose = { closed++ },
                    onInterceptBackUrl = { completed++ },
                    backUrlPrefix = "https://p.cash/back",
                )
            }
        }
    }

    private companion object {
        const val URL = "https://paycore.example/kyc"
        const val COMPLETE_TITLE = "I completed verification"
    }
}
