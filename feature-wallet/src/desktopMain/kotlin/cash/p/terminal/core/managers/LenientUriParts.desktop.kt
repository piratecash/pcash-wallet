package cash.p.terminal.core.managers

internal actual fun lenientUriParts(url: String): UriParts {
    val scheme = schemeOf(url)
    val rest = if (scheme == null) url else url.substring(scheme.length + 1)
    val host = rest.takeIf { it.startsWith("//") }?.let { hostOf(it.substring(2)) }
    return UriParts(scheme, host)
}

// As android.net.Uri: a ':' after the first '/', '?' or '#' belongs to the path, not to a scheme.
private fun schemeOf(url: String): String? {
    val colon = url.indexOf(':')
    val delimiter = url.indexOfFirst { it == '/' || it == '?' || it == '#' }
    return if (colon >= 0 && (delimiter < 0 || colon < delimiter)) url.substring(0, colon) else null
}

private fun hostOf(authorityAndPath: String): String? {
    val authority = authorityAndPath.substringBefore('/').substringBefore('?').substringBefore('#')
    val hostAndPort = authority.substringAfterLast('@')
    val portSeparator = hostAndPort.lastIndexOf(':')
    val isPort = portSeparator >= 0 && hostAndPort.indexOf(']', portSeparator) < 0
    return (if (isPort) hostAndPort.substring(0, portSeparator) else hostAndPort).ifEmpty { null }
}
