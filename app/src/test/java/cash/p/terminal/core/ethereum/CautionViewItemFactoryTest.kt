package cash.p.terminal.core.ethereum

import cash.p.terminal.R
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.evm_node_request_failed
import cash.p.terminal.strings.helpers.Translator as ResourceTranslator
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
        mockkObject(Translator, ResourceTranslator)
        every { ResourceTranslator.getString(any<Int>()) } answers { "legacy:${firstArg<Int>()}" }
        every { ResourceTranslator.getString(any<Int>(), *anyVararg()) } answers {
            "legacy:${firstArg<Int>()}:${(args[1] as Array<*>).joinToString()}"
        }
        every { Translator.getString(any(), *anyVararg()) } answers {
            val id = firstArg<Any>()
            "res:${if (id === Res.string.evm_node_request_failed) "node" else "other"}:" +
                (args[1] as Array<*>).joinToString()
        }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator, ResourceTranslator)
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

        assertEquals("legacy:${R.string.source_blocked_by_provider_error}", textOf(error))
    }

    @Test
    fun cautionViewItems_executionReverted_staysExecutionReverted() {
        val error = JsonRpc.ResponseError.RpcError(
            RpcResponse.Error(3, "execution reverted: nope")
        )

        assertEquals(
            "legacy:${R.string.EthereumTransaction_Error_ExecutionReverted}:execution reverted: nope",
            textOf(error)
        )
    }

    private fun textOf(error: Throwable) =
        factory.cautionViewItems(emptyList(), listOf(error)).single().text
}
