package cash.p.terminal.core.managers

import android.net.Uri

/**
 * Detects whether a URI is a TON Connect deeplink in one of the supported formats:
 *  - `tc:...`              — Tonkeeper-compatible scheme
 *  - `pcash.money:...`     — legacy P.cash scheme
 *  - `pcash://ton-connect` — current P.cash scheme
 */
fun Uri.isTonConnectDeeplink(): Boolean {
    val raw = toString()
    return raw.startsWith("pcash.money:")
        || raw.startsWith("tc:")
        || (scheme == "pcash" && host == "ton-connect")
}
