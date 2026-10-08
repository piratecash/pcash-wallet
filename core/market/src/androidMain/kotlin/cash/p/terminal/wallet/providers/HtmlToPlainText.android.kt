package cash.p.terminal.wallet.providers

import androidx.core.text.HtmlCompat

internal actual fun htmlToPlainText(html: String): String =
    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
