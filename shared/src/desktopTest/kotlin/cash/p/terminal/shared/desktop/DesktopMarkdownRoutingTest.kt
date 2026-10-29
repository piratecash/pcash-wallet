package cash.p.terminal.shared.desktop

import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.markdown.MarkdownPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.ui_compose.components.UrlOpener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopMarkdownRoutingTest {

    private class RecordingUrlOpener : UrlOpener {
        val opened = mutableListOf<String>()
        override fun open(url: String) {
            opened += url
        }
    }

    private val urlOpener = RecordingUrlOpener()
    private val backStack = NavBackStack<HSPage>(DesktopMainPage())
    private val navigation = HSNavigation(backStack)

    @Test
    fun markdown_url_returnsPopupMarkdownPageForThatUrl() {
        val page = DesktopMarketPages().markdown("https://host/guide.md")

        assertIs<MarkdownPage>(page)
        assertEquals(MarkdownPage.Input("https://host/guide.md", showAsPopup = true), page.input)
    }

    @Test
    fun openMarkdownOrWeblink_markdownUrl_opensMarkdownPageInApp() {
        DesktopWalletPages(urlOpener).openMarkdownOrWeblink(navigation, "https://host/guide.md")

        val top = backStack.last()
        assertIs<MarkdownPage>(top)
        assertEquals(MarkdownPage.Input("https://host/guide.md"), top.input)
        assertEquals(emptyList(), urlOpener.opened)
    }

    @Test
    fun openMarkdownOrWeblink_webUrl_opensInBrowser() {
        DesktopWalletPages(urlOpener).openMarkdownOrWeblink(navigation, "https://host/page.html")

        assertEquals(listOf("https://host/page.html"), urlOpener.opened)
        assertIs<DesktopMainPage>(backStack.last())
    }
}
