package cash.p.terminal.modules.walletconnect.request.signtransaction

import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.request.sendtransaction.WalletConnectTransaction
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class WCSignEthereumTransactionRequestViewModelTest {

    private val evmKitWrapper = mockk<EvmKitWrapper>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(WCDelegate)
        every { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) } returns Unit
        every { WCDelegate.rejectRequest(any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        WCDelegate.sessionRequestEvent = null
        unmockkObject(WCDelegate)
    }

    @Test
    fun reject_globalRequestReplacedByNewer_rejectsOwnRequest() {
        val viewModel = createViewModel()
        WCDelegate.sessionRequestEvent = sessionRequest(topic = NEWER_TOPIC, id = NEWER_ID)

        viewModel.reject()

        verify(exactly = 1) { WCDelegate.rejectRequest(OWN_TOPIC, OWN_ID, any(), any()) }
        verify(exactly = 0) { WCDelegate.rejectRequest(NEWER_TOPIC, any(), any(), any()) }
    }

    @Test
    fun reject_globalRequestCleared_rejectsOwnRequest() {
        val viewModel = createViewModel()
        WCDelegate.sessionRequestEvent = null

        viewModel.reject()

        verify(exactly = 1) { WCDelegate.rejectRequest(OWN_TOPIC, OWN_ID, any(), any()) }
    }

    @Test
    fun sign_globalRequestCleared_respondsToOwnRequest() = runTest {
        coEvery { evmKitWrapper.signReconciled(any()) } answers {
            firstArg<RawTransaction>() to
                Signature(27, ByteArray(32) { 1 }, ByteArray(32) { 2 })
        }
        every { evmKitWrapper.evmKit.chain.id } returns 56
        val viewModel = createViewModel()
        WCDelegate.sessionRequestEvent = null

        viewModel.sign()

        verify(exactly = 1) { WCDelegate.respondPendingRequest(OWN_ID, OWN_TOPIC, any(), any(), any()) }
    }

    private fun createViewModel() = WCSignEthereumTransactionRequestViewModel(
        evmKit = evmKitWrapper,
        baseCoinService = mockk(relaxed = true),
        sendEvmTransactionViewItemFactory = mockk(relaxed = true),
        dAppName = "dApp",
        transaction = transaction,
        topic = OWN_TOPIC,
        requestId = OWN_ID,
    )

    private fun sessionRequest(topic: String, id: Long) = Wallet.Model.SessionRequest(
        topic = topic,
        chainId = "eip155:56",
        peerMetaData = null,
        request = Wallet.Model.SessionRequest.JSONRPCRequest(id = id, method = "eth_signTransaction", params = "[]"),
    )

    private companion object {
        const val OWN_TOPIC = "topicA"
        const val OWN_ID = 1L
        const val NEWER_TOPIC = "topicB"
        const val NEWER_ID = 2L

        val transaction = WalletConnectTransaction(
            from = Address("0x9eed45bd6de84ef8117e41cc35b30e7cd9135d63"),
            to = Address("0x9eed45bd6de84ef8117e41cc35b30e7cd9135d63"),
            nonce = 0,
            gasPrice = 1_000_000_000,
            gasLimit = 21_000,
            maxPriorityFeePerGas = null,
            maxFeePerGas = null,
            value = BigInteger.ONE,
            data = ByteArray(0),
        )
    }
}
