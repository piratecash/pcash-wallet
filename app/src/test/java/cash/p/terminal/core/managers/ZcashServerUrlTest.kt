package cash.p.terminal.core.managers

import cash.p.terminal.core.managers.ZcashServerUrl.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ZcashServerUrlTest {

    private fun assertValid(expected: String, raw: String) =
        assertEquals(ParseResult.Valid(expected), ZcashServerUrl.parse(raw))

    private fun assertInvalid(raw: String) = assertEquals(ParseResult.Invalid, ZcashServerUrl.parse(raw))

    @Test
    fun parse_httpsHost_isValidWithExplicitPort() = assertValid("https://zec.rocks:443", "https://zec.rocks")

    @Test
    fun parse_httpsHostWithPort_keepsPort() = assertValid("https://host.io:9067", "https://host.io:9067")

    @Test
    fun parse_onionHttp_isValidWithPort80() = assertValid("http://abc.onion:80", "http://abc.onion")

    @Test
    fun parse_caseTrailingSlashAndImplicitPort_normaliseToSameString() {
        val expected = "https://host.io:443"
        assertValid(expected, "https://Host.io/")
        assertValid(expected, "HTTPS://HOST.IO:443")
        assertValid(expected, "  https://host.io  ")
    }

    @Test
    fun parse_httpNonOnion_isInvalid() = assertInvalid("http://host.io")

    @Test
    fun parse_userInfo_isInvalid() = assertInvalid("https://user:pw@host.io")

    @Test
    fun parse_query_isInvalid() = assertInvalid("https://host.io?x=1")

    @Test
    fun parse_fragment_isInvalid() = assertInvalid("https://host.io#x")

    @Test
    fun parse_path_isInvalid() = assertInvalid("https://host.io/lwd")

    @Test
    fun parse_blankHost_isInvalid() = assertInvalid("https:/")

    @Test
    fun parse_otherScheme_isInvalid() = assertInvalid("ftp://host.io")

    @Test
    fun parse_garbage_isInvalid() {
        assertInvalid("not a url")
        assertInvalid("")
        assertInvalid("https://host.io:99999")
    }
}
