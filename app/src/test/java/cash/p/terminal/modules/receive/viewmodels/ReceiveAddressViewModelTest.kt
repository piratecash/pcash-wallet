package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.reactivex.processors.PublishProcessor
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveAddressViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val testDispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    private val viewModelStore = ViewModelStore()

    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val adapter = mockk<IReceiveAdapter>(relaxed = true)
    private val initializationInProgress = MutableStateFlow(false)
    private val adaptersReady = PublishProcessor.create<Map<Wallet, IAdapter>>()

    private val account = mockk<Account> {
        every { isWatchAccount } returns false
    }
    private val coin = mockk<Coin> {
        every { code } returns "ZEC"
    }
    private val blockchain = mockk<Blockchain> {
        every { name } returns "Zcash"
    }
    private val token = mockk<Token>(relaxed = true) {
        every { this@mockk.blockchain } returns this@ReceiveAddressViewModelTest.blockchain
        every { blockchainType } returns BlockchainType.Zcash
        every { type } returns TokenType.Native
    }
    private val wallet = mockk<Wallet>(relaxed = true) {
        every { this@mockk.account } returns this@ReceiveAddressViewModelTest.account
        every { this@mockk.coin } returns this@ReceiveAddressViewModelTest.coin
        every { this@mockk.token } returns this@ReceiveAddressViewModelTest.token
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { adapterManager.adaptersReadyObservable } returns adaptersReady
        every { adapterManager.initializationInProgressFlow } returns initializationInProgress
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns null
        coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns null
        every { adapter.receiveAddress } returns ADAPTER_ADDRESS
        every { adapter.isMainNet } returns true
        every { adapter.isAddressHistorySupported } returns false
        every { adapter.usedAddresses(any()) } returns emptyList()
        coEvery { adapter.isAddressActive(any()) } returns true
        // The default IReceiveAdapter behaviour every non-Zcash adapter keeps.
        every { adapter.freshReceiveAddressChanges } returns flowOf(Unit)
        coEvery { adapter.freshReceiveAddress() } returns ADAPTER_ADDRESS
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReceiveAddressViewModel {
        val viewModel = ReceiveAddressViewModel(
            wallet = wallet,
            adapterManager = adapterManager,
            dispatcherProvider = testDispatcherProvider,
        )
        viewModelStore.put("receive-address", viewModel)
        return viewModel
    }

    @Test
    fun setData_adapterMissingWhileInitializing_showsLoading() = runTest(dispatcher) {
        initializationInProgress.value = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
    }

    @Test
    fun setData_adapterMissingAfterInitFinished_showsError() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.viewState is ViewState.Error)
    }

    @Test
    fun setData_adapterMissingButFallbackAddressAvailable_showsSuccessWithAddress() =
        runTest(dispatcher) {
            coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns FALLBACK_ADDRESS

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ViewState.Success, viewModel.uiState.viewState)
            assertEquals(FALLBACK_ADDRESS, viewModel.uiState.address)
        }

    @Test
    fun setData_isAddressActiveThrows_keepsSuccessAndAssumesActive() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } throws IllegalStateException("no network")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
        assertEquals(false, viewModel.uiState.showTronAlert)
    }

    @Test
    fun setData_isAddressActiveReturnsFalse_keepsSuccessAndShowsNotActive() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(true, viewModel.uiState.showTronAlert)
    }

    @Test
    fun initializationFinished_adapterAppears_replacesFallbackWithAdapterAddress() =
        runTest(dispatcher) {
            initializationInProgress.value = true
            coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns FALLBACK_ADDRESS

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(FALLBACK_ADDRESS, viewModel.uiState.address)

            every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
            initializationInProgress.value = false
            advanceUntilIdle()

            assertEquals(ViewState.Success, viewModel.uiState.viewState)
            assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
        }

    @Test
    fun setData_beforeTheFreshAddressArrives_showsTheStaticAddressWithoutSuspendingOnTheSdk() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.freshReceiveAddress() } coAnswers { CompletableDeferred<String>().await() }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_freshAddressArrivesLater_replacesTheAddress() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        val fresh = CompletableDeferred<String>()
        coEvery { adapter.freshReceiveAddress() } coAnswers { fresh.await() }

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)

        fresh.complete(VERIFIED_ADDRESS)
        advanceUntilIdle()

        assertEquals(VERIFIED_ADDRESS, viewModel.uiState.address)
        assertEquals(VERIFIED_ADDRESS, viewModel.uiState.uri)
    }

    @Test
    fun setData_freshAddressJobFails_keepsTheShownAddress() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.freshReceiveAddress() } throws IllegalStateException("boom")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_discoveryCompletesWhileTheScreenIsOpen_replacesTheFallbackWithTheDerivedAddress() =
        runTest(dispatcher) {
            every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
            val changes = MutableSharedFlow<Unit>(replay = 1)
            changes.tryEmit(Unit)
            every { adapter.freshReceiveAddressChanges } returns changes
            var callCount = 0
            coEvery { adapter.freshReceiveAddress() } coAnswers {
                callCount++
                if (callCount == 1) ADAPTER_ADDRESS else VERIFIED_ADDRESS
            }

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)

            changes.emit(Unit)
            advanceUntilIdle()

            assertEquals(VERIFIED_ADDRESS, viewModel.uiState.address)
        }

    @Test
    fun setData_sessionAcquiredAfterAFailedOpen_replacesTheFallbackWhenTheWalkCompletes() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        val changes = MutableSharedFlow<Unit>(replay = 1)
        changes.tryEmit(Unit)
        every { adapter.freshReceiveAddressChanges } returns changes
        var callCount = 0
        coEvery { adapter.freshReceiveAddress() } coAnswers {
            callCount++
            if (callCount == 1) throw IllegalStateException("Zcash wallet session is unavailable")
            else VERIFIED_ADDRESS
        }

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)

        changes.emit(Unit)
        advanceUntilIdle()

        assertEquals(VERIFIED_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_onErrorClick_startsTheFreshAddressJob() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        every { adapter.usedAddresses(false) } throws IllegalStateException("boom")

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.viewState is ViewState.Error)

        every { adapter.usedAddresses(false) } returns emptyList()
        viewModel.onErrorClick()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        coVerify(atLeast = 1) { adapter.freshReceiveAddress() }
    }

    @Test
    fun setData_freshAddressDiffersFromTheStaticOne_showsTheSelectorAnswer() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.freshReceiveAddress() } returns DERIVED_ADDRESS

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(DERIVED_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_freshAddressEqualsTheStaticOne_showsTheStaticAddress() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.freshReceiveAddress() } returns ADAPTER_ADDRESS

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_calledAgain_cancelsTheEarlierFreshAddressJob() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        var callCount = 0
        coEvery { adapter.freshReceiveAddress() } coAnswers {
            callCount++
            if (callCount == 1) awaitCancellation() else VERIFIED_ADDRESS
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(VERIFIED_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_calledAgainWhileTheFreshAddressIsBeingApplied_showsTheLaterRendersAddress() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        val activation = CompletableDeferred<Boolean>()
        coEvery { adapter.isAddressActive(VERIFIED_ADDRESS) } coAnswers { activation.await() }
        var callCount = 0
        coEvery { adapter.freshReceiveAddress() } coAnswers {
            if (++callCount == 1) VERIFIED_ADDRESS else awaitCancellation()
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        adaptersReady.onNext(emptyMap())
        activation.complete(true)
        advanceUntilIdle()

        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_adapterVanishesAfterAVerifiedAnswer_keepsTheVerifiedAddressInsteadOfTheFallback() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.freshReceiveAddress() } returns VERIFIED_ADDRESS
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(VERIFIED_ADDRESS, viewModel.uiState.address)

        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns null
        coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns ADAPTER_ADDRESS
        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        val published = viewModel.uiState
        assertEquals(ViewState.Success, published.viewState)
        assertEquals(VERIFIED_ADDRESS, published.address)
    }

    @Test
    fun setData_activationCheckStillPending_publishesSuccessWithTheAddressAlreadyShown() = runTest(dispatcher) {
        Dispatchers.setMain(StandardTestDispatcher(dispatcher.scheduler))
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        val probe = CompletableDeferred<Boolean>()
        coEvery { adapter.isAddressActive(any()) } coAnswers { probe.await() }
        val viewModel = createViewModel()

        runCurrent()
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)

        probe.complete(false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.showTronAlert)
    }

    @Test
    fun setData_publishedWhileTheAdapterIsStillBeingRead_neverPairsSuccessWithAnEmptyAddress() = runTest(dispatcher) {
        // The collector runs on a real worker so it can be held inside the adapter read while Main publishes.
        Dispatchers.setMain(StandardTestDispatcher(dispatcher.scheduler))
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        val readingAddress = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val childStarted = CountDownLatch(1)
        every { adapter.receiveAddress } answers {
            readingAddress.countDown()
            releaseRead.await(5, TimeUnit.SECONDS)
            ADAPTER_ADDRESS
        }
        coEvery { adapter.freshReceiveAddress() } coAnswers {
            childStarted.countDown()
            ADAPTER_ADDRESS
        }
        val viewModel = ReceiveAddressViewModel(
            wallet = wallet,
            adapterManager = adapterManager,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO, testScope),
        )
        viewModelStore.put("receive-address", viewModel)

        assertTrue(readingAddress.await(5, TimeUnit.SECONDS))
        runCurrent()
        val published = viewModel.uiState
        assertFalse(
            "success published with an empty address",
            published.viewState == ViewState.Success && published.address.isEmpty(),
        )

        releaseRead.countDown()
        assertTrue(childStarted.await(5, TimeUnit.SECONDS))
        runCurrent()
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
    }

    @Test
    fun setData_calledAgainAfterAFailedActivationCheck_probesTheUnchangedAddressAgain() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } throws IllegalStateException("no network") andThen false
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.showTronAlert)

        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.showTronAlert)
    }

    @Test
    fun setData_usedAddressesThrows_showsTheErrorStateAndKeepsCollecting() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        every { adapter.usedAddresses(false) } throws IllegalStateException("boom")

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.viewState is ViewState.Error)

        every { adapter.usedAddresses(false) } returns emptyList()
        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
    }

    @Test
    fun setData_cancelledWhileAwaitingTheReceiveAddress_propagatesTheCancellation() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns null
        coEvery { adapterManager.getReceiveAddressForWallet(wallet) } coAnswers {
            CompletableDeferred<String?>().await()
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModelStore.clear()
        advanceUntilIdle()

        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
    }

    private companion object {
        const val ADAPTER_ADDRESS = "u1adapteraddress"
        const val FALLBACK_ADDRESS = "u1fallbackaddress"
        const val VERIFIED_ADDRESS = "t1verified"
        const val DERIVED_ADDRESS = "t1derived"
    }

    @Test
    fun setData_freshAddressEqualsTheStaticOne_checksActivationOnce() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.isAddressActive(ADAPTER_ADDRESS) }
    }
}
