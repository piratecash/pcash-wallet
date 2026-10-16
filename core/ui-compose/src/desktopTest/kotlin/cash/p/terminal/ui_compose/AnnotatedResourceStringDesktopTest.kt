package cash.p.terminal.ui_compose

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotatedResourceStringDesktopTest {

    @Test
    fun htmlToAnnotatedString_boldAndBreaks_keepsTextAndBoldRange() {
        val result = AnnotatedResourceString.htmlToAnnotatedString("<b>1. Title</b><br/>Body &amp; more<br/><br/>")

        assertEquals("1. Title\nBody & more", result.text)
        val bold = result.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0 to 8, bold.start to bold.end)
    }

    @Test
    fun htmlToAnnotatedString_link_addsUrlAnnotation() {
        val result = AnnotatedResourceString.htmlToAnnotatedString("Use the <a href=\"https://p.cash\">p.cash</a> app.")

        assertEquals("Use the p.cash app.", result.text)
        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals("https://p.cash", (link.item as LinkAnnotation.Url).url)
        assertEquals(8 to 14, link.start to link.end)
    }

    @Test
    fun htmlToAnnotatedString_preserveNewlinesAndCollapsedSpaces_matchesAndroidLegacyMode() {
        val result = AnnotatedResourceString.htmlToAnnotatedString("one\ntwo   three", preserveNewlines = true)

        assertEquals("one\ntwo three", result.text)
    }
}
