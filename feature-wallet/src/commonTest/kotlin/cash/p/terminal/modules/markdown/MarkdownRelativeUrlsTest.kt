package cash.p.terminal.modules.markdown

import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstText
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownRelativeUrlsTest {

    private val baseUrl = "https://host/guides/token_guides/en/zcash.md"

    private val markdown = """
        ![](../images/a.png)

        # Title

        Zcash is [Bitcoin-like](bitcoin.md) and [external](https://other.org/x).

        ![inline](data:image/png;base64,AAAA)
    """.trimIndent()

    @Test
    fun withUrlsResolvedAgainst_relativeDestinations_resolvedAgainstMarkdownUrl() {
        val resolved = parse(markdown).withUrlsResolvedAgainst(baseUrl)

        assertEquals(
            listOf(
                "https://host/guides/token_guides/images/a.png",
                "data:image/png;base64,AAAA",
            ),
            resolved.collect { (it.type as? AstImage)?.destination },
        )
        assertEquals(
            listOf("https://host/guides/token_guides/en/bitcoin.md", "https://other.org/x"),
            resolved.collect { (it.type as? AstLink)?.destination },
        )
    }

    @Test
    fun withUrlsResolvedAgainst_anyTree_keepsTextAndStructure() {
        val original = parse(markdown)

        val resolved = original.withUrlsResolvedAgainst(baseUrl)

        assertEquals(original.collect { it.type::class }, resolved.collect { it.type::class })
        assertEquals(
            original.collect { (it.type as? AstText)?.literal },
            resolved.collect { (it.type as? AstText)?.literal },
        )
    }

    @Test
    fun withUrlsResolvedAgainst_invalidBaseUrl_returnsTreeUnchanged() {
        val original = parse(markdown)

        assertEquals(original, original.withUrlsResolvedAgainst("not a url"))
    }

    private fun parse(text: String): AstNode =
        CommonmarkAstNodeParser(CommonMarkdownParseOptions.Default).parse(text)

    /** Depth-first values of [select] over the tree, walking children forward and checking back-links. */
    private fun <T : Any> AstNode.collect(select: (AstNode) -> T?): List<T> = buildList {
        fun visit(node: AstNode) {
            select(node)?.let(::add)
            var child = node.links.firstChild
            var previous: AstNode? = null
            while (child != null) {
                check(child.links.parent === node && child.links.previous === previous)
                visit(child)
                previous = child
                child = child.links.next
            }
            check(node.links.lastChild === previous)
        }
        visit(this@collect)
    }
}
