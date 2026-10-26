package cash.p.terminal.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.shared.desktop.DesktopForegroundEffect
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IBackgroundManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalTestApi::class)
class DesktopHostTest {

    @BeforeTest
    fun setUp() = DesktopTestGraph.startUnlocked()

    @AfterTest
    fun tearDown() = DesktopTestGraph.stop()

    @Test
    fun marketTab_selected_showsMarketScreen() = runDesktopComposeUiTest {
        setContent { TestWindowHost { ComposeAppTheme { PcashTestApp() } } }

        onNodeWithContentDescription("Markets").performClick()

        onNodeWithText("Total Cap").assertExists()
    }

    @Test
    fun coinRow_clicked_opensCoinPageAndBackReturnsToMarket() = runDesktopComposeUiTest {
        setContent { TestWindowHost { ComposeAppTheme { PcashTestApp() } } }
        onNodeWithContentDescription("Markets").performClick()
        waitForText("BTC")

        onNodeWithText("BTC").performClick()
        waitForText("Bitcoin")
        waitUntilGone("Total Cap")
        onNodeWithContentDescription("Back").performClick()

        waitForText("Total Cap")
    }

    @Test
    fun baseCurrencyRow_clicked_opensPageKeepingSettingsTabSelected() = runDesktopComposeUiTest {
        setContent { TestWindowHost { ComposeAppTheme { PcashTestApp() } } }
        onNodeWithContentDescription("Settings").performClick()

        onNodeWithText("Base Currency").performClick()
        onNodeWithText("EUR").performClick()
        waitUntilBackButtonGone()

        onNodeWithContentDescription("Settings").assertIsSelected()
        onNodeWithText("Donate").assertExists()
    }

    @Test
    fun baseCurrency_switchedToEur_marketShowsEurValues() = runDesktopComposeUiTest {
        setContent { TestWindowHost { ComposeAppTheme { PcashTestApp() } } }
        onNodeWithContentDescription("Markets").performClick()
        waitForText("$1T")

        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Base Currency").performClick()
        onNodeWithText("EUR").performClick()
        waitUntilBackButtonGone()
        onNodeWithContentDescription("Markets").performClick()

        waitForText("€2T")
    }

    @Test
    fun windowFocus_lost_marksAppInactiveButKeepsWalletRunning() = runDesktopComposeUiTest {
        var focused by mutableStateOf(true)
        var minimized by mutableStateOf(false)
        setWindowContent(focused = { focused }, minimized = { minimized })
        val foreground = KoinPlatform.getKoin().get<AppForegroundState>()
        waitForIdle()
        assertTrue(foreground.inForeground.value)

        focused = false
        minimized = true
        waitUntilWallet(BackgroundManagerState.EnterBackground)
        minimized = false

        waitUntilWallet(BackgroundManagerState.EnterForeground)
        assertFalse(foreground.inForeground.value)
    }

    @Test
    fun windowMinimized_movesWalletToBackground() = runDesktopComposeUiTest {
        var minimized by mutableStateOf(false)
        setWindowContent(focused = { !minimized }, minimized = { minimized })
        waitForIdle()
        assertEquals(BackgroundManagerState.EnterForeground, backgroundManager().stateFlow.value)

        minimized = true
        waitUntilWallet(BackgroundManagerState.EnterBackground)
    }

    private fun ComposeUiTest.waitUntilWallet(state: BackgroundManagerState) =
        waitUntil(timeoutMillis = TIMEOUT_MS) { backgroundManager().stateFlow.value == state }

    private fun ComposeUiTest.setWindowContent(focused: () -> Boolean, minimized: () -> Boolean) {
        val windowInfo = object : WindowInfo {
            override val isWindowFocused get() = focused()
        }
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) { DesktopForegroundEffect(minimized()) }
        }
    }

    private fun backgroundManager() = KoinPlatform.getKoin().get<IBackgroundManager>()

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitUntilGone(text: String) =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.waitUntilBackButtonGone() =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isEmpty() }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
