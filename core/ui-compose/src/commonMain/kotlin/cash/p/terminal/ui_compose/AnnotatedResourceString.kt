package cash.p.terminal.ui_compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

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
