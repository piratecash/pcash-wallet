package cash.p.terminal.network.backendswap

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.yifi.respondJson
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class BackendSwapRepositoryTest {

    @Test
    fun getTransactionStatus_knownStatuses_mapToExpectedEnum() = runTest {
        val expected = mapOf(
            "waiting" to TransactionStatusEnum.WAITING,
            "exchanging" to TransactionStatusEnum.EXCHANGING,
            "hold" to TransactionStatusEnum.VERIFYING,
            "finished" to TransactionStatusEnum.FINISHED,
            "failed" to TransactionStatusEnum.FAILED,
            "refunded" to TransactionStatusEnum.REFUNDED,
            "expired" to TransactionStatusEnum.FAILED,
            "something_new" to TransactionStatusEnum.UNKNOWN,
        )

        expected.forEach { (status, expectedEnum) ->
            val repository = backendSwapRepository { respondJson("""{"id":"order-1","status":"$status"}""") }
            val result = repository.getTransactionStatus(
                SwapProviderStatusRequest("order-1", "addr", walletAddress = "0xwallet")
            )
            assertEquals("status=$status", expectedEnum, result?.status)
        }
    }

    @Test
    fun getTransactionStatus_nullWalletAddress_returnsNullWithoutRequest() = runTest {
        var called = false
        val repository = backendSwapRepository {
            called = true
            respondJson("""{"id":"order-1","status":"waiting"}""")
        }

        val result = repository.getTransactionStatus(
            SwapProviderStatusRequest("order-1", "addr", walletAddress = null)
        )

        assertNull(result)
        assertEquals(false, called)
    }

    @Test
    fun getTransactionStatus_finishedWithAmountToActual_returnsAmountOutReal() = runTest {
        val result = statusFor(""","amountToActual":"0.01234"""")

        assertEquals(0, BigDecimal("0.01234").compareTo(result?.amountOutReal))
        assertNull(result?.finishedAt)
    }

    @Test
    fun getTransactionStatus_amountToActualAbsentNullOrNotNumeric_returnsNullAmountOutReal() = runTest {
        listOf("", ""","amountToActual":null""", ""","amountToActual":"n/a"""").forEach { amountField ->
            assertNull("amountField=$amountField", statusFor(amountField)?.amountOutReal)
        }
    }

    @Test
    fun getTransactionStatus_requestsSwapByIdPath() = runTest {
        lateinit var seen: HttpRequestData
        val repository = backendSwapRepository { request ->
            seen = request
            respondJson("""{"id":"order-1","status":"waiting"}""")
        }

        repository.getTransactionStatus(SwapProviderStatusRequest("order-1", "addr", walletAddress = "0xwallet"))

        assertEquals("https://p.cash/v2/exchanges/order-1", seen.url.toString())
    }

    private suspend fun statusFor(amountField: String) =
        backendSwapRepository { respondJson("""{"id":"order-1","status":"finished"$amountField}""") }
            .getTransactionStatus(SwapProviderStatusRequest("order-1", "addr", walletAddress = "0xwallet"))
}
