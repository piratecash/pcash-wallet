package cash.p.terminal.modules.nft

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.modules.balance.OpenSendTokenSelect
import cash.p.terminal.modules.balance.openSendTokenSelect
import java.net.URI

sealed interface NftLinkTarget {
    data class Web(val url: String) : NftLinkTarget
    data class Transfer(val openSend: OpenSendTokenSelect) : NftLinkTarget
    data object Unsupported : NftLinkTarget
}

private val WEB_SCHEMES = setOf("http", "https")
private const val TON_SCHEME = "ton"

/** The link is shown to the user before it opens, so a character that would not read as itself rules it out. */
fun nftLinkTarget(uri: String): NftLinkTarget {
    if (uri.codePoints().anyMatch { it.isInvisibleInLink() }) return NftLinkTarget.Unsupported
    val parsed = tryOrNull { URI(uri) } ?: return NftLinkTarget.Unsupported
    val scheme = parsed.scheme?.lowercase()
    return when {
        scheme in WEB_SCHEMES && !parsed.host.isNullOrBlank() -> NftLinkTarget.Web(uri)
        scheme == TON_SCHEME -> AddressUriParser.addressUri(uri)
            ?.let { NftLinkTarget.Transfer(it.openSendTokenSelect()) }
            ?: NftLinkTarget.Unsupported
        else -> NftLinkTarget.Unsupported
    }
}

// Code points, not chars: tag characters such as U+E0061 are FORMAT but arrive as two surrogate halves.
private fun Int.isInvisibleInLink(): Boolean {
    val type = Character.getType(this)
    return Character.isWhitespace(this) || Character.isSpaceChar(this) || Character.isISOControl(this) ||
        type == Character.FORMAT.toInt() || type == Character.SURROGATE.toInt()
}
