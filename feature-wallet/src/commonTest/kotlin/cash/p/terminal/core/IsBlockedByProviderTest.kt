package cash.p.terminal.core

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class IsBlockedByProviderTest {

    @Test
    fun isBlockedByProvider_http403_returnsTrue() {
        assertTrue(httpException(403).isBlockedByProvider())
    }

    @Test
    fun isBlockedByProvider_wrapped403_returnsTrue() {
        assertTrue(RuntimeException("sync failed", httpException(403)).isBlockedByProvider())
    }

    @Test
    fun isBlockedByProvider_http525_returnsFalse() {
        assertFalse(httpException(525).isBlockedByProvider())
    }

    @Test
    fun isBlockedByProvider_timeout_returnsFalse() {
        assertFalse(SocketTimeoutException().isBlockedByProvider())
    }

    private fun httpException(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody()))
}
