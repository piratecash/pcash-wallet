package cash.p.terminal.core.managers

import android.net.Uri

internal actual fun lenientUriParts(url: String): UriParts =
    Uri.parse(url).let { UriParts(it.scheme, it.host) }
