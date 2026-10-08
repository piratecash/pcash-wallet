package cash.p.terminal.wallet.providers

private val tagPattern = Regex("<[^>]*>")

private val entities = mapOf(
    "&nbsp;" to " ",
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&apos;" to "'",
)

internal actual fun htmlToPlainText(html: String): String =
    entities.entries.fold(tagPattern.replace(html, "")) { text, (entity, char) ->
        text.replace(entity, char)
    }
