package cash.p.terminal.network.yifi

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult
import cash.p.terminal.network.yifi.data.entity.BackendYiFiResponseError
import cash.p.terminal.network.yifi.data.entity.request.CreateSwapYiFiRequest
import cash.p.terminal.network.yifi.data.mapper.YiFiMapper
import cash.p.terminal.network.yifi.data.repository.YiFiRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class YiFiRepositoryTest {

    @Test
    fun getTransactionStatus_knownStatuses_mapToExpectedEnum() = runTest {
        val expected = mapOf(
            "pending" to TransactionStatusEnum.WAITING,
            "active" to TransactionStatusEnum.WAITING,
            "processing" to TransactionStatusEnum.EXCHANGING,
            "completed" to TransactionStatusEnum.FINISHED,
            "failed" to TransactionStatusEnum.FAILED,
            "cancelled" to TransactionStatusEnum.FAILED,
            "something_new" to TransactionStatusEnum.UNKNOWN,
        )

        expected.forEach { (status, expectedEnum) ->
            assertEquals("status=$status", expectedEnum, statusFor(status).status)
        }
    }

    @Test
    fun getTransactionStatus_completed_returnsAmountAndFinishedAt() = runTest {
        val result = statusFor("completed", completionTime = "2026-09-14T12:00:00.000Z")

        assertEquals(BigDecimal("0.30722634"), result.amountOutReal)
        assertEquals(1_789_387_200_000L, result.finishedAt)
    }

    @Test
    fun getTransactionStatus_completedWithBlankCompletionTime_finishedAtNull() = runTest {
        assertNull(statusFor("completed", completionTime = "").finishedAt)
    }

    @Test
    fun getTransactionStatus_notFinished_ignoresCompletionTime() = runTest {
        assertNull(statusFor("processing", completionTime = "2026-09-14T12:00:00.000Z").finishedAt)
    }

    @Test
    fun getTransactionStatus_errorBody_throwsBackendErrorWithCodeMessageAndStatus() = runTest {
        val repository = repository {
            respondJson(
                """{"success":false,"error":{"code":"RATE_EXPIRED","message":"Rate expired"}}""",
                HttpStatusCode.BadRequest,
            )
        }

        val error = assertFailsWith<BackendYiFiResponseError> {
            repository.getTransactionStatus(SwapProviderStatusRequest("tx-1", "addr"))
        }

        assertEquals("RATE_EXPIRED", error.code)
        assertEquals("Rate expired", error.message)
        assertEquals(400, error.statusCode)
    }

    @Test
    fun createSwap_malformedSuccessBody_throwsBackendErrorWithoutRawResponse() = runTest {
        val logs = mutableListOf<String>()
        val repository = YiFiRepository(
            yiFiApi(logs) {
                respondJson("""{"transactionId":"tx-1","accessToken":"SECRET_TOKEN","depositAddress":null}""")
            },
            YiFiMapper(),
        )

        val error = assertFailsWith<BackendYiFiResponseError> {
            repository.createSwap(
                CreateSwapYiFiRequest("ff", "BTC", "BTC", "ETH", "ETH", BigDecimal.ONE, "0xreceive")
            )
        }

        assertEquals(200, error.statusCode)
        assertFalse(error.stackTraceToString().contains("SECRET_TOKEN"))
        assertFalse(logs.any { it.contains("SECRET_TOKEN") })
    }

    private suspend fun statusFor(
        status: String,
        completionTime: String = "",
    ): SwapProviderTransactionStatusResult {
        val repository = repository {
            respondJson(
                """{"status":"$status","statusList":[],"receiveAmount":"0.30722634","completionTime":"$completionTime","txDepositHash":null}"""
            )
        }
        return repository.getTransactionStatus(SwapProviderStatusRequest("tx-1", "addr"))
    }

    private fun repository(
        respond: MockRequestHandleScope.() -> HttpResponseData,
    ) = YiFiRepository(yiFiApi { respond() }, YiFiMapper())
}
