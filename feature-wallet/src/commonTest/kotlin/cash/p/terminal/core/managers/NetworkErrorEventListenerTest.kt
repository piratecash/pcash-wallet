package cash.p.terminal.core.managers

import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

class NetworkErrorEventListenerTest {

    private val blockchainType = BlockchainType.Ethereum
    private val accountId = "account-1"

    @Test
    fun responseHeadersEnd_402_recordsHttpStatusError() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val listener = NetworkErrorEventListener(blockchainType, accountId, tracker, recordHttpErrors = true)
        val response = response(code = 402)
        val call = call(response.request)

        listener.responseHeadersEnd(call, response)

        verify(exactly = 1) {
            tracker.record(
                blockchainType,
                accountId,
                match { it.throwable.let { t -> t is HttpStatusException && t.code == 402 } }
            )
        }
    }

    @Test
    fun responseHeadersEnd_200_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val listener = NetworkErrorEventListener(blockchainType, accountId, tracker, recordHttpErrors = true)
        val response = response(code = 200)
        val call = call(response.request)

        listener.responseHeadersEnd(call, response)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun responseHeadersEnd_402_recordingDisabled_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val listener = NetworkErrorEventListener(blockchainType, accountId, tracker, recordHttpErrors = false)
        val response = response(code = 402)
        val call = call(response.request)

        listener.responseHeadersEnd(call, response)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun responseHeadersEnd_creditsHeader_storesValue() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val listener = NetworkErrorEventListener(blockchainType, accountId, tracker, recordHttpErrors = true)
        val response = response(code = 200, headers = mapOf("x-credits-remaining" to "-42"))
        val call = call(response.request)

        listener.responseHeadersEnd(call, response)

        verify(exactly = 1) { tracker.recordExplorerCredits(blockchainType, accountId, "-42") }
    }

    private fun call(request: Request): Call {
        val call = mockk<Call>()
        every { call.request() } returns request
        every { call.isCanceled() } returns false
        return call
    }

    private fun response(code: Int, headers: Map<String, String> = emptyMap()): Response {
        val request = Request.Builder().url("https://api.blockscout.com/api").build()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return builder.build()
    }
}
