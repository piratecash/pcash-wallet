package cash.p.terminal.ui_compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object AnnotatedResourceString {
    fun htmlToAnnotatedString(
        htmlString: String,
        preserveNewlines: Boolean = false,
        linkStyle: SpanStyle? = null,
    ): AnnotatedString {
        val processedHtml = if (preserveNewlines) htmlString.replace("\n", "<br>") else htmlString
        return parseHtml(processedHtml, linkStyle)
    }
}

internal expect fun parseHtml(html: String, linkStyle: SpanStyle?): AnnotatedString

/** Keeps the resource's `<b>` spans where the platform resources keep them (Compose resources drop markup). */
@Composable
expect fun annotatedStringResource(resource: StringResource): AnnotatedString

@Composable
fun annotatedHtmlStringResource(resource: StringResource, linkStyle: SpanStyle? = null): AnnotatedString =
    rememberHtmlAnnotatedString(stringResource(resource), linkStyle)

@Composable
internal fun rememberHtmlAnnotatedString(html: String, linkStyle: SpanStyle?): AnnotatedString =
    remember(html, linkStyle) { AnnotatedResourceString.htmlToAnnotatedString(html, linkStyle = linkStyle) }
