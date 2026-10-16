package cash.p.terminal.core.managers

import cash.p.terminal.core.tryOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientUriPartsDesktopTest {

    @Test
    fun lenientUriParts_urlTable_matchesAndroidUriWithoutThrowing() {
        val mismatches = CASES.mapNotNull { (url, expected) ->
            val actual = tryOrNull { lenientUriParts(url) }
            "$url -> $actual".takeIf { actual != expected }
        }

        assertEquals(emptyList<String>(), mismatches)
    }

    private companion object {
        val CASES = listOf(
            "https://rpc.example.com/v1?key=1" to UriParts("https", "rpc.example.com"),
            "wss://ws.example.com" to UriParts("wss", "ws.example.com"),
            "https://my_node.example.com" to UriParts("https", "my_node.example.com"),
            "https://user:secret@rpc.example.com:8545/path" to UriParts("https", "rpc.example.com"),
            "ftp://bad host" to UriParts("ftp", "bad host"),
            "rpc.example.com/path:8545" to UriParts(null, null),
            "mailto:someone@example.com" to UriParts("mailto", null),
        )
    }
}
