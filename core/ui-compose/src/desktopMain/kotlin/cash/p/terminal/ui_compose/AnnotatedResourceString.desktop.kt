package cash.p.terminal.ui_compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val tokenRegex = Regex("<(/?)([a-zA-Z]+)([^>]*)>|[^<]+")
private val hrefRegex = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']")
private val whitespaceRegex = Regex("\\s+")
private val entities = mapOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")

// The subset the Android parser keeps (bold and links); whitespace collapses and the end is trimmed as there.
internal actual fun parseHtml(html: String, linkStyle: SpanStyle?): AnnotatedString {
    val parsed = buildAnnotatedString {
        val open = ArrayDeque<Pair<String, Int>>()
        tokenRegex.findAll(html).forEach { token ->
            val tag = token.groupValues[2].lowercase()
            when {
                tag.isEmpty() -> append(token.value.replace(whitespaceRegex, " ").decodeEntities())
                tag == "br" -> append('\n')
                tag == "p" -> if (length > 0 && !endsWithParagraph()) append("\n\n")
                token.groupValues[1] == "/" -> open.lastOrNull { it.first == tag }?.let { entry ->
                    open.remove(entry)
                    pop(entry.second)
                }
                tag == "b" || tag == "strong" -> open.addLast(tag to pushStyle(SpanStyle(fontWeight = FontWeight.Bold)))
                tag == "a" -> hrefRegex.find(token.groupValues[3])?.let { href ->
                    val url = LinkAnnotation.Url(href.groupValues[1], linkStyle?.let { TextLinkStyles(style = it) })
                    open.addLast(tag to pushLink(url))
                }
            }
        }
    }
    return parsed.subSequence(0, parsed.text.trimEnd().length)
}

private fun AnnotatedString.Builder.endsWithParagraph() = toAnnotatedString().text.endsWith("\n\n")

private fun String.decodeEntities(): String =
    entities.entries.fold(this) { text, (entity, char) -> text.replace(entity, char) }.replace("&amp;", "&")

@Composable
actual fun annotatedStringResource(resource: StringResource): AnnotatedString = AnnotatedString(stringResource(resource))
