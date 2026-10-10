package cash.p.terminal.modules.walletconnect.request

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException

// org.json.JSONArray needs a real implementation (not the JVM unit-test stub), hence Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class WCRequestEvmViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(WCDelegate)
        unmockkObject(EvmMessageSigning)
        WCDelegate.sessionRequestEvent = null
    }

    private fun buildSessionRequest(params: String) = Wallet.Model.SessionRequest(
        topic = "topic",
        chainId = "eip155:1",
        peerMetaData = null,
        request = Wallet.Model.SessionRequest.JSONRPCRequest(
            id = 1L,
            method = "personal_sign",
            params = params,
        ),
    )

    private fun viewModelWithParams(params: String): WCRequestEvmViewModel {
        WCDelegate.sessionRequestEvent = buildSessionRequest(params)

        val wcManager: WCManager = mockk(relaxed = true) {
            every { getBlockchainType(any()) } returns null
        }

        return WCRequestEvmViewModel(
            accountManager = mockk<IAccountManager>(relaxed = true),
            evmBlockchainManager = mockk<EvmBlockchainManager>(relaxed = true),
            wcManager = wcManager,
        )
    }

    /**
     * Builds a VM whose `allow()` can reach the signing step: a non-null resolved
     * [WCRequestEvmViewModel]'s evmKitWrapper/signer chain, wired via mocks. Requires
     * `mockkObject(WCDelegate)` to already be in effect (this stubs `sessionRequestEvent`).
     */
    private fun viewModelReadyToSign(signerMock: Signer): WCRequestEvmViewModel {
        every { WCDelegate.sessionRequestEvent } returns
            buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")

        val account: Account = mockk(relaxed = true)
        val accountManager: IAccountManager = mockk(relaxed = true)
        every { accountManager.activeAccount } returns account

        val evmKitWrapper: EvmKitWrapper = mockk(relaxed = true)
        every { evmKitWrapper.signer } returns signerMock

        val evmKitManager: EvmKitManager = mockk(relaxed = true)
        coEvery { evmKitManager.getEvmKitWrapper(any(), any()) } returns evmKitWrapper

        return viewModelWithKitManager(accountManager, evmKitManager)
    }

    private fun viewModelWithKitManager(
        accountManager: IAccountManager,
        evmKitManager: EvmKitManager,
    ): WCRequestEvmViewModel {
        val evmBlockchainManager: EvmBlockchainManager = mockk(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(any()) } returns evmKitManager

        val wcManager: WCManager = mockk(relaxed = true)
        every { wcManager.getBlockchainType(any()) } returns BlockchainType.Ethereum

        return WCRequestEvmViewModel(
            accountManager = accountManager,
            evmBlockchainManager = evmBlockchainManager,
            wcManager = wcManager,
        )
    }

    private fun failingKitManager(error: Throwable): EvmKitManager = mockk(relaxed = true) {
        coEvery { getEvmKitWrapper(any(), any()) } throws error
    }

    private fun suspendedKitManager(kit: CompletableDeferred<EvmKitWrapper>): EvmKitManager =
        mockk(relaxed = true) {
            coEvery { getEvmKitWrapper(any(), any()) } coAnswers { kit.await() }
        }

    private fun accountManagerWithActiveAccount(): IAccountManager = mockk(relaxed = true) {
        every { activeAccount } returns mockk<Account>(relaxed = true)
    }

    private fun WCRequestEvmViewModel.personalSignBytes(): ByteArray {
        val method = WCRequestEvmViewModel::class.java.getDeclaredMethod("personalSignBytes")
        method.isAccessible = true
        return try {
            method.invoke(this) as ByteArray
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    // A hung call never resumes and ignores cancellation, so blocking on it would hang the suite.
    private fun launchUnconfined(block: suspend () -> Unit) =
        CoroutineScope(Dispatchers.Unconfined).launch { block() }

    private fun WCRequestEvmViewModel.rejectCompletesWithoutSuspending(): Boolean =
        launchUnconfined { reject() }.isCompleted

    private val WCRequestEvmViewModel.status: RequestStatus?
        get() = (sessionRequestUi as? SessionRequestUI.Content)?.status

    private fun signingSucceeds() {
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
    }

    // Holds each response's success callback so the test decides when the relay answers.
    private fun deferRespondPendingRequest(): MutableList<() -> Unit> {
        val onSuccess = mutableListOf<() -> Unit>()
        every { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) } answers {
            onSuccess += arg<() -> Unit>(3)
        }
        return onSuccess
    }

    private fun deferRejectRequest(): MutableList<() -> Unit> {
        val onSuccess = mutableListOf<() -> Unit>()
        every { WCDelegate.rejectRequest(any(), any(), any(), any()) } answers {
            onSuccess += arg<() -> Unit>(2)
        }
        return onSuccess
    }

    @Test
    fun init_kitCreationFails_sessionRequestUiIsInitial() {
        WCDelegate.sessionRequestEvent = buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")

        val viewModel = viewModelWithKitManager(
            accountManagerWithActiveAccount(),
            failingKitManager(IllegalStateException("key locked")),
        )

        assertEquals(SessionRequestUI.Initial, viewModel.sessionRequestUi)
    }

    @Test
    fun init_kitCreationCancelled_isNotTreatedAsFailure() {
        WCDelegate.sessionRequestEvent = buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")

        val viewModel = viewModelWithKitManager(
            accountManagerWithActiveAccount(),
            failingKitManager(CancellationException("cancelled")),
        )

        assertEquals(SessionRequestUI.Loading, viewModel.sessionRequestUi)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun init_kitCreationSuspended_returnsInLoadingState() {
        WCDelegate.sessionRequestEvent = buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")

        val viewModel = viewModelWithKitManager(
            accountManagerWithActiveAccount(),
            suspendedKitManager(CompletableDeferred()),
        )

        assertEquals(SessionRequestUI.Loading, viewModel.sessionRequestUi)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun init_kitResolvedAsynchronously_sessionRequestUiIsContent() {
        WCDelegate.sessionRequestEvent = buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")
        val kit = CompletableDeferred<EvmKitWrapper>()
        val viewModel = viewModelWithKitManager(accountManagerWithActiveAccount(), suspendedKitManager(kit))

        kit.complete(mockk(relaxed = true))

        assertTrue(viewModel.sessionRequestUi is SessionRequestUI.Content)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun allow_beforeKitResolved_throwsNoSuitableEvmKitWithoutSigning() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        every { WCDelegate.sessionRequestEvent } returns
            buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")
        val viewModel = viewModelWithKitManager(
            accountManagerWithActiveAccount(),
            suspendedKitManager(CompletableDeferred()),
        )

        assertThrows(WCSessionManager.RequestDataError.NoSuitableEvmKit::class.java) {
            runBlocking { viewModel.allow() }
        }

        coVerify(exactly = 0) { EvmMessageSigning.signPersonalMessage(any(), any()) }
        verify(exactly = 0) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondError(any(), any(), any(), any(), any()) }
    }

    @Test
    fun personalSignBytes_hexPayload_decodesToOriginalBytesWithoutUtf8RoundTrip() {
        // 0xff00 is valid hex but NOT valid UTF-8 text: a String(...)->toByteArray() round trip
        // (the old behavior) would corrupt it via UTF-8 replacement characters.
        val viewModel = viewModelWithParams("""["0xff00", "0xAddress"]""")

        val bytes = viewModel.personalSignBytes()

        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00), bytes)
    }

    @Test
    fun personalSignBytes_nonHexPayload_fallsBackToRawUtf8Bytes() {
        val viewModel = viewModelWithParams("""["Not a hex string!", "0xAddress"]""")

        val bytes = viewModel.personalSignBytes()

        assertArrayEquals("Not a hex string!".toByteArray(), bytes)
    }

    @Test
    fun personalSignBytes_emptyParamsArray_throwsIllegalArgumentException() {
        val viewModel = viewModelWithParams("[]")

        assertThrows(IllegalArgumentException::class.java) {
            viewModel.personalSignBytes()
        }
    }

    @Test
    fun allow_signingThrows_respondsWithErrorAndPropagatesException() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)

        val signingError = TrezorSigningException("Trezor operation cancelled by user")
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } throws signingError
        every {
            WCDelegate.respondError(any(), any(), any(), captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val thrown = assertThrows(TrezorSigningException::class.java) {
            runBlocking { viewModel.allow() }
        }

        assertEquals(signingError, thrown)
        verify { WCDelegate.respondError(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    @Test
    fun allow_signingSucceeds_respondsWithPendingRequestResult() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)

        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every {
            WCDelegate.respondPendingRequest(any(), any(), any(), captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        runBlocking { viewModel.allow() }

        verify { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondError(any(), any(), any(), any(), any()) }
    }

    @Test
    fun allow_afterSuccessfulResponse_keepsContentAndDoesNotRespondAgain() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)

        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every {
            WCDelegate.respondPendingRequest(any(), any(), any(), captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        runBlocking {
            viewModel.allow()
            viewModel.allow()
        }

        // The closing screen must not recompose into the error state shown for Initial.
        assertTrue(viewModel.sessionRequestUi is SessionRequestUI.Content)
        verify(exactly = 1) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    @Test
    fun allow_signingFailsThenRespondErrorSucceeds_showsInitialAndRejectReturnsWithoutHanging() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        val signingError = TrezorSigningException("Trezor operation cancelled by user")
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } throws signingError
        every { WCDelegate.respondError(any(), any(), any(), any(), any()) } answers {
            arg<() -> Unit>(3).invoke()
        }
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val thrown = assertThrows(TrezorSigningException::class.java) {
            runBlocking { viewModel.allow() }
        }
        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()

        assertEquals(signingError, thrown)
        assertEquals(SessionRequestUI.Initial, viewModel.sessionRequestUi)
        assertTrue(rejectCompleted)
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun allow_signingFailsThenRespondErrorFails_showsInitialAndRejectReturnsWithoutHanging() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        val respondError = IllegalStateException("relay unavailable")
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } throws
            TrezorSigningException("Trezor operation cancelled by user")
        every { WCDelegate.respondError(any(), any(), any(), any(), any()) } answers {
            arg<(Throwable) -> Unit>(4).invoke(respondError)
        }
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { viewModel.allow() }
        }
        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()

        assertEquals(respondError, thrown)
        assertEquals(SessionRequestUI.Initial, viewModel.sessionRequestUi)
        assertTrue(rejectCompleted)
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun allow_respondPendingRequestFails_showsInitialAndRejectReturnsWithoutHanging() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        val respondError = IllegalStateException("relay unavailable")
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) } answers {
            arg<(Throwable) -> Unit>(4).invoke(respondError)
        }
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { viewModel.allow() }
        }
        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()

        assertEquals(respondError, thrown)
        assertEquals(SessionRequestUI.Initial, viewModel.sessionRequestUi)
        assertTrue(rejectCompleted)
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun reject_rejectRequestFails_showsInitialAndNextRejectReturnsWithoutHanging() {
        mockkObject(WCDelegate)
        val rejectError = IllegalStateException("relay unavailable")
        every { WCDelegate.rejectRequest(any(), any(), any(), any()) } answers {
            arg<(Throwable) -> Unit>(3).invoke(rejectError)
        }
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { viewModel.reject() }
        }
        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()

        assertEquals(rejectError, thrown)
        assertEquals(SessionRequestUI.Initial, viewModel.sessionRequestUi)
        assertTrue(rejectCompleted)
        verify(exactly = 1) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun reject_noPendingRequest_returnsImmediately() {
        mockkObject(WCDelegate)
        every { WCDelegate.sessionRequestEvent } returns
            buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")
        val viewModel = viewModelWithKitManager(
            accountManagerWithActiveAccount(),
            suspendedKitManager(CompletableDeferred()),
        )

        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()

        assertTrue(rejectCompleted)
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun allow_tappedTwiceBeforeResponseArrives_respondsOnceAndClosesOnce() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        signingSucceeds()
        val respondSuccess = deferRespondPendingRequest()
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val first = launchUnconfined { viewModel.allow() }
        val second = launchUnconfined { viewModel.allow() }
        val statusWhileResponding = viewModel.status
        respondSuccess.single().invoke()

        assertEquals(RequestStatus.Responding, statusWhileResponding)
        assertTrue(first.isCompleted && second.isCompleted)
        assertEquals(RequestStatus.Responded, viewModel.status)
        coVerify(exactly = 1) { EvmMessageSigning.signPersonalMessage(any(), any()) }
        verify(exactly = 1) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    @Test
    fun reject_duringSigning_isIgnoredAndOnlyAllowResponseIsSent() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        val signature = CompletableDeferred<ByteArray>()
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } coAnswers { signature.await() }
        val respondSuccess = deferRespondPendingRequest()
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        launchUnconfined { viewModel.allow() }
        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()
        signature.complete(byteArrayOf(0x01, 0x02, 0x03))
        respondSuccess.single().invoke()

        assertTrue(rejectCompleted)
        assertEquals(RequestStatus.Responded, viewModel.status)
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
        verify(exactly = 1) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    @Test
    fun reject_tappedTwiceBeforeResponseArrives_rejectsOnceAndClosesOnce() {
        mockkObject(WCDelegate)
        val rejectSuccess = deferRejectRequest()
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val first = launchUnconfined { viewModel.reject() }
        val second = launchUnconfined { viewModel.reject() }
        rejectSuccess.single().invoke()

        assertTrue(first.isCompleted && second.isCompleted)
        assertEquals(RequestStatus.Responded, viewModel.status)
        verify(exactly = 1) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
    }

    @Test
    fun allow_duringReject_isIgnored() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        val rejectSuccess = deferRejectRequest()
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        launchUnconfined { viewModel.reject() }
        val allow = launchUnconfined { viewModel.allow() }
        rejectSuccess.single().invoke()

        assertTrue(allow.isCompleted)
        assertEquals(RequestStatus.Responded, viewModel.status)
        coVerify(exactly = 0) { EvmMessageSigning.signPersonalMessage(any(), any()) }
        verify(exactly = 0) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    @Test
    fun allowAndReject_whileLoading_sendNothingAndStayLoading() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        every { WCDelegate.sessionRequestEvent } returns
            buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")
        val viewModel = viewModelWithKitManager(
            accountManagerWithActiveAccount(),
            suspendedKitManager(CompletableDeferred()),
        )

        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()
        assertThrows(WCSessionManager.RequestDataError.NoSuitableEvmKit::class.java) {
            runBlocking { viewModel.allow() }
        }

        assertTrue(rejectCompleted)
        assertEquals(SessionRequestUI.Loading, viewModel.sessionRequestUi)
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondError(any(), any(), any(), any(), any()) }
    }

    @Test
    fun allow_responseDelivered_closesWithoutPassingThroughErrorState() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        signingSucceeds()
        val respondSuccess = deferRespondPendingRequest()
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        launchUnconfined { viewModel.allow() }
        val beforeDelivery = viewModel.sessionRequestUi
        respondSuccess.single().invoke()

        assertEquals(RequestStatus.Responding, (beforeDelivery as SessionRequestUI.Content).status)
        assertEquals(RequestStatus.Responded, viewModel.status)
    }

    @Test
    fun allow_signingFails_respondsErrorOnceAndIgnoresFurtherActions() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } throws
            TrezorSigningException("Trezor operation cancelled by user")
        every { WCDelegate.respondError(any(), any(), any(), any(), any()) } answers {
            arg<() -> Unit>(3).invoke()
        }
        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { viewModel.allow() }
        }
        runBlocking { viewModel.allow() }
        val rejectCompleted = viewModel.rejectCompletesWithoutSuspending()

        assertTrue(rejectCompleted)
        assertEquals(SessionRequestUI.Initial, viewModel.sessionRequestUi)
        coVerify(exactly = 1) { EvmMessageSigning.signPersonalMessage(any(), any()) }
        verify(exactly = 1) { WCDelegate.respondError(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.rejectRequest(any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 10_000L
    }
}
