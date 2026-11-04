package cash.p.terminal.core.ethereum

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.unknown_send_error
import io.horizontalsystems.core.Translator
import io.horizontalsystems.ethereumkit.api.core.RpcResponse
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EvmSendErrorMessageTest {

    @After
    fun tearDown() {
        unmockkObject(Translator)
    }

    @Test
    fun evmSendErrorMessage_rpcError_returnsNodeMessage() {
        val error = JsonRpc.ResponseError.RpcError(RpcResponse.Error(code = -32000, message = "nonce too low"))

        assertEquals("nonce too low", error.evmSendErrorMessage())
    }

    @Test
    fun evmSendErrorMessage_noMessage_returnsUnknownSendError() {
        mockkObject(Translator)
        every { Translator.getString(Res.string.unknown_send_error, *anyVararg()) } returns "Unknown send error"

        assertEquals("Unknown send error", IllegalStateException().evmSendErrorMessage())
    }
}
