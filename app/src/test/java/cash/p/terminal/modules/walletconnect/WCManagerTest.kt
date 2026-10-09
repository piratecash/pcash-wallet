package cash.p.terminal.modules.walletconnect

import cash.p.terminal.modules.walletconnect.handler.IWCHandler
import com.reown.walletkit.client.Wallet
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Test

class WCManagerTest {

    @Test
    fun getActionForRequest_handlerThrows_returnsNull() {
        val handler = mockk<IWCHandler> {
            every { chainNamespace } returns CHAIN_NAMESPACE
            every { getAction(any(), any(), any()) } throws IllegalStateException("kit unavailable")
        }
        val manager = WCManager(accountManager = mockk(relaxed = true)).apply { addWcHandler(handler) }

        assertNull(manager.getActionForRequest(sessionRequest()))
    }

    private fun sessionRequest() = Wallet.Model.SessionRequest(
        topic = "topic",
        chainId = "$CHAIN_NAMESPACE:pubnet",
        peerMetaData = null,
        request = Wallet.Model.SessionRequest.JSONRPCRequest(
            id = 1L,
            method = "stellar_signXDR",
            params = "{}",
        ),
    )

    private companion object {
        const val CHAIN_NAMESPACE = "stellar"
    }
}
