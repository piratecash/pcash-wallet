package cash.p.terminal.modules.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IsMarkdownLinkTest {

    @Test
    fun isMarkdownLink_mdAndMarkdownSuffixCaseInsensitive_true() {
        assertTrue(isMarkdownLink("https://host/a/guide.md"))
        assertTrue(isMarkdownLink("https://host/a/GUIDE.MARKDOWN"))
    }

    @Test
    fun isMarkdownLink_schemeLessMarkdownHost_true() {
        assertTrue(isMarkdownLink("host/guide.md"))
    }

    @Test
    fun isMarkdownLink_webPageBlankOrQueryAfterSuffix_false() {
        assertFalse(isMarkdownLink("https://host/page.html"))
        assertFalse(isMarkdownLink(" "))
        assertFalse(isMarkdownLink("https://host/guide.md?x=1"))
    }

    @Test
    fun validUrlOrNull_withoutScheme_prependsHttps() {
        assertEquals("https://host/guide.md", validUrlOrNull("host/guide.md"))
        assertNull(validUrlOrNull(""))
    }
}
