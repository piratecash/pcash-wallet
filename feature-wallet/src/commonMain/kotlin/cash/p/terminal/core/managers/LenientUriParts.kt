package cash.p.terminal.core.managers

internal data class UriParts(val scheme: String?, val host: String?)

/** Never throws, unlike `java.net.URI`: a malformed stored URL must not hide the valid ones. */
internal expect fun lenientUriParts(url: String): UriParts
