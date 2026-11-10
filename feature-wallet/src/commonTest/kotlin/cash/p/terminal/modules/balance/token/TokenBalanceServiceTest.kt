package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.BaseTronAdapter
import cash.p.terminal.core.managers.PendingBalanceCalculator
import cash.p.terminal.core.tor.TorSyncState
import cash.p.terminal.modules.balance.BalanceAdapterRepository
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.ReceiveAccountStatus
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceWarning
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

class TokenBalanceServiceTest {

    private val adaptersReady = PublishProcessor.create<Map<Wallet, IAdapter>>()
    private val initializationInProgress = MutableStateFlow(true)
    private val walletBalanceUpdated = MutableSharedFlow<Wallet>()
    private val wallet = mockk<Wallet>(relaxed = true) {
        every { token } returns mockk(relaxed = true) {
            every { blockchainType } returns BlockchainType.Tron
        }
    }
    private val tronAdapter = mockk<BaseTronAdapter>(relaxed = true) {
        every { receiveAddress } returns "tron-address"
    }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true) {
        every { adaptersReadyObservable } returns adaptersReady
        every { initializationInProgressFlow } returns initializationInProgress
        every { walletBalanceUpdatedFlow } returns walletBalanceUpdated
        every { getBalanceAdapterForWallet(any()) } returns null
        every { getAdjustedBalanceData(any()) } returns BalanceData(BigDecimal.ONE)
    }
    private val repositoryScope = CoroutineScope(Dispatchers.Unconfined)
    private val repository = BalanceAdapterRepository(
        adapterManager,
        mockk(relaxed = true),
        mockk<PendingBalanceCalculator>(relaxed = true) {
            every { pendingChangedFlow } returns MutableSharedFlow()
        },
        mockk<PlatformChainSupport>(relaxed = true) {
            every { isSupported(any<Wallet>()) } returns true
        },
        mockk<PlatformOperationSupport>(relaxed = true),
        mockk<TorSyncState>(relaxed = true) {
            every { syncErrorsHidden } returns MutableStateFlow(false)
        },
        TestDispatcherProvider(Dispatchers.Unconfined, repositoryScope),
    )
    private val xRateRepository = mockk<DefaultBalanceXRateRepository>(relaxed = true) {
        every { itemObservable } returns Observable.never()
        every { getLatestRates() } returns emptyMap()
    }
    private val service = TokenBalanceService(wallet, xRateRepository, repository)

    @After
    fun tearDown() {
        service.clear()
        repositoryScope.cancel()
    }

    // The sequence leaves no ready event at all: the adapter just exists by the time anything subscribes.
    @Test
    fun start_adapterAppearsBeforeWaitingStarts_warningAppearsWithoutReadyEvent() = runTest(timeout = TIMEOUT) {
        every { adapterManager.getAdapterForWalletOld(wallet) } returnsMany listOf(null, null, tronAdapter)
        coEvery { tronAdapter.accountStatus(any()) } returns ReceiveAccountStatus.ControlledByOthers

        service.start()

        assertEquals(BalanceWarning.TronControlledByOthersWarning, awaitWarning())
    }

    @Test
    fun start_adapterAppearsLater_warningAppearsOnReadyEvent() = runTest(timeout = TIMEOUT) {
        var adapter: IAdapter? = null
        every { adapterManager.getAdapterForWalletOld(wallet) } answers { adapter }
        coEvery { tronAdapter.accountStatus(any()) } returns ReceiveAccountStatus.NotActive

        service.start()
        settle()
        assertNull(service.balanceItem?.warning)

        adapter = tronAdapter
        adaptersReady.onNext(emptyMap())

        assertEquals(BalanceWarning.TronInactiveAccountWarning, awaitWarning())
    }

    @Test
    fun start_adapterAlreadyExists_requestsStatusOnce() = runTest(timeout = TIMEOUT) {
        every { adapterManager.getAdapterForWalletOld(wallet) } returns tronAdapter
        coEvery { tronAdapter.accountStatus(any()) } returns ReceiveAccountStatus.Active

        service.start()
        settle()

        coVerify(exactly = 1) { tronAdapter.accountStatus(any()) }
        assertNull(service.balanceItem?.warning)
    }

    @Test
    fun start_adapterAppearsBetweenCheckAndFirstRequest_keepsWarningWithoutRepeatRequest() =
        runTest(timeout = TIMEOUT) {
            every { adapterManager.getAdapterForWalletOld(wallet) } returnsMany listOf(null, tronAdapter)
            coEvery { tronAdapter.accountStatus(any()) } returns
                ReceiveAccountStatus.ControlledByOthers andThenThrows IllegalStateException("no network")

            service.start()
            settle()

            coVerify(exactly = 1) { tronAdapter.accountStatus(any()) }
            assertEquals(BalanceWarning.TronControlledByOthersWarning, service.balanceItem?.warning)
        }

    @Test
    fun start_lateRequestFails_leavesItemUntouched() = runTest(timeout = TIMEOUT) {
        every { adapterManager.getAdapterForWalletOld(wallet) } returnsMany listOf(null, null, tronAdapter)
        coEvery { tronAdapter.accountStatus(any()) } throws IllegalStateException("no network")

        service.start()
        val published = service.balanceItem
        settle()

        coVerify(exactly = 1) { tronAdapter.accountStatus(any()) }
        assertEquals(published, service.balanceItem)
        assertNull(service.balanceItem?.warning)
    }

    @Test
    fun start_balanceUpdatesAroundLateRequest_keepBothBalanceAndWarning() = runTest(timeout = TIMEOUT) {
        val status = CompletableDeferred<ReceiveAccountStatus>()
        every { adapterManager.getAdapterForWalletOld(wallet) } returnsMany listOf(null, null, tronAdapter)
        coEvery { tronAdapter.accountStatus(any()) } coAnswers { status.await() }
        service.start()

        driveBalanceUpdateUntilShown(BigDecimal.TEN)
        status.complete(ReceiveAccountStatus.ControlledByOthers)

        assertEquals(BalanceWarning.TronControlledByOthersWarning, awaitWarning())
        assertEquals(BigDecimal.TEN, service.balanceItem?.balanceData?.available)

        driveBalanceUpdateUntilShown(BigDecimal("20"))
        assertEquals(BalanceWarning.TronControlledByOthersWarning, service.balanceItem?.warning)
    }

    private suspend fun awaitWarning(): BalanceWarning? =
        service.balanceItemFlow.first { it?.warning != null }?.warning

    /** The service runs on real dispatchers and PublishSubject drops events before it subscribes, so repeat. */
    private suspend fun driveBalanceUpdateUntilShown(amount: BigDecimal) = withContext(Dispatchers.Default) {
        every { adapterManager.getAdjustedBalanceData(any()) } returns BalanceData(amount)
        repository.setWallet(listOf(wallet))
        while (service.balanceItem?.balanceData?.available != amount) {
            walletBalanceUpdated.emit(wallet)
            delay(10)
        }
    }

    /** Negative assertions need any wrongly launched work on the service's real dispatchers to have run. */
    private suspend fun settle() = withContext(Dispatchers.Default) { delay(300) }

    private companion object {
        val TIMEOUT = 20.seconds
    }
}
