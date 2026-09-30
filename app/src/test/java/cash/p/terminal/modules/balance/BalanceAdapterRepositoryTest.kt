package cash.p.terminal.modules.balance

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.managers.AdapterManager
import cash.p.terminal.core.managers.BeamLifecycleCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.PendingBalanceCalculator
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.processors.PublishProcessor
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceAdapterRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private lateinit var adapterManager: IAdapterManager
    private lateinit var balanceCache: BalanceCache
    private lateinit var pendingBalanceCalculator: PendingBalanceCalculator
    private lateinit var pendingChangedFlow: MutableSharedFlow<Unit>
    private lateinit var testWallet: Wallet

    @Before
    fun setUp() {
        pendingChangedFlow = MutableSharedFlow()

        adapterManager = mockk(relaxed = true) {
            every { adaptersReadyObservable } returns Flowable.never()
            every { walletBalanceUpdatedFlow } returns MutableSharedFlow()
            every { getAdjustedBalanceData(any()) } returns BalanceData(BigDecimal.TEN)
            every { getBalanceAdapterForWallet(any()) } returns mockk<IBalanceAdapter>(relaxed = true) {
                every { balanceStateUpdatedFlow } returns MutableSharedFlow()
                every { balanceUpdatedFlow } returns MutableSharedFlow()
            }
        }

        balanceCache = mockk(relaxed = true)

        pendingBalanceCalculator = mockk(relaxed = true)
        every { pendingBalanceCalculator.pendingChangedFlow } returns pendingChangedFlow

        testWallet = mockk(relaxed = true)
    }

    @Test
    fun updatesObservable_emitsWhenPendingTransactionsChange() = runTest(dispatcher) {
        val repository = createRepository()
        repository.setWallet(listOf(testWallet))

        val testObserver = repository.updatesObservable.test()

        pendingChangedFlow.emit(Unit)

        testObserver.assertValueCount(1)
        assertEquals(testWallet, testObserver.values().first())

        testObserver.dispose()
    }

    @Test
    fun updatesObservable_emitsForAllWalletsWhenPendingTransactionsChange() = runTest(dispatcher) {
        val wallet1 = mockk<Wallet>(relaxed = true)
        val wallet2 = mockk<Wallet>(relaxed = true)
        val wallet3 = mockk<Wallet>(relaxed = true)

        val repository = createRepository()
        repository.setWallet(listOf(wallet1, wallet2, wallet3))

        val testObserver = repository.updatesObservable.test()

        pendingChangedFlow.emit(Unit)

        testObserver.assertValueCount(3)
        assertTrue("Should emit wallet1", testObserver.values().contains(wallet1))
        assertTrue("Should emit wallet2", testObserver.values().contains(wallet2))
        assertTrue("Should emit wallet3", testObserver.values().contains(wallet3))

        testObserver.dispose()
    }

    @Test
    fun balanceData_unavailableThenAuthoritativeZero_preservesCacheUntilReadyAndPersistsZero() = runTest(dispatcher) {
        val ready = PublishProcessor.create<Map<Wallet, IAdapter>>()
        val updates = MutableSharedFlow<Wallet>()
        every { adapterManager.adaptersReadyObservable } returns ready
        every { adapterManager.walletBalanceUpdatedFlow } returns updates
        every { adapterManager.getAdjustedBalanceData(testWallet) } returns null
        every { balanceCache.getCache(testWallet) } returns BalanceData(BigDecimal.TEN)
        val repository = createRepository()
        repository.setWallet(listOf(testWallet))
        try {
            ready.onNext(emptyMap())
            updates.emit(testWallet)
            pendingChangedFlow.emit(Unit)
            assertEquals(BigDecimal.TEN, repository.balanceData(testWallet).available)
            verify(exactly = 0) { balanceCache.setCache(testWallet, any()) }
            verify { balanceCache.setCache(emptyMap()) }
            val zero = BalanceData(BigDecimal.ZERO)
            every { adapterManager.getAdjustedBalanceData(testWallet) } returns zero
            updates.emit(testWallet)
            assertEquals(zero, repository.balanceData(testWallet))
            verify(exactly = 1) { balanceCache.setCache(testWallet, zero) }
        } finally {
            repository.clear()
        }
    }

    @Test
    fun balanceData_freshWalletWithoutAuthoritativeBalanceOrCache_displaysZero() = runTest(dispatcher) {
        every { adapterManager.getAdjustedBalanceData(testWallet) } returns null
        every { balanceCache.getCache(testWallet) } returns null
        val repository = createRepository()
        try {
            assertEquals(BigDecimal.ZERO, repository.balanceData(testWallet).available)
        } finally {
            repository.clear()
        }
    }

    // R1: the row a syncing BEAM wallet shows. Deliberately end-to-end over the real
    // BeamAdapter and the real AdapterManager, because the seam that produced the reported
    // zero sits between them, not in this repository.
    @Test
    fun balanceData_beamSyncingWithADatabaseBalance_showsTheAmountsNotZero() = runTest(dispatcher) {
        val sdkBalance = MutableStateFlow(BeamBalance())
        val sdkState = MutableStateFlow<BeamWalletState>(BeamWalletState.Syncing(10, 100, 1, 10))
        val adapter = beamAdapter(sdkBalance, sdkState)
        val wallet = mockk<Wallet>(relaxed = true) {
            every { token } returns mockk(relaxed = true) {
                every { blockchainType } returns BlockchainType.Beam
            }
        }
        val manager = realAdapterManager(wallet, adapter)
        every { pendingBalanceCalculator.adjustBalance(any(), any()) } answers { secondArg() }
        every { balanceCache.getCache(any()) } returns null
        val repository = BalanceAdapterRepository(
            manager,
            balanceCache,
            pendingBalanceCalculator,
            TestDispatcherProvider(dispatcher, testScope),
        )
        try {
            repository.setWallet(listOf(wallet))
            adapter.attachLocalData()
            // What the wallet database knows before the node has confirmed anything.
            sdkBalance.value = BeamBalance(available = 4200, loadedFromDatabase = true)
            manager.startAdapterManager()
            advanceUntilIdle()

            assertEquals(BigDecimal("0.00004200"), repository.balanceData(wallet).available)
            // Drive the per-wallet write path, so the sibling test's "never written" assertion is
            // measured against a path that demonstrably fires when there is something to write.
            // The manager's own balance push reaches the same path, so this does not isolate the
            // pending-changed trigger and is not meant to.
            pendingChangedFlow.emit(Unit)
            verify {
                balanceCache.setCache(
                    wallet,
                    withArg<BalanceData> { assertEquals(0, BigDecimal("0.00004200").compareTo(it.available)) },
                )
            }
            // The same amounts are what gets persisted, so a later launch without an adapter shows them.
            verify {
                balanceCache.setCache(
                    withArg<Map<Wallet, BalanceData>> { persisted ->
                        assertEquals(setOf(wallet), persisted.keys)
                        assertEquals(0, BigDecimal("0.00004200").compareTo(persisted.getValue(wallet).available))
                    }
                )
            }
        } finally {
            repository.clear()
            adapter.close()
            manager.quit()
        }
    }

    // The other half of R1: before the SDK has read the wallet database there are no amounts to
    // show, and the row must keep the last cached figure rather than render — and then persist —
    // a zero. In the field this window is what an offline or airplane-mode launch sits in, because
    // the session never starts; here it is reproduced directly by an unloaded SDK balance, which is
    // the condition the fix actually keys on.
    @Test
    fun balanceData_beamDatabaseNotReadYet_keepsTheCachedAmountAndDoesNotPersistZero() = runTest(dispatcher) {
        val sdkBalance = MutableStateFlow(BeamBalance())
        val sdkState = MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
        val adapter = beamAdapter(sdkBalance, sdkState)
        val wallet = mockk<Wallet>(relaxed = true) {
            every { token } returns mockk(relaxed = true) {
                every { blockchainType } returns BlockchainType.Beam
            }
        }
        val manager = realAdapterManager(wallet, adapter)
        every { pendingBalanceCalculator.adjustBalance(any(), any()) } answers { secondArg() }
        every { balanceCache.getCache(wallet) } returns BalanceData(BigDecimal("4.2"))
        val repository = BalanceAdapterRepository(
            manager,
            balanceCache,
            pendingBalanceCalculator,
            TestDispatcherProvider(dispatcher, testScope),
        )
        try {
            repository.setWallet(listOf(wallet))
            adapter.attachLocalData()
            manager.startAdapterManager()
            advanceUntilIdle()

            assertEquals(BigDecimal("4.2"), repository.balanceData(wallet).available)
            // The destructive half of the bug: an unknown balance must not be written over the
            // cached row. The wallet is simply absent from the map the ready pass persists, and
            // the per-wallet write path stays silent even when it is explicitly driven.
            pendingChangedFlow.emit(Unit)
            verify { balanceCache.setCache(emptyMap()) }
            verify(exactly = 0) { balanceCache.setCache(wallet, any()) }
        } finally {
            repository.clear()
            adapter.close()
            manager.quit()
        }
    }

    private fun beamAdapter(
        sdkBalance: MutableStateFlow<BeamBalance>,
        sdkState: MutableStateFlow<BeamWalletState>,
    ): BeamAdapter {
        val sdk = mockk<BeamWalletSession> {
            every { state } returns sdkState
            every { balance } returns sdkBalance
            every { transactions } returns MutableStateFlow(emptyList())
        }
        val session = mockk<BeamSessionOwner.Session> {
            every { accountId } returns "beam-account"
            every { wallet } returns sdk
            every { receiveAddress } returns
                BeamAddress("public-offline-test", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
        }
        val owner = mockk<BeamSessionOwner>(relaxed = true) { every { current } returns session }
        val lifecycle = BeamLifecycleCoordinator(
            mockk { every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground) },
            mockk { every { keepAliveBlockchains } returns MutableStateFlow(emptySet()) },
            mockk {
                every { isConnected } returns MutableStateFlow(true)
                every { acquireMonitoringLease() } returns AutoCloseable { }
                coEvery { refreshAndAwaitValidation() } returns true
            },
            mockk {
                every { effectiveFlow } returns MutableStateFlow(emptySet())
                every { stateFlow } returns MutableStateFlow(emptyMap())
                every { isNetworkPaused(any()) } returns false
            },
        )
        return BeamAdapter(
            owner,
            session,
            mockk<DispatcherProvider> { every { io } returns dispatcher },
            mockk(relaxed = true),
            lifecycle,
        )
    }

    // Deliberately NOT started: the repository must subscribe to adaptersReadyObservable and
    // walletBalanceUpdatedFlow before they fire, or a cache assertion can never fail.
    private fun realAdapterManager(wallet: Wallet, adapter: BeamAdapter): AdapterManager {
        val activeWallets = MutableStateFlow(listOf(wallet))
        val walletManager = mockk<IWalletManager>(relaxed = true) {
            every { this@mockk.activeWallets } answers { activeWallets.value }
            every { activeWalletsFlow } returns activeWallets
        }
        val adapterFactory = mockk<AdapterFactory>(relaxed = true)
        coEvery { adapterFactory.getAdapterOrNull(wallet, any()) } returns adapter
        val manager = AdapterManager(
            walletManager,
            adapterFactory,
            btcBlockchainManager = mockk(relaxed = true) {
                every { restoreModeUpdatedObservable } returns PublishSubject.create()
            },
            evmBlockchainManager = mockk(relaxed = true) { every { allBlockchains } returns emptyList() },
            solanaKitManager = mockk(relaxed = true) { every { kitStoppedObservable } returns Observable.never() },
            tronKitManager = mockk(relaxed = true),
            tonKitManager = mockk(relaxed = true),
            moneroKitManager = mockk(relaxed = true) { every { kitStoppedObservable } returns Observable.never() },
            stellarKitManager = mockk(relaxed = true),
            pendingBalanceCalculator = pendingBalanceCalculator,
            fallbackAddressProvider = mockk(relaxed = true),
            offlineModeManager = mockk<OfflineModeManager>(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(dispatcher, testScope),
        )
        return manager
    }

    private fun createRepository() = BalanceAdapterRepository(
        adapterManager,
        balanceCache,
        pendingBalanceCalculator,
        TestDispatcherProvider(dispatcher, testScope)
    )
}
