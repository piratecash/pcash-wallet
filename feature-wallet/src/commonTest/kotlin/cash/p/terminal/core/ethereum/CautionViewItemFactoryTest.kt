package cash.p.terminal.core.ethereum

import cash.p.terminal.resources.EthereumTransaction_Error_ExecutionReverted
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.evm_node_request_failed
import cash.p.terminal.resources.source_blocked_by_provider_error
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.ethereumkit.api.core.RpcResponse
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class CautionViewItemFactoryTest {

    private val factory = CautionViewItemFactory(mockk(), mockk<IAppNumberFormatter>())

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } answers {
            val name = when (firstArg<Any>()) {
                Res.string.evm_node_request_failed -> "node"
                Res.string.source_blocked_by_provider_error -> "blocked"
                Res.string.EthereumTransaction_Error_ExecutionReverted -> "reverted"
                else -> "other"
            }
            "res:$name:" + (args[1] as Array<*>).joinToString()
        }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
    }

    @Test
    fun cautionViewItems_rpcInternalError_showsNodeFailureWithDetail() {
        val error = JsonRpc.ResponseError.RpcError(RpcResponse.Error(-32000, "Internal error"))

        assertEquals("res:node:Internal error", textOf(error))
    }

    @Test
    fun cautionViewItems_socketTimeout_showsNodeFailure() {
        assertEquals("res:node:timeout", textOf(SocketTimeoutException("timeout")))
    }

    @Test
    fun cautionViewItems_http403_staysBlockedByProvider() {
        val error = HttpException(Response.error<Any>(403, "".toResponseBody("text/plain".toMediaType())))

        assertEquals("res:blocked:", textOf(error))
    }

    @Test
    fun cautionViewItems_executionReverted_staysExecutionReverted() {
        val error = JsonRpc.ResponseError.RpcError(
            RpcResponse.Error(3, "execution reverted: nope")
        )

        assertEquals("res:reverted:execution reverted: nope", textOf(error))
    }

    private fun textOf(error: Throwable) =
        factory.cautionViewItems(emptyList(), listOf(error)).single().text
}
