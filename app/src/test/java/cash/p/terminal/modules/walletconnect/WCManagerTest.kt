package cash.p.terminal.modules.walletconnect

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.walletconnect.handler.IWCHandler
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WCManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val manager = WCManager(
        accountManager = mockk(relaxed = true),
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    )

    @Before
    fun setUp() {
        mockkObject(WalletKit)
        // WalletKit methods may be @JvmStatic, which mockkObject does not intercept.
        mockkStatic(WalletKit::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getActionForRequest_handlerThrows_returnsNull() {
        val handler = mockk<IWCHandler> {
            every { chainNamespace } returns CHAIN_NAMESPACE
            every { getAction(any(), any(), any()) } throws IllegalStateException("kit unavailable")
        }
        manager.addWcHandler(handler)

        assertNull(manager.getActionForRequest(sessionRequest()))
    }

    @Test
    fun getRequestVerifyAlert_sdkThrows_returnsNull() = runTest(dispatcher) {
        every { WalletKit.getVerifyContext(REQUEST_ID) } throws IllegalStateException("not initialized")

        assertNull(manager.getRequestVerifyAlert(REQUEST_ID))
    }

    @Test
    fun getRequestVerifyAlert_noContext_returnsNull() = runTest(dispatcher) {
        every { WalletKit.getVerifyContext(REQUEST_ID) } returns null

        assertNull(manager.getRequestVerifyAlert(REQUEST_ID))
    }

    @Test
    fun getRequestVerifyAlert_invalidOrigin_returnsImpersonation() = runTest(dispatcher) {
        every { WalletKit.getVerifyContext(REQUEST_ID) } returns verifyContext(Wallet.Model.Validation.INVALID)

        assertEquals(WCVerifyAlert.Impersonation(ORIGIN), manager.getRequestVerifyAlert(REQUEST_ID))
    }

    @Test
    fun getRequestVerifyAlert_unknownOrigin_returnsNull() = runTest(dispatcher) {
        every { WalletKit.getVerifyContext(REQUEST_ID) } returns verifyContext(Wallet.Model.Validation.UNKNOWN)

        assertNull(manager.getRequestVerifyAlert(REQUEST_ID))
    }

    private fun verifyContext(validation: Wallet.Model.Validation) = Wallet.Model.VerifyContext(
        id = REQUEST_ID,
        origin = ORIGIN,
        validation = validation,
        verifyUrl = "",
        isScam = false,
    )

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
        const val REQUEST_ID = 7L
        const val ORIGIN = "https://evil.example"
    }
}
