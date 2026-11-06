package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.ReceiveAccountStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.AccountStatus
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.transaction.Signer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapTronAccountWarningServiceTest {

    private val dispatcher = StandardTestDispatcher()
    private val adapter = mockk<IReceiveAdapter> {
        every { receiveAddress } returns "tron-address"
    }
    private val account = mockk<Account>()
    private val adapterManager = mockk<IAdapterManager> {
        every { getAdapterForToken<IReceiveAdapter>(any()) } returns null
    }
    private val accountManager = mockk<IAccountManager> {
        every { activeAccount } returns account
    }
    private val tronKitManager = mockk<TronKitManager>(relaxUnitFun = true)
    private val tronKit = mockk<TronKit> {
        every { address } returns mockk<Address>()
    }

    private val usdt = token(BlockchainType.Tron, TokenType.Eip20("usdt"))
    private val trx = token(BlockchainType.Tron, TokenType.Native)
    private val eth = token(BlockchainType.Ethereum, TokenType.Native)

    @Test
    fun setTarget_nonTronToken_makesNoRequest() = runTest(dispatcher) {
        val service = startedService()

        service.setTarget(eth, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
        coVerify(exactly = 0) { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) }
    }

    @Test
    fun setTarget_tronWithAdapter_controlledByOthers_publishesFlag() = runTest(dispatcher) {
        useAdapter(ReceiveAccountStatus.ControlledByOthers)
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertTrue(service.stateFlow.value)
        coVerify(exactly = 0) { tronKitManager.getTronKitWrapper(any()) }
    }

    @Test
    fun setTarget_tronWithAdapter_notActive_noFlag() = runTest(dispatcher) {
        useAdapter(ReceiveAccountStatus.NotActive)
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
    }

    @Test
    fun setTarget_noAdapter_probesThroughKitAndUnlinks() = runTest(dispatcher) {
        useKit(signer = mockk<Signer>())
        coEvery { tronKit.accountStatus(any()) } returns AccountStatus.ControlledByOthers
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertTrue(service.stateFlow.value)
        coVerify(exactly = 1) { tronKitManager.unlink(account) }
    }

    @Test
    fun setTarget_noAdapterWatchAccount_noFlag() = runTest(dispatcher) {
        useKit(signer = null)
        coEvery { tronKit.accountStatus(any()) } returns AccountStatus.ControlledByOthers
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
        coVerify(exactly = 1) { tronKitManager.unlink(account) }
    }

    @Test
    fun setTarget_customRecipient_makesNoRequestAndClearsFlag() = runTest(dispatcher) {
        useAdapter(ReceiveAccountStatus.ControlledByOthers)
        val service = startedService()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()
        assertTrue(service.stateFlow.value)

        service.setTarget(usdt, customRecipient = true)
        runCurrent()

        assertFalse(service.stateFlow.value)
        coVerify(exactly = 1) { adapter.accountStatus(any()) }
    }

    @Test
    fun setTarget_usdtThenTrx_oneRequestAndFlagKept() = runTest(dispatcher) {
        useAdapter(ReceiveAccountStatus.ControlledByOthers)
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()
        service.setTarget(trx, customRecipient = false)
        runCurrent()

        assertTrue(service.stateFlow.value)
        coVerify(exactly = 1) { adapter.accountStatus(any()) }
    }

    @Test
    fun setTarget_networkFailure_noFlag() = runTest(dispatcher) {
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        coEvery { adapter.accountStatus(any()) } throws IllegalStateException("no network")
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
    }

    @Test
    fun setTarget_requestFailed_nextCallRetries() = runTest(dispatcher) {
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        coEvery { adapter.accountStatus(any()) } throws
            IllegalStateException("no network") andThen ReceiveAccountStatus.ControlledByOthers
        val service = startedService()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()
        assertFalse(service.stateFlow.value)

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertTrue(service.stateFlow.value)
        coVerify(exactly = 2) { adapter.accountStatus(any()) }
    }

    @Test
    fun setTarget_successfulRequest_nextCallsDoNotRepeatIt() = runTest(dispatcher) {
        useAdapter(ReceiveAccountStatus.Active)
        val service = startedService()

        repeat(3) {
            service.setTarget(usdt, customRecipient = false)
            runCurrent()
        }

        coVerify(exactly = 1) { adapter.accountStatus(any()) }
    }

    @Test
    fun setTarget_cancelledRequestFails_doesNotClearNewerRequest() = runTest(dispatcher) {
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        lateinit var service: SwapTronAccountWarningService
        var calls = 0
        coEvery { adapter.accountStatus(any()) } answers {
            if (++calls == 1) {
                service.setTarget(eth, customRecipient = false)
                service.setTarget(usdt, customRecipient = false)
                throw IllegalStateException("no network")
            }
            ReceiveAccountStatus.Active
        }
        service = startedService()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertEquals(2, calls)
    }

    @Test
    fun setTarget_unsupportedAccount_noFlagAndNoRetry() = runTest(dispatcher) {
        coEvery { tronKitManager.getTronKitWrapper(account) } throws UnsupportedAccountException()
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
        coVerify(exactly = 1) { tronKitManager.getTronKitWrapper(account) }
    }

    @Test
    fun setTarget_noActiveAccount_noFlag() = runTest(dispatcher) {
        every { accountManager.activeAccount } returns null
        val service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
    }

    @Test
    fun setTarget_leavesTronDuringRequest_doesNotPublishStaleResult() = runTest(dispatcher) {
        val status = CompletableDeferred<ReceiveAccountStatus>()
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        coEvery { adapter.accountStatus(any()) } coAnswers { status.await() }
        val service = startedService()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        service.setTarget(eth, customRecipient = false)
        status.complete(ReceiveAccountStatus.ControlledByOthers)
        runCurrent()

        assertFalse(service.stateFlow.value)
    }

    @Test
    fun setTarget_leavesTronWhileRequestIgnoresCancellation_doesNotPublishStaleResult() = runTest(dispatcher) {
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        lateinit var service: SwapTronAccountWarningService
        // Blocking network call: the answer arrives although the job was cancelled meanwhile.
        coEvery { adapter.accountStatus(any()) } answers {
            service.setTarget(eth, customRecipient = false)
            ReceiveAccountStatus.ControlledByOthers
        }
        service = startedService()

        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        assertFalse(service.stateFlow.value)
    }

    @Test
    fun setTarget_returnsToTronAfterStaleRequest_publishesOnlyNewResult() = runTest(dispatcher) {
        val first = CompletableDeferred<ReceiveAccountStatus>()
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        coEvery { adapter.accountStatus(any()) } coAnswers { first.await() } andThen ReceiveAccountStatus.Active
        val service = startedService()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        service.setTarget(eth, customRecipient = false)
        service.setTarget(usdt, customRecipient = false)
        first.complete(ReceiveAccountStatus.ControlledByOthers)
        runCurrent()

        assertFalse(service.stateFlow.value)
        coVerify(exactly = 2) { adapter.accountStatus(any()) }
    }

    @Test
    fun setTarget_cancelledDuringKitRequest_stillUnlinks() = runTest(dispatcher) {
        useKit(signer = mockk<Signer>())
        coEvery { tronKit.accountStatus(any()) } coAnswers { CompletableDeferred<AccountStatus>().await() }
        var unlinked = false
        // The real unlink takes a Mutex, which throws in a cancelled context.
        coEvery { tronKitManager.unlink(account) } coAnswers {
            currentCoroutineContext().ensureActive()
            unlinked = true
        }
        val service = startedService()
        service.setTarget(usdt, customRecipient = false)
        runCurrent()

        service.setTarget(eth, customRecipient = false)
        runCurrent()

        assertTrue(unlinked)
    }

    private fun TestScope.startedService() = SwapTronAccountWarningService(
        adapterManager,
        accountManager,
        tronKitManager,
        // A distinct io dispatcher keeps the real Main<->IO cancellation boundary.
        TestDispatcherProvider(dispatcher, backgroundScope, io = StandardTestDispatcher(testScheduler)),
    ).also { it.start(backgroundScope) }

    private fun useAdapter(status: ReceiveAccountStatus) {
        every { adapterManager.getAdapterForToken<IReceiveAdapter>(any()) } returns adapter
        coEvery { adapter.accountStatus(any()) } returns status
    }

    private fun useKit(signer: Signer?) {
        coEvery { tronKitManager.getTronKitWrapper(account) } returns TronKitWrapper(tronKit, signer)
    }

    private fun token(type: BlockchainType, tokenType: TokenType) = Token(
        coin = Coin(uid = "coin-$tokenType", name = "Coin", code = "CN", marketCapRank = null, image = null),
        blockchain = Blockchain(type, type.uid, null),
        type = tokenType,
        decimals = 6,
    )
}
