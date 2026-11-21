package cash.p.terminal.modules.walletconnect

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.modules.walletconnect.handler.WCHandlerEvm
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class WCDelegateTest {

    private val responses = mutableListOf<Wallet.Params.SessionRequestResponse>()

    @Before
    fun setUp() {
        clearMocks(sessionManager)
        val evmBlockchainManager = mockk<EvmBlockchainManager>(relaxed = true) {
            every { getChain(any()) } returns Chain.Ethereum
        }
        val wcManager = WCManager(accountManager = mockk(relaxed = true)).apply {
            addWcHandler(WCHandlerEvm(evmBlockchainManager, mockk(relaxed = true)))
        }
        startKoin {
            modules(module {
                single { sessionManager }
                factory { WCWalletRequestHandler(evmBlockchainManager, wcManager) }
            })
        }
        mockkObject(WalletKit)
        every { WalletKit.respondSessionRequest(capture(responses), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkObject(WalletKit)
        WCDelegate.sessionRequestEvent = null
    }

    @Test
    fun onSessionRequest_ethSignFromNonActiveAccount_respondsMethodNotSupported() {
        val ethSign = request(id = 1, method = "eth_sign")
        every { sessionManager.getNewSessionRequest() } returns null

        deliver(ethSign)

        assertMethodNotSupported(ethSign)
        assertNull(WCDelegate.sessionRequestEvent)
    }

    @Test
    fun onSessionRequest_ethSignFromActiveAccount_respondsWithoutOpeningRequest() {
        val ethSign = request(id = 1, method = "eth_sign")
        every { sessionManager.getNewSessionRequest() } returns ethSign

        deliver(ethSign)

        assertMethodNotSupported(ethSign)
        assertNull(WCDelegate.sessionRequestEvent)
    }

    @Test
    fun onSessionRequest_personalSign_opensRequest() {
        val personalSign = request(id = 1, method = "personal_sign")
        every { sessionManager.getNewSessionRequest() } returns personalSign

        deliver(personalSign)

        assertTrue(responses.isEmpty())
        assertEquals(personalSign, WCDelegate.sessionRequestEvent)
    }

    @Test
    fun onSessionRequest_deliveredEthSignQueuedPersonalSign_rejectsEthSignAndOpensPersonalSign() {
        val ethSign = request(id = 1, method = "eth_sign")
        val personalSign = request(id = 2, method = "personal_sign")
        every { sessionManager.getNewSessionRequest() } returns personalSign

        deliver(ethSign)

        assertMethodNotSupported(ethSign)
        assertEquals(personalSign, WCDelegate.sessionRequestEvent)
    }

    @Test
    fun onSessionRequest_deliveredPersonalSignQueuedEthSign_rejectsQueuedWithoutOpening() {
        val personalSign = request(id = 1, method = "personal_sign")
        val ethSign = request(id = 2, method = "eth_sign")
        every { sessionManager.getNewSessionRequest() } returns ethSign

        deliver(personalSign)

        assertMethodNotSupported(ethSign)
        assertNull(WCDelegate.sessionRequestEvent)
    }

    @Test
    fun onSessionRequest_presentedRequestRedelivered_keepsPresentedRequest() {
        val sendTransaction = request(id = 1, method = "eth_sendTransaction")
        WCDelegate.sessionRequestEvent = sendTransaction
        every { sessionManager.getNewSessionRequest() } returns null

        deliver(sendTransaction)

        assertEquals(sendTransaction, WCDelegate.sessionRequestEvent)
    }

    @Test
    fun onSessionRequest_ethSignRejected_notifiesPendingRequestsChanged() = runBlocking {
        val ethSign = request(id = 1, method = "eth_sign")
        every { sessionManager.getNewSessionRequest() } returns ethSign
        every { WalletKit.respondSessionRequest(any(), any(), any()) } answers {
            secondArg<(Wallet.Params.SessionRequestResponse) -> Unit>().invoke(firstArg())
        }
        val notified = async(start = CoroutineStart.UNDISPATCHED) { WCDelegate.pendingRequestEvents.first() }

        deliver(ethSign)

        withTimeout(5_000) { notified.await() }
    }

    private fun deliver(sessionRequest: Wallet.Model.SessionRequest) =
        WCDelegate.onSessionRequest(sessionRequest, mockk(relaxed = true))

    private fun assertMethodNotSupported(sessionRequest: Wallet.Model.SessionRequest) {
        val response = responses.single()
        val error = response.jsonRpcResponse as Wallet.Model.JsonRpcResponse.JsonRpcError
        assertEquals(sessionRequest.topic, response.sessionTopic)
        assertEquals(sessionRequest.request.id, error.id)
        assertEquals(4200, error.code)
    }

    private fun request(id: Long, method: String) = Wallet.Model.SessionRequest(
        topic = "topic",
        chainId = "eip155:1",
        peerMetaData = null,
        request = Wallet.Model.SessionRequest.JSONRPCRequest(id = id, method = method, params = "[]"),
    )

    private companion object {
        // App.wcSessionManager caches the first instance it resolves, so every test shares this mock.
        val sessionManager = mockk<WCSessionManager>()
    }
}
