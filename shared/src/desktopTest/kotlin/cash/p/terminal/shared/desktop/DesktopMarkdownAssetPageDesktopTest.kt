package cash.p.terminal.shared.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopMarkdownAssetPageDesktopTest {

    private val page = DesktopMarkdownAssetPage(FaqTopic.PrivateKeys.assetPrefix)
    private val navigation = HSNavigation(NavBackStack<HSPage>(DesktopMainPage(), page))
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        DesktopTestGraph.start()
        loadKoinModules(module { single<UrlOpener> { NoUrlOpener } })
    }

    @AfterTest
    fun tearDown() {
        DesktopTestGraph.stop()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun getContent_bundledAsset_showsHeadingFromEnglishFile() = runDesktopComposeUiTest {
        showApp()

        waitForHeading()

        onNodeWithText(HEADING).assertExists()
    }

    @Test
    fun getContent_closeClicked_popsThePage() = runDesktopComposeUiTest {
        showApp()
        waitForHeading()

        onNodeWithContentDescription(CLOSE).performClick()

        assertEquals(listOf<HSPage>(navigation.backStack.first()), navigation.backStack.toList())
    }

    private fun ComposeUiTest.showApp() = setContent {
        TestWindowHost {
            ComposeAppTheme {
                PcashApp(remember { navigation })
            }
        }
    }

    private fun ComposeUiTest.waitForHeading() =
        waitUntil(timeoutMillis = TIMEOUT_MS) { onAllNodesWithText(HEADING).fetchSemanticsNodes().isNotEmpty() }

    private object NoUrlOpener : UrlOpener {
        override fun open(url: String) = Unit
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val HEADING = "What are private keys?"
        const val CLOSE = "Close"
    }
}
