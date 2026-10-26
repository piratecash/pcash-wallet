package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.requiresTrezorPreparation
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerNotFoundException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.m2049r.xmrwallet.service.DaemonConnectResult
import com.m2049r.xmrwallet.service.LocalOpenResult
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.MoneroNative
import com.piratecash.monero.signer.ExternalSignerRegistration
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.File
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.StubMethod
import net.bytebuddy.matcher.ElementMatchers.isNative
import net.bytebuddy.matcher.ElementMatchers.isPublic
import net.bytebuddy.matcher.ElementMatchers.isStatic
import net.bytebuddy.matcher.ElementMatchers.not
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroKitWrapperRefreshTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    private val controlledRefreshCoordinatorJobs = mutableListOf<Job>()

    @After
    fun cancelControlledRefreshCoordinatorJobs() {
        controlledRefreshCoordinatorJobs.forEach(Job::cancel)
        controlledRefreshCoordinatorJobs.clear()
    }


    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val account = Account(
        id = "account-id",
        name = "Monero",
        type = AccountType.MnemonicMonero(
            words = emptyList(),
            password = "password",
            height = 1,
            walletInnerName = "wallet"
        ),
        origin = AccountOrigin.Created,
        level = 0
    )
    private val unsupportedAccount = account.copy(
        type = AccountType.EvmAddress("0x1234")
    )
    private val trezorAccount = account.copy(
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
    )
    private val ledgerAccount = account.copy(
        type = AccountType.LedgerDevice(deviceId = "device-id", model = "NanoSPlus", walletPublicKey = "wallet-key"),
    )

    @Test
    fun refresh_syncingWallet_doesNotStopService() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)

        wrapper.refresh()

        verify(exactly = 0) { service.stop(any()) }
    }

    @Test
    fun refresh_pausedWalletFailedResume_restartsWallet() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, unsupportedAccount)
        every { service.resume(wrapper) } returns false

        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        wrapper.pause()
        wrapper.refresh()

        verify(exactly = 1) { service.resume(wrapper) }
        verify(exactly = 1) { service.stop(false) }
    }

    @Test
    fun getBalance_walletNotOpen_returnsNull() {
        // A real service: stubbing `wallet` on a mock instantiates Wallet, whose static init loads the native lib.
        assertNull(createWrapper(MoneroWalletService(File("unused"))).getBalance())
    }

    @Test
    fun getBalance_duringStop_returnsNull() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)
        val balancesReadDuringStop = mutableListOf<Long?>()
        every { service.stop(any()) } answers {
            balancesReadDuringStop += wrapper.getBalance()
            true
        }
        setStarted(wrapper)

        wrapper.stop()

        assertEquals(listOf<Long?>(null), balancesReadDuringStop)
    }

    @Test
    fun startAccountServiceOrOpenLocally_nodeUnreachable_opensWalletLocally() = runTest {
        val service = nodeUnreachableService(LocalOpenResult.Opened)
        val wrapper = createWrapper(service)
        var transactionUpdates = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            wrapper.transactionsStateUpdatedFlow.collect { transactionUpdates++ }
        }

        assertFalse(wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true))

        verify(exactly = 1) { service.startOffline("wallet", "password") }
        val state = wrapper.syncState.value as AdapterState.NotSynced
        assertEquals("No internet connection", state.error.message)
        assertFalse(wrapper.isNetworkOnline)
        assertEquals(1, transactionUpdates)
        assertTrue(openedWithoutNode(wrapper))
    }

    @Test
    fun startAccountServiceOrOpenLocally_nodeUnreachableLocalOpenFails_keepsWalletClosed() = runTest {
        val service = nodeUnreachableService(LocalOpenResult.Failed(null, "boom"))
        val wrapper = createWrapper(service)

        assertFalse(wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true))

        val state = wrapper.syncState.value as AdapterState.NotSynced
        assertEquals("boom", state.error.message)
        assertFalse(wrapper.isNetworkOnline)
        assertTrue(wrapper.getTransactions().isEmpty())
        assertFalse(openedWithoutNode(wrapper))
    }

    @Test
    fun startAccountServiceOrOpenLocally_otherStartFailure_propagatesWithoutLocalOpen() = runTest {
        val service = mockService()
        val failure = IllegalStateException("native failure")
        every { service.start("wallet", "password") } throws failure
        val wrapper = createWrapper(service)

        val thrown = runCatching {
            wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true)
        }.exceptionOrNull()

        assertSame(failure, thrown)
        verify(exactly = 0) { service.startOffline(any(), any()) }
    }

    @Test
    fun startAccountServiceOrOpenLocally_nodeReachable_doesNotOpenLocally() = runTest {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true) {
            every { isOk } returns true
        }
        every { service.start("wallet", "password") } returns status
        val wrapper = createWrapper(service)

        assertTrue(wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true))

        verify(exactly = 0) { service.startOffline(any(), any()) }
    }

    @Test
    fun stop_openedWithoutNode_clearsFlag() = runTest {
        val service = nodeUnreachableService(LocalOpenResult.Opened)
        every { service.stop(true) } returns true
        val wrapper = createWrapper(service)
        wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true)

        wrapper.stop()

        assertFalse(openedWithoutNode(wrapper))
        assertFalse(wrapper.isNetworkOnline)
    }

    @Test
    fun resume_pausedHardwareWallet_doesNotConnectDaemon() = runTest {
        assertPausedWalletResumeDoesNotConnectDaemon(trezorAccount)
    }

    @Test
    fun resume_pausedLocalWalletNotFromFallback_doesNotConnectDaemon() = runTest {
        assertPausedWalletResumeDoesNotConnectDaemon(account)
    }

    @Test
    fun resume_fallbackWalletNodeReachable_connectsNodeBeforeResuming() = runTest {
        val daemonSelector = mockk<MoneroDaemonSelector>(relaxed = true)
        val (service, wrapper) = fallbackWallet(DaemonConnectResult.Connected, daemonSelector)

        assertTrue(wrapper.resume())

        coVerifyOrder {
            daemonSelector.selectDaemon()
            service.connectDaemon()
            service.resume(wrapper)
        }
        assertTrue(wrapper.isNetworkOnline)
        assertFalse(openedWithoutNode(wrapper))
    }

    @Test
    fun resume_fallbackWalletNodeStillUnreachable_staysOpenLocally() = runTest {
        val (service, wrapper) = fallbackWallet(DaemonConnectResult.Failed(null))

        assertFalse(wrapper.resume())

        assertFallbackWalletStaysOpenLocally(service, wrapper)
    }

    @Test
    fun refresh_fallbackWalletNodeStillUnreachable_staysOpenLocally() = runTest {
        val (service, wrapper) = fallbackWallet(DaemonConnectResult.Failed(null))

        wrapper.refresh()

        assertFallbackWalletStaysOpenLocally(service, wrapper)
    }

    // A failed connectDaemon() still initializes the daemon, so the kit then resumes the wallet without a node.
    private suspend fun fallbackWallet(
        connectResult: DaemonConnectResult,
        daemonSelector: MoneroDaemonSelector = mockk(relaxed = true),
    ): Pair<MoneroWalletService, MoneroKitWrapper> {
        val service = nodeUnreachableService(LocalOpenResult.Opened)
        every { service.connectDaemon() } returns connectResult
        val wrapper = createWrapper(service, daemonSelector = daemonSelector)
        every { service.resume(wrapper) } returns true
        wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true)
        return service to wrapper
    }

    private fun assertFallbackWalletStaysOpenLocally(service: MoneroWalletService, wrapper: MoneroKitWrapper) {
        verify(exactly = 1) { service.connectDaemon() }
        verify(exactly = 0) { service.resume(any()) }
        verify(exactly = 0) { service.stop(any()) }
        assertFalse(wrapper.isNetworkOnline)
        assertTrue(openedWithoutNode(wrapper))
    }

    private suspend fun assertPausedWalletResumeDoesNotConnectDaemon(account: Account) {
        val service = mockService()
        val wrapper = createWrapper(service, account)
        every { service.resume(wrapper) } returns false
        setStarted(wrapper)
        wrapper.pause()

        assertFalse(wrapper.resume())

        verify(exactly = 0) { service.connectDaemon() }
    }

    private fun nodeUnreachableService(localOpen: LocalOpenResult): MoneroWalletService {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true) {
            every { isOk } returns false
            every { connectionStatus } returns ConnectionStatus.ConnectionStatus_Disconnected
        }
        every { service.start("wallet", "password") } returns status
        every { service.startOffline("wallet", "password") } returns localOpen
        return service
    }

    private fun openedWithoutNode(wrapper: MoneroKitWrapper): Boolean =
        privateField(wrapper, "openedWithoutNode").getBoolean(wrapper)

    @Test
    fun refreshHardwareKeyImagesWithProgress_reportsNativeProgressAndClearsObserver() {
        val events = mutableListOf<String>()
        val states = mutableListOf<AdapterState>()
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        var progressObserver: ((Long) -> Unit)? = null
        var fallbackCalls = 0
        every { service.setControlledRefreshProgressObserver(any()) } answers {
            events += "register"
            progressObserver = firstArg()
        }
        every { service.clearControlledRefreshProgressObserver() } answers { events += "clear" }

        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        wrapper.refreshHardwareKeyImagesWithProgress(
            cachedTotalHeight = 0,
            fallbackTotalHeight = {
                events += "fallback"
                fallbackCalls += 1
                1_000
            },
            onNewBlock = {},
        ) {
            events += "refresh"
            progressObserver?.let { observer ->
                observer(400)
                states += wrapper.syncState.value
                observer(500)
                states += wrapper.syncState.value
            }
        }

        assertNotNull(progressObserver)
        assertEquals(
            listOf(
                AdapterState.Syncing(progress = 40.0, blocksRemained = 600),
                AdapterState.Syncing(progress = 50.0, blocksRemained = 500),
            ),
            states,
        )
        assertEquals(1, fallbackCalls)
        assertEquals(listOf("fallback", "register", "refresh", "clear"), events)
    }

    @Test
    fun refreshHardwareKeyImagesWithProgress_refreshFailureClearsObserver() {
        val events = mutableListOf<String>()
        val error = IllegalStateException("refresh failed")
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every { service.setControlledRefreshProgressObserver(any()) } answers { events += "register" }
        every { service.clearControlledRefreshProgressObserver() } answers { events += "clear" }

        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        val thrown = try {
            wrapper.refreshHardwareKeyImagesWithProgress(
                cachedTotalHeight = 1_000,
                fallbackTotalHeight = { error("fallback should not be called") },
                onNewBlock = {},
            ) {
                events += "refresh"
                throw error
            }
            null
        } catch (caught: Throwable) {
            caught
        }

        assertSame(error, thrown)
        assertEquals(listOf("register", "refresh", "clear"), events)
    }

    @Test
    fun rescan_hardwareGatewayWaiting_publishesSyncingBeforeNativeCallback() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        val coordinator = privateField(wrapper, "controlledRefreshCoordinator")
            .get(wrapper) as ControlledHardwareRefreshCoordinator
        val mutex = coordinatorMutex(coordinator)
        mutex.lock()

        try {
            assertSame(AdapterState.Synced, wrapper.syncState.value)
            val rescan = async(start = CoroutineStart.UNDISPATCHED) { wrapper.rescan(42) }

            assertTrue(wrapper.syncState.value is AdapterState.Syncing)
            verify(exactly = 1) { restoreSettingsManager.savePendingMoneroRescan(trezorAccount, 42) }
            wrapper.onRefreshed(wallet = null, full = true)
            assertTrue(wrapper.syncState.value is AdapterState.Syncing)
            privateField(wrapper, "controlledLiveRefreshPending").set(wrapper, true)
            privateField(wrapper, "controlledLiveRefreshCommitted").set(wrapper, true)
            wrapper.onRefreshed(wallet = null, full = true)
            assertSame(AdapterState.Synced, wrapper.syncState.value)
            rescan.cancelAndJoin()
        } finally {
            mutex.unlock()
        }
    }

    @Test
    fun abortControlledHardwareWallet_syncingRescanFailure_publishesNotSyncedWithSameError() {
        val error = IllegalStateException("Trezor rejected rescan")
        val wrapper = createWrapper(mockService(), trezorAccount)
        setSyncState(wrapper, AdapterState.Syncing())

        invokeAbortControlledHardwareWallet(wrapper, error)

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertSame(error, state?.error)
    }

    @Test
    fun controlledRefreshCoordinator_failureAbortsBeforeSignerRelease() = runTest {
        val registration = mockk<ExternalSignerRegistration>(relaxed = true)
        val abort = mockk<() -> Unit>(relaxed = true)
        val coordinator = controlledRefreshCoordinator()

        assertTrue(
            runCatching {
                coordinator.execute(
                    testControlledRefreshOperation(
                        refresh = { aborter ->
                            withExternalSignerRegistration(registration) {
                                try {
                                    error("refresh failed")
                                } catch (error: Throwable) {
                                    aborter.abort(error)
                                    throw error
                                }
                            }
                        },
                        abort = { abort() },
                    ),
                )
            }.isFailure,
        )

        verifyOrder {
            abort.invoke()
            registration.release()
        }
    }

    @Test
    fun controlledRefreshCoordinator_concurrentCallers_createOneOperationAndJoin() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        var refreshCount = 0
        var finalizationCount = 0
        val operation = testControlledRefreshOperation(
            refresh = {
                refreshCount += 1
                refreshStarted.complete(Unit)
                refreshGate.await()
            },
            finalize = { finalizationCount += 1 },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation) }
        refreshStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation) }

        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        assertEquals(1, refreshCount)
        refreshGate.complete(Unit)

        first.await()
        second.await()
        assertEquals(1, finalizationCount)
    }

    @Test
    fun controlledRefreshCoordinator_lastCancelledWaiter_releasesInNonCancellableContext() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        var abortError: Throwable? = null
        val operation = testControlledRefreshOperation(
            refresh = {
                refreshStarted.complete(Unit)
                refreshGate.await()
            },
            abort = { abortError = it },
        )

        val waiter = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation) }
        refreshStarted.await()
        val mutex = coordinatorMutex(coordinator)
        mutex.lock()
        waiter.cancel()
        runCurrent()

        assertEquals(null, abortError)
        mutex.unlock()
        waiter.join()
        runCurrent()

        assertTrue(abortError is CancellationException)
    }

    @Test
    fun controlledRefreshCoordinator_cancelDuringFinalization_completesAbortBeforeFreshRetry() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val finalizationStarted = CompletableDeferred<Unit>()
        val abortFinished = CompletableDeferred<Unit>()
        val releaseFinalization = CompletableDeferred<Unit>()
        var retryRefreshCount = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(
                testControlledRefreshOperation(
                    finalize = {
                        finalizationStarted.complete(Unit)
                        releaseFinalization.await()
                    },
                    abort = { abortFinished.complete(Unit) },
                ),
            )
        }
        finalizationStarted.await()

        first.cancel()
        runCurrent()
        abortFinished.await()
        first.join()

        val retry = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(
                testControlledRefreshOperation(refresh = { retryRefreshCount += 1 }),
            )
        }
        retry.await()
        assertEquals(1, retryRefreshCount)
    }

    @Test
    fun controlledRefreshCoordinator_cancelDuringFinalization_abortsBeforeQueuedLifecycleOperation() =
        runTest {
            val coordinator = controlledRefreshCoordinator()
            val lifecycleMutex = Mutex()
            val finalizationStarted = CompletableDeferred<Unit>()
            val releaseFinalization = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val operation = object : ControlledHardwareRefreshOperation {
                override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
                    lifecycleMutex.withLock {
                        try {
                            finalizationStarted.complete(Unit)
                            releaseFinalization.await()
                        } catch (error: Throwable) {
                            aborter.abort(error)
                            throw error
                        }
                    }
                }

                override fun abort(error: Throwable) {
                    order += "abort"
                }
            }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.execute(operation)
            }
            finalizationStarted.await()
            val lifecycleOperation = async(start = CoroutineStart.UNDISPATCHED) {
                lifecycleMutex.withLock {
                    order += "lifecycle"
                }
            }

            refresh.cancel()
            refresh.join()
            lifecycleOperation.await()

            assertEquals(listOf("abort", "lifecycle"), order)
        }

    @Test
    fun controlledRefreshCoordinator_cancelWhileWaitingForLifecycle_doesNotAbortUnstartedOperation() =
        runTest {
            val coordinator = controlledRefreshCoordinator()
            val lifecycleMutex = Mutex(locked = true)
            val runStarted = CompletableDeferred<Unit>()
            var abortCount = 0
            val operation = object : ControlledHardwareRefreshOperation {
                override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
                    runStarted.complete(Unit)
                    lifecycleMutex.withLock { }
                }

                override fun abort(error: Throwable) {
                    abortCount += 1
                }
            }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.execute(operation)
            }
            runStarted.await()

            refresh.cancel()
            refresh.join()
            lifecycleMutex.unlock()

            assertEquals(0, abortCount)
        }

    @Test
    fun controlledRefreshCoordinator_staleWaitersDoNotKeepReplacementOperationAlive() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val staleScheduler = TestCoroutineScheduler()
        val staleSupervisor = SupervisorJob()
        val staleScope = CoroutineScope(staleSupervisor + StandardTestDispatcher(staleScheduler))
        val firstStarted = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        val replacementStarted = CompletableDeferred<Unit>()
        val replacementGate = CompletableDeferred<Unit>()
        var replacementAbortCount = 0

        try {
            val firstOperation = testControlledRefreshOperation(
                refresh = {
                    firstStarted.complete(Unit)
                    firstGate.await()
                },
            )
            staleScope.async { coordinator.execute(firstOperation) }
            staleScope.async { coordinator.execute(firstOperation) }
            staleScheduler.runCurrent()
            runCurrent()
            firstStarted.await()

            firstGate.complete(Unit)
            runCurrent()

            val replacement = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.execute(
                    testControlledRefreshOperation(
                        refresh = {
                            replacementStarted.complete(Unit)
                            replacementGate.await()
                        },
                        abort = { replacementAbortCount += 1 },
                    ),
                )
            }
            replacementStarted.await()

            replacement.cancel()
            replacement.join()
            runCurrent()

            assertEquals(1, replacementAbortCount)
        } finally {
            firstGate.complete(Unit)
            replacementGate.complete(Unit)
            staleScheduler.runCurrent()
            runCurrent()
            staleSupervisor.cancel()
        }
    }

    @Test
    fun abortControlledHardwareWallet_leavesRetryableKeyImageSyncReadiness() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        invokeAbortControlledHardwareWallet(wrapper, IllegalStateException("refresh failed"))

        assertSame(MoneroSpendReadiness.NeedsKeyImageSync, wrapper.spendReadiness.value)
    }

    @Test
    fun openPausedHardwareWalletForRefresh_armsSessionBeforeSynchronousStartCallback() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        var sessionDuringStart: Long? = null

        wrapper.openPausedHardwareWalletForRefresh {
            assertTrue(privateField(wrapper, "startInProgress").getBoolean(wrapper))
            sessionDuringStart = activeReconciliationSession(wrapper)
        }

        assertTrue(sessionDuringStart != null)
        assertFalse(privateField(wrapper, "startInProgress").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isStarted").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isPaused").getBoolean(wrapper))
    }

    @Test
    fun controlledRefreshCoordinator_failureAbortsThenRetryCreatesFreshOperation() = runTest {
        val coordinator = controlledRefreshCoordinator()
        var abortCount = 0
        var retryRefreshCount = 0
        var retryFinalizationCount = 0

        assertTrue(
            runCatching {
                coordinator.execute(
                    testControlledRefreshOperation(
                        refresh = { throw IllegalStateException("refresh failed") },
                        abort = { abortCount += 1 },
                    ),
                )
            }.isFailure,
        )

        coordinator.execute(
            testControlledRefreshOperation(
                refresh = { retryRefreshCount += 1 },
                finalize = { retryFinalizationCount += 1 },
            ),
        )

        assertEquals(1, abortCount)
        assertEquals(1, retryRefreshCount)
        assertEquals(1, retryFinalizationCount)
    }

    @Test
    fun controlledRefreshFinalization_missingCallback_isRetryableHardwareFailure() = runTest {
        val failure = runCatching {
            awaitControlledRefreshFinalization(CompletableDeferred(), timeoutMillis = 1)
        }.exceptionOrNull()

        assertTrue(failure is HardwareWalletOperationException)
    }

    @Test
    fun hardwareStartupRecovery_explicitColdRecoveryPending_resumesExplicitRecovery() {
        assertSame(
            HardwareStartupRecovery.ExplicitColdRecovery,
            hardwareStartupRecovery(MoneroSpentReconciliationState.ExplicitColdRecoveryPending),
        )
    }

    @Test
    fun startAccountService_initialCatchUp_releasesGatewayBeforeOrdinaryResume() = runTest {
        val events = mutableListOf<String>()
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroHardwareGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.LiveRefreshPending
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } answers {
            events += "start_paused"
            status
        }
        every { service.resume(wrapper) } answers {
            events += "resume"
            true
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway_enter"
            try {
                secondArg<(String) -> Any?>().invoke("wallet-key")
            } finally {
                events += "gateway_exit"
            }
        }

        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(listOf("gateway_enter", "start_paused", "gateway_exit", "resume"), events)
        verify(exactly = 0) { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) }
        verify(exactly = 1) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "controlledLiveRefreshPending").getBoolean(wrapper))
        assertFalse(privateField(wrapper, "controlledLiveRefreshCommitted").getBoolean(wrapper))
    }

    @Test
    fun startAccountService_readyWallet_refreshesWhileGatewayOwned() = runTest {
        val events = mutableListOf<String>()
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroHardwareGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.Ready
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } answers {
            events += "start_paused"
            status
        }
        every { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) } answers {
            events += "controlled_refresh"
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway_enter"
            try {
                secondArg<(String) -> Any?>().invoke("wallet-key")
            } finally {
                events += "gateway_exit"
            }
        }

        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(listOf("gateway_enter", "start_paused", "controlled_refresh", "gateway_exit"), events)
        verify(exactly = 1) { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) }
        verify(exactly = 0) { service.resume(wrapper) }
        verify(exactly = 1) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
        assertTrue(privateField(wrapper, "controlledLiveRefreshPending").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "controlledLiveRefreshCommitted").getBoolean(wrapper))
    }

    @Test
    fun startAccountService_migrationReplayFailure_remainsPendingAndRetries() = runTest {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroHardwareGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        val events = mutableListOf<String>()
        var refreshAttempts = 0
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.MigrationReplayRequired
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } returns status
        every { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) } answers {
            events += "refresh"
            if (refreshAttempts++ == 0) error("refresh failed")
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway"
            secondArg<(String) -> Any?>().invoke("wallet-key")
        }
        every {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.MigrationReplayPending,
            )
        } answers { events += "pending" }

        assertTrue(
            runCatching {
                wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
            }.isFailure,
        )
        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(
            listOf("pending", "gateway", "refresh", "pending", "gateway", "refresh"),
            events,
        )
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    @Test
    fun startAccountService_ledgerNotConnected_failsWithoutOpeningWalletOrColdState() = runTest {
        val service = mockService()
        val gateway = mockk<MoneroHardwareGateway>()
        val wrapper = createWrapper(service, ledgerAccount, gateway = gateway)
        val missing = LedgerNotFoundException()
        coEvery { gateway.executeUnattended<Any?>(ledgerAccount, any()) } throws missing

        val thrown = runCatching {
            wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
        }.exceptionOrNull()

        assertSame(missing, thrown)
        verify(exactly = 0) { service.startPaused(any(), any()) }
        verify(exactly = 0) { service.start(any(), any()) }
        verify(exactly = 0) { restoreSettingsManager.moneroSpentReconciliationState(any()) }
        verify(exactly = 0) { restoreSettingsManager.saveMoneroSpentReconciliationState(any(), any()) }
    }

    @Test
    fun handleStartFailure_ledgerNotConnected_showsNotSyncedWithoutThrowing() {
        val failure = LedgerNotFoundException()
        val wrapper = spyk(createWrapper(mockService(), ledgerAccount), recordPrivateCalls = true)
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns true
        invokeActivateReconciliationSession(wrapper)

        invokeHandleStartFailure(wrapper, failure)

        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun startAccountService_ledgerWalletDoesNotOpen_neverResetsOrStoresWallet() = runTest {
        val service = mockService()
        val wrapper = spyk(createWrapper(service, ledgerAccount, gateway = ledgerGateway(mutableListOf())))
        // A null status is what makes a software wallet treat its files as corrupted.
        every { service.startPaused("wallet", "password") } returns null
        invokeActivateReconciliationSession(wrapper)

        val thrown = runCatching {
            wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
        }.exceptionOrNull()

        assertTrue(thrown is HardwareWalletOperationException)
        verify(exactly = 0) { wrapper.refreshProxyWalletLeaseOwned() }
        verify(exactly = 0) { service.start(any(), any()) }
        verify(exactly = 0) { service.stop(any()) }
        verify(exactly = 0) { restoreSettingsManager.settings(any(), any()) }
    }

    @Test
    fun startAccountService_viewKeyExportDenied_failsWithInstructionWithoutSync() = runTest {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val wrapper = spyk(createWrapper(service, ledgerAccount, gateway = ledgerGateway(mutableListOf())))
        every { status.isOk } returns false
        every { status.hardwareWalletError } returns HardwareWalletErrorCode.ViewKeyExportDenied
        every { service.startPaused("wallet", "password") } returns status
        invokeActivateReconciliationSession(wrapper)

        val thrown = runCatching {
            wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
        }.exceptionOrNull()

        assertSame(HardwareWalletErrorCode.ViewKeyExportDenied, (thrown as HardwareWalletOperationException).error)
        verify(exactly = 0) { wrapper.refreshProxyWalletLeaseOwned() }
    }

    @Test
    fun startAccountService_ledgerConnected_opensWithoutScanning() = runTest {
        val events = mutableListOf<String>()
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val wrapper = spyk(createWrapper(service, ledgerAccount, gateway = ledgerGateway(events)))
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } answers {
            events += "open:" + (wrapper.syncState.value as AdapterState.Syncing).substatus
            status
        }
        invokeActivateReconciliationSession(wrapper)

        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(
            listOf("unattended_enter", "open:${AdapterState.Substatus.ConfirmViewKeyExport}", "gateway_exit"),
            events,
        )
        assertEquals(AdapterState.Connecting, wrapper.syncState.value)
        verify(exactly = 0) { wrapper.refreshProxyWalletLeaseOwned() }
        verify(exactly = 0) { restoreSettingsManager.saveMoneroSpentReconciliationState(any(), any()) }
    }

    @Test
    fun refresh_ledgerUnpluggedDuringSync_keepsWalletOpenAndNotSynced() = runTest {
        val service = mockService()
        val lost = HardwareWalletOperationException(HardwareWalletErrorCode.Disconnected, "unplugged")
        val wrapper = spyk(createWrapper(service, ledgerAccount, gateway = ledgerGateway(mutableListOf())))
        every { wrapper.refreshProxyWalletLeaseOwned() } throws lost
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        wrapper.refresh()

        assertSame(lost, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        verify(exactly = 0) { service.stop(any()) }
    }

    @Test
    fun start_ledgerFirstSyncStoreFault_closesWalletAndPublishesNotSynced() = runTest {
        val scan = LedgerScan(mutableListOf())
        val wrapper = closedLedgerWrapper(scan)
        every { scan.wallet.storeSafe() } returns MONERO_STORE_NATIVE_FAULT

        withWalletRecord { wrapper.start() }
        awaitProxySync(wrapper)

        val failure = (wrapper.syncState.value as AdapterState.NotSynced).error
        assertSame(HardwareWalletErrorCode.StoreFailed, (failure as HardwareWalletOperationException).error)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
        verify(exactly = 1) { scan.service.abandonFaultedWallet() }
    }

    @Test
    fun start_ledgerAbsent_failsWithoutSelectingDaemonOrOpeningDeviceSession() = runTest {
        val daemonSelector = mockk<MoneroDaemonSelector>(relaxed = true)
        val gateway = mockk<MoneroHardwareGateway>(relaxed = true)
        val appAccess = mockk<LedgerAppAccess>()
        val wrapper = createWrapper(
            mockService(),
            ledgerAccount,
            gateway = gateway,
            daemonSelector = daemonSelector,
            ledgerSessions = realLedgerGateway(appAccess, LedgerUserOperations(), devicePresent = false),
        )

        withWalletRecord { wrapper.start() }

        assertTrue((wrapper.syncState.value as AdapterState.NotSynced).error is LedgerNotFoundException)
        coVerify(exactly = 0) { daemonSelector.selectDaemon() }
        coVerify(exactly = 0) { gateway.executeUnattended<Any?>(any(), any()) }
        coVerify(exactly = 0) { gateway.executeYielding<Any?>(any(), any()) }
        confirmVerified(appAccess)
    }

    @Test
    fun start_ledgerPresent_selectsDaemon() = runTest {
        val daemonSelector = mockk<MoneroDaemonSelector>(relaxed = true)
        val wrapper = createWrapper(
            mockService(),
            ledgerAccount,
            gateway = ledgerGateway(mutableListOf()),
            daemonSelector = daemonSelector,
        )

        withWalletRecord { runCatching { wrapper.start() } }

        coVerify(exactly = 1) { daemonSelector.selectDaemon() }
    }

    @Test
    fun start_ledgerOnline_awaitsViewKeyExportOnlyWhileWalletLoads() = assertViewKeyExportAwaitedOnlyWhileLoading(false)

    @Test
    fun start_ledgerOffline_awaitsViewKeyExportOnlyWhileWalletLoads() = assertViewKeyExportAwaitedOnlyWhileLoading(true)

    @Test
    fun start_ledgerWalletLoadFails_stopsAwaitingViewKeyExport() = runTest {
        val userOperations = LedgerUserOperations()
        val scan = LedgerScan(mutableListOf())
        val wrapper = closedLedgerWrapper(scan, userOperations = userOperations)
        every { scan.service.startPaused(any(), any()) } throws IllegalStateException("wallet load failed")

        withWalletRecord { runCatching { wrapper.start() } }

        assertFalse(userOperations.awaitingViewKeyExport.first())
    }

    @Test
    fun start_ledgerAccount_returnsAfterOpenBeforeInitialScanCompletes() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val deviceFree = CompletableDeferred<Unit>()
        val gateway = ledgerGateway(mutableListOf())
        coEvery { gateway.executeYielding<Any?>(ledgerAccount, any()) } coAnswers {
            deviceFree.await()
            secondArg<(String) -> Any?>()("wallet-key")
        }
        val wrapper = closedLedgerWrapper(scan, gateway)

        withWalletRecord { wrapper.start() }

        assertTrue(privateField(wrapper, "isStarted").getBoolean(wrapper))
        assertEquals(emptyList<String>(), events)
        deviceFree.complete(Unit)
        awaitProxySync(wrapper)
        assertEquals(listOf("refresh@70", "store@100"), events)
    }

    @Test
    fun moneroScanReachedDaemon_nodeStillSyncing_isFalse() {
        assertFalse(moneroScanReachedDaemon(walletHeight = 100, daemonHeight = 100, daemonTargetHeight = 200))
    }

    @Test
    fun moneroScanReachedDaemon_walletBehindNode_isFalse() {
        assertFalse(moneroScanReachedDaemon(walletHeight = 90, daemonHeight = 100, daemonTargetHeight = 100))
    }

    @Test
    fun moneroScanReachedDaemon_nodeUnreachable_isFalse() {
        assertFalse(moneroScanReachedDaemon(walletHeight = 0, daemonHeight = 0, daemonTargetHeight = 0))
    }

    @Test
    fun moneroScanReachedDaemon_walletAtNodeTip_isTrue() {
        assertTrue(moneroScanReachedDaemon(walletHeight = 100, daemonHeight = 100, daemonTargetHeight = 100))
    }

    @Test
    fun startAccountService_appBackgroundedWhileQueuedForDevice_defersWithoutDeviceIo() = runTest {
        val service = mockService()
        val device = MoneroLedgerStub()
        val session = LedgerSession(device.provider)
        var inForeground = true
        val userOperations = LedgerUserOperations()
        val gateway = realLedgerGateway(
            LedgerAppAccess(session, moneroFetcher(), storedMoneroKey(), userOperations),
            userOperations,
            mockk { every { this@mockk.inForeground } answers { inForeground } },
        )
        val wrapper = createWrapper(
            service,
            ledgerAccount,
            gateway = gateway,
            userOperations = userOperations,
            ledgerSessions = gateway,
        )
        invokeActivateReconciliationSession(wrapper)
        val otherOperationMayFinish = CompletableDeferred<Unit>()
        launch { session.withApp(LEDGER_MONERO_APP, "0.0.0") { otherOperationMayFinish.await() } }
        val start = async {
            runCatching { wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true) }
        }
        advanceUntilIdle()

        inForeground = false
        otherOperationMayFinish.complete(Unit)

        assertTrue(start.await().exceptionOrNull() is LedgerSessionDeferredException)
        assertEquals(1, device.exchanges)
        verify(exactly = 0) { service.startPaused(any(), any()) }
    }

    @Test
    fun onDeviceAttached_syncedLedgerWallet_syncsThroughDevice() = runTest {
        val events = mutableListOf<String>()
        val wrapper = syncingLedgerWrapper(events)
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.onDeviceAttached()

        assertEquals(listOf("yielding_enter", "sync", "gateway_exit"), events)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun onDeviceAttached_ledgerWalletNotSynced_syncsThroughDevice() = runTest {
        val events = mutableListOf<String>()
        val wrapper = syncingLedgerWrapper(events)
        setSyncState(wrapper, AdapterState.NotSynced(LedgerNotFoundException()))

        wrapper.onDeviceAttached()

        assertEquals(listOf("yielding_enter", "sync", "gateway_exit"), events)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun onDeviceAttached_openFailedWithoutDevice_retriesOpen() = runTest {
        val missing = LedgerNotFoundException()
        // A real service: stubbing `wallet` on a mock instantiates Wallet, whose static init loads the native lib.
        val wrapper = createWrapper(MoneroWalletService(File("unused")), ledgerAccount)
        setSyncState(wrapper, AdapterState.NotSynced(missing))

        wrapper.onDeviceAttached()
        awaitProxySync(wrapper)

        assertNotSame(missing, (wrapper.syncState.value as AdapterState.NotSynced).error)
    }

    @Test
    fun onDeviceAttached_startedLedgerWalletOtherAppOpen_syncsWithoutAppSwitchKeepingState() = runTest {
        var switchForbidden: Boolean? = null
        val gateway = mockk<MoneroHardwareGateway> {
            coEvery { executeYielding<Any?>(ledgerAccount, any()) } coAnswers {
                switchForbidden = currentCoroutineContext()[LedgerAppSwitchForbidden.Key] != null
                throw LedgerSessionDeferredException()
            }
        }
        val wrapper = startedLedgerWrapper(gateway)
        val failed = AdapterState.NotSynced(LedgerNotFoundException())
        setSyncState(wrapper, failed)

        wrapper.onDeviceAttached()

        assertEquals(true, switchForbidden)
        assertSame(failed, wrapper.syncState.value)
    }

    @Test
    fun onDeviceAttached_openFailedWithoutDevice_retriesOpenAllowingAppSwitch() = runTest {
        var switchForbidden: Boolean? = null
        // The open stops at its first device-independent step; reaching the gateway needs the native wallet.
        val dao = mockk<MoneroFileDao> {
            coEvery { getAssociatedRecord(ledgerAccount.id) } coAnswers {
                switchForbidden = currentCoroutineContext()[LedgerAppSwitchForbidden.Key] != null
                null
            }
        }
        startKoin { modules(module { single { dao } }) }
        try {
            val wrapper = createWrapper(MoneroWalletService(File("unused")), ledgerAccount)
            setSyncState(wrapper, AdapterState.NotSynced(LedgerNotFoundException()))

            wrapper.onDeviceAttached()
            awaitProxySync(wrapper)
        } finally {
            stopKoin()
        }

        assertEquals(false, switchForbidden)
    }

    @Test
    fun onDeviceAttached_openRefusedOnDevice_leavesWalletClosed() = runTest {
        val gateway = mockk<MoneroHardwareGateway>(relaxed = true)
        val wrapper = createWrapper(mockService(), ledgerAccount, gateway = gateway)
        val refused = AdapterState.NotSynced(LedgerRejectedException())
        setSyncState(wrapper, refused)

        wrapper.onDeviceAttached()

        assertSame(refused, wrapper.syncState.value)
        coVerify(exactly = 0) { gateway.executeUnattended<Any?>(any(), any()) }
    }

    @Test
    fun startAccountService_ledgerNodeUnreachable_failsWithNetworkError() = runTest {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val wrapper = createWrapper(service, ledgerAccount, gateway = ledgerGateway(mutableListOf()))
        every { status.isOk } returns false
        every { status.hardwareWalletError } returns null
        every { status.connectionStatus } returns ConnectionStatus.ConnectionStatus_Disconnected
        every { service.startPaused("wallet", "password") } returns status
        invokeActivateReconciliationSession(wrapper)

        val thrown = runCatching {
            wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
        }.exceptionOrNull()

        assertSame(HardwareWalletErrorCode.Network, (thrown as HardwareWalletOperationException).error)
    }

    @Test
    fun onLedgerAppOpen_openLedgerWallet_syncsThroughDevice() = runTest {
        val events = mutableListOf<String>()
        val wrapper = syncingLedgerWrapper(events)
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.onLedgerAppOpen()

        assertEquals(listOf("yielding_enter", "sync", "gateway_exit"), events)
    }

    @Test
    fun onLedgerAppOpen_walletNotOpened_leavesDeviceAlone() = runTest {
        val gateway = mockk<MoneroHardwareGateway>(relaxed = true)
        val wrapper = createWrapper(mockService(), ledgerAccount, gateway = gateway)
        setSyncState(wrapper, AdapterState.NotSynced(LedgerRejectedException()))

        wrapper.onLedgerAppOpen()

        coVerify(exactly = 0) { gateway.executeUnattended<Any?>(any(), any()) }
        coVerify(exactly = 0) { gateway.executeYielding<Any?>(any(), any()) }
    }

    @Test
    fun onLedgerAppOpen_deferredToForeground_keepsSyncState() = runTest {
        val gateway = mockk<MoneroHardwareGateway> {
            coEvery { executeYielding<Any?>(ledgerAccount, any()) } throws LedgerSessionDeferredException()
        }
        val wrapper = createWrapper(mockService(), ledgerAccount, gateway = gateway)
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.onLedgerAppOpen()

        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun refresh_ledgerBusyAfterFailedSync_showsWaitingForLedgerUntilSynced() = runTest {
        val deviceFree = CompletableDeferred<Unit>()
        val wrapper = failedLedgerWrapper(busyLedgerGateway(deviceFree))

        val refresh = launch { wrapper.refresh() }
        runCurrent()

        assertEquals(AdapterState.Syncing(substatus = AdapterState.Substatus.WaitingForLedger), wrapper.syncState.value)
        deviceFree.complete(Unit)
        refresh.join()
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun refresh_ledgerSyncYieldsAfterFailedSync_showsWaitingForLedgerNotStaleError() = runTest {
        val wrapper = failedLedgerWrapper(ledgerGateway(mutableListOf()))
        every { wrapper.refreshProxyWalletLeaseOwned() } throws LedgerSessionYieldedException()

        wrapper.refresh()

        assertEquals(AdapterState.Syncing(substatus = AdapterState.Substatus.WaitingForLedger), wrapper.syncState.value)
    }

    @Test
    fun pause_ledgerSyncQueuedBehindDeviceOperation_cancelsQueuedSyncAndPausesAtOnce() = runTest {
        val wrapper = failedLedgerWrapper(busyLedgerGateway(CompletableDeferred()))
        val failed = wrapper.syncState.value
        wrapper.refresh()
        assertEquals(AdapterState.Syncing(substatus = AdapterState.Substatus.WaitingForLedger), wrapper.syncState.value)

        // Never returns if the queued sync keeps the lifecycle lock.
        wrapper.pause()

        assertTrue(privateField(wrapper, "isPaused").getBoolean(wrapper))
        assertSame(failed, wrapper.syncState.value)
        assertNull(proxySync(wrapper))
    }

    @Test
    fun stop_duringLedgerProxySync_pausesAtBlockStoresAndClosesWithoutWaitingForTip() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val wrapper = scanningLedgerWrapper(scan, userOperations = userOperations)
        lateinit var stopping: Job
        scan.atFirstBatch { stopping = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.stop() } }

        wrapper.refresh()
        stopping.join()

        assertEquals(listOf("refresh@70", "store@80", "close(save=true)"), events)
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun refresh_callerCancelledDuringLedgerProxySync_syncContinuesAndChannelNotCancelled() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val deviceFree = CompletableDeferred<Unit>()
        val wrapper = scanningLedgerWrapper(scan, busyLedgerGateway(deviceFree))

        val caller = launch { wrapper.refresh() }
        runCurrent()
        caller.cancelAndJoin()
        deviceFree.complete(Unit)
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@100"), events)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun refresh_stopPendingBeforeProxyRefresh_skipsWalletRefreshAndRestoresState() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        lateinit var wrapper: MoneroKitWrapper
        lateinit var stopping: Job
        val gateway = sessionEnteringGateway {
            stopping = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.stop() }
        }
        wrapper = scanningLedgerWrapper(scan, gateway)

        wrapper.refresh()
        stopping.join()

        assertEquals(listOf("close(save=true)"), events)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun requestCheckpoint_duringLedgerProxySync_storesAtBoundaryAndContinuesToTip() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        scan.atFirstBatch { launch(start = CoroutineStart.UNDISPATCHED) { wrapper.requestCheckpoint() } }

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@80", "refresh@80", "store@100"), events)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun stop_ledgerReopenWaitingForViewKeyConfirmation_cancelsOpenAndClosesWithoutStore() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val gateway = mockk<MoneroHardwareGateway> {
            coEvery { executeUnattended<Any?>(ledgerAccount, any()) } coAnswers { awaitCancellation() }
        }
        val wrapper = scanningLedgerWrapper(scan, gateway)
        privateField(wrapper, "isStarted").set(wrapper, false)
        // The open itself needs native node setup; what matters is the device session it waits in.
        coEvery { wrapper["startInternal"](any<Boolean>(), any<Boolean>()) } coAnswers {
            wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
        }
        wrapper.refresh()

        wrapper.stop()

        assertNull(proxySync(wrapper))
        assertEquals(emptyList<String>(), events)
        verify(exactly = 0) { scan.service.startPaused(any(), any()) }
    }

    @Test
    fun rescan_ledgerDuringRunningSync_returnsAtOnceAndNextPassSchedulesRescanFromNewHeight() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        ledgerBirthday()
        var returnedDuringScan = false
        scan.atFirstBatch {
            returnedDuringScan = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.rescan(60) }.isCompleted
        }

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertTrue(returnedDuringScan)
        assertEquals(
            listOf(
                "refresh@70", "store@80", "rescanFrom@60", "refresh@60", "store@100", "storeKeys@100", "refresh@100",
            ),
            events,
        )
    }

    @Test
    fun refresh_ledgerRescanStartedThenStopped_storesCacheThenKeysAndNextOpenSchedulesNoRescan() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        ledgerBirthday(60)
        lateinit var stopping: Job
        scan.atFirstBatch { stopping = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.stop() } }

        wrapper.refresh()
        stopping.join()

        reopenAndAssertRescanKept(wrapper, events)
    }

    @Test
    fun stop_ledgerRescanObservedThenSyncFailed_storesCacheThenKeysBeforeClose() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        ledgerBirthday(60)
        // The node drops after the first block: wallet2 fails while the device stays intact.
        scan.afterBlockObserved = {
            scan.afterBlockObserved = {}
            error("node unreachable")
        }

        wrapper.refresh()
        awaitProxySync(wrapper)
        wrapper.stop()

        reopenAndAssertRescanKept(wrapper, events)
    }

    @Test
    fun refresh_closedByManager_neverReopensWallet() = runTest {
        val scan = LedgerScan(mutableListOf())
        val wrapper = scanningLedgerWrapper(scan)

        wrapper.close()
        withWalletRecord {
            wrapper.refresh()
            awaitProxySync(wrapper)
        }

        verify(exactly = 0) { scan.service.startPaused(any(), any()) }
    }

    @Test
    fun refresh_ledgerSyncedWalletRescansBackToStoredTip_storesCacheThenKeys() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events, batches = 0)
        val settings = ledgerBirthday()
        val wrapper = scanningLedgerWrapper(scan)
        wrapper.refresh()
        settings.birthdayHeight = 60

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(
            listOf("refresh@100", "store@100", "rescanFrom@60", "refresh@60", "store@100", "storeKeys@100"),
            events,
        )
    }

    @Test
    fun refresh_ledgerRescanScheduledButNoBlockObserved_neverStoresKeys() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events).apply { daemonSynced = false }
        ledgerBirthday(60)
        lateinit var wrapper: MoneroKitWrapper
        wrapper = scanningLedgerWrapper(
            scan,
            sessionEnteringGateway { launch(start = CoroutineStart.UNDISPATCHED) { wrapper.requestCheckpoint() } },
        )

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(listOf("rescanFrom@60", "store@70", "refresh@70"), events)
    }

    @Test
    fun refresh_ledgerSecondRescanHeightDeferred_previousObservationDoesNotAuthorizeKeys() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val settings = ledgerBirthday(60)
        var checkpointInSession = false
        lateinit var wrapper: MoneroKitWrapper
        wrapper = scanningLedgerWrapper(
            scan,
            sessionEnteringGateway {
                if (checkpointInSession) launch(start = CoroutineStart.UNDISPATCHED) { wrapper.requestCheckpoint() }
            },
        )
        // The node falls behind mid-scan: the rescan from 60 has begun, but nothing is stored.
        scan.atFirstBatch { scan.daemonSynced = false }
        wrapper.refresh()
        settings.birthdayHeight = 50
        wrapper.refresh()
        settings.birthdayHeight = 60
        checkpointInSession = true
        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(
            listOf(
                "rescanFrom@60", "refresh@60", "rescanFrom@50", "refresh@100",
                "rescanFrom@60", "store@100", "refresh@100",
            ),
            events,
        )
    }

    @Test
    fun refresh_ledgerProxyScan_publishesBlocksRemainedOnSyncState() = runTest {
        val scan = LedgerScan(mutableListOf())
        // The node's height is still unknown when the scan starts.
        every { scan.service.daemonHeight } returns 0
        every { scan.wallet.daemonBlockChainHeight } returnsMany listOf(0L, 100L)
        val wrapper = scanningLedgerWrapper(scan)
        val states = mutableListOf<AdapterState>()
        scan.afterBlockObserved = { states += wrapper.syncState.value }

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(listOf(20L, 10L, null), states.map { (it as AdapterState.Syncing).blocksRemained })
    }

    @Test
    fun refresh_ledgerProxySyncRunning_secondTriggerRunsOnceAfterward() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        scan.atFirstBatch {
            launch(start = CoroutineStart.UNDISPATCHED) {
                wrapper.refresh()
                wrapper.onLedgerAppOpen()
            }
        }

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@100", "refresh@100"), events)
    }

    @Test
    fun pause_duringLedgerProxySync_preemptsAtBoundaryWithoutWaitingForTip() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val wrapper = scanningLedgerWrapper(scan, userOperations = userOperations)
        lateinit var pausing: Job
        scan.atFirstBatch { pausing = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.pause() } }

        wrapper.refresh()
        pausing.join()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@80", "pause"), events)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun createNewSubaddress_duringLedgerProxySync_preemptsAtBoundaryThenSyncsOn() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val subaddresses = spyk(FakeSubaddressSource())
        every { subaddresses.create() } answers {
            events += "subaddress"
            callOriginal()
        }
        val wrapper = scanningLedgerWrapper(scan, subaddresses = subaddresses)
        lateinit var creating: Job
        scan.atFirstBatch {
            creating = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.createNewSubaddress() }
        }

        wrapper.refresh()
        creating.join()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@80", "subaddress", "refresh@80", "store@100"), events)
    }

    @Test
    fun estimateFee_duringLedgerProxySync_preemptsAtBoundaryWithoutWaitingForTip() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        every { scan.wallet.estimateTransactionFee(any()) } answers {
            events += "fee"
            1L
        }
        lateinit var estimating: Job
        scan.atFirstBatch {
            estimating = launch(start = CoroutineStart.UNDISPATCHED) {
                wrapper.estimateFee(BigDecimal.ONE, "address", null)
            }
        }

        wrapper.refresh()
        estimating.join()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@80", "fee", "refresh@80", "store@100"), events)
    }

    @Test
    fun refresh_firstLedgerSyncYieldedAfterOpen_nextTriggerSyncsInsteadOfSkipping() = runTest {
        val events = mutableListOf<String>()
        val wrapper = syncingLedgerWrapper(events)
        // What a first sync put off right after the open leaves on screen.
        setSyncState(wrapper, AdapterState.Syncing(substatus = AdapterState.Substatus.ConfirmViewKeyExport))

        wrapper.refresh()

        assertEquals(listOf("yielding_enter", "sync", "gateway_exit"), events)
    }

    @Test
    fun preemptRerun_walletStopped_doesNotOpen() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan)
        // The reopen fails, so the wallet stays closed.
        coEvery { wrapper["startInternal"](any<Boolean>(), any<Boolean>()) } coAnswers { events += "open" }
        lateinit var releasing: Job
        scan.atFirstBatch {
            releasing = launch(start = CoroutineStart.UNDISPATCHED) {
                runCatching { wrapper.withNativeWalletReleased(localOnly = false) { events += "released" } }
            }
        }

        wrapper.refresh()
        releasing.join()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@80", "close(save=true)", "released", "open"), events)
    }

    @Test
    fun pause_attachTriggerWhileCancelledSyncDrains_coalescedNoSecondJobBeforePause() = runTest {
        val events = mutableListOf<String>()
        val sessions = mutableListOf<String>()
        val scan = LedgerScan(events)
        val wrapper = scanningLedgerWrapper(scan, ledgerGateway(sessions))
        lateinit var pausing: Job
        scan.atFirstBatch {
            pausing = launch(start = CoroutineStart.UNDISPATCHED) { wrapper.pause() }
            launch(start = CoroutineStart.UNDISPATCHED) { wrapper.onDeviceAttached() }
        }

        wrapper.refresh()
        pausing.join()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "store@80", "pause"), events)
        assertEquals(listOf("yielding_enter", "gateway_exit"), sessions)
    }

    @Test
    fun refresh_ledgerTransportFailsMidScan_closesWithoutStoreAndReopensOnAttach() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val link = LedgerLink()
        val lost = LedgerException.TransportClosed()
        val wrapper = scanningLedgerWrapper(scan, link.gateway, ledgerSessions = link.sessions)
        coEvery { wrapper["startInternal"](any<Boolean>(), any<Boolean>()) } coAnswers { events += "open" }
        scan.atFirstBatch {
            link.failure = lost
            error("wallet2 refresh failed")
        }

        wrapper.refresh()
        awaitProxySync(wrapper)
        val failed = wrapper.syncState.value
        wrapper.onDeviceAttached()
        awaitProxySync(wrapper)

        assertSame(lost, (failed as AdapterState.NotSynced).error)
        assertEquals(listOf("refresh@70", "close(save=false)", "open"), events)
    }

    @Test
    fun refresh_ledgerChannelFailureSwallowedByWallet2_neverStoresAndCloses() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val link = LedgerLink()
        val lost = LedgerException.TransportClosed()
        val wrapper = scanningLedgerWrapper(scan, link.gateway, ledgerSessions = link.sessions)
        scan.atFirstBatch { link.failure = lost }

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "close(save=false)"), events)
        assertSame(lost, (wrapper.syncState.value as AdapterState.NotSynced).error)
    }

    @Test
    fun stop_unsavedCloseAfterDeviceFailureFailed_retriesCloseStillWithoutStore() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val link = LedgerLink()
        val wrapper = scanningLedgerWrapper(scan, link.gateway, ledgerSessions = link.sessions)
        var closeAttempts = 0
        every { scan.service.stop(any()) } answers {
            events += "close(save=${firstArg<Boolean>()})"
            closeAttempts++ > 0
        }
        ledgerBirthday(60)
        scan.atFirstBatch { link.failure = LedgerException.TransportClosed() }

        wrapper.refresh()
        awaitProxySync(wrapper)
        wrapper.stop()

        assertEquals(listOf("rescanFrom@60", "refresh@60", "close(save=false)", "close(save=false)"), events)
    }

    @Test
    fun refresh_ledgerSwallowedFailureThenUserYield_treatedAsDeviceFailure() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val link = LedgerLink()
        val lost = LedgerException.TransportClosed()
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val wrapper = scanningLedgerWrapper(
            scan,
            link.gateway,
            userOperations = userOperations,
            ledgerSessions = link.sessions,
        )
        lateinit var userOperation: Job
        scan.atFirstBatch {
            link.failure = lost
            userOperation = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                userOperations.run { awaitCancellation() }
            }
        }

        wrapper.refresh()
        awaitProxySync(wrapper)
        userOperation.cancelAndJoin()

        assertEquals(listOf("refresh@70", "close(save=false)"), events)
        assertSame(lost, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun onDeviceAttached_duringFailedSyncWindDown_coalescedAndReopensAfterward() = runTest {
        val events = mutableListOf<String>()
        val scan = LedgerScan(events)
        val link = LedgerLink()
        val wrapper = scanningLedgerWrapper(scan, link.gateway, ledgerSessions = link.sessions)
        coEvery { wrapper["startInternal"](any<Boolean>(), any<Boolean>()) } coAnswers { events += "open" }
        scan.atFirstBatch {
            link.failure = LedgerException.TransportClosed()
            launch(start = CoroutineStart.UNDISPATCHED) { wrapper.onDeviceAttached() }
            error("wallet2 refresh failed")
        }

        wrapper.refresh()
        awaitProxySync(wrapper)

        assertEquals(listOf("refresh@70", "close(save=false)", "open"), events)
    }

    @Test
    fun resume_afterTransportFailureInBackground_reopensWallet() = runTest {
        val events = mutableListOf<String>()
        val wrapper = scanningLedgerWrapper(LedgerScan(events))
        coEvery { wrapper["startInternal"](any<Boolean>(), any<Boolean>()) } coAnswers { events += "open" }
        privateField(wrapper, "isStarted").set(wrapper, false)
        setSyncState(wrapper, AdapterState.NotSynced(LedgerException.TransportClosed()))

        assertTrue(wrapper.resume())
        awaitProxySync(wrapper)

        assertEquals(listOf("open"), events)
    }

    @Test
    fun refresh_ledgerSyncDeferredAfterFailedSync_restoresFailure() = runTest {
        val wrapper = failedLedgerWrapper(
            mockk { coEvery { executeYielding<Any?>(ledgerAccount, any()) } throws LedgerSessionDeferredException() },
        )
        val failed = wrapper.syncState.value

        wrapper.refresh()

        assertSame(failed, wrapper.syncState.value)
    }

    @Test
    fun getSubaddresses_ledgerSyncWaitingForDevice_returnsEarlierListWithoutWaiting() = runTest {
        val subaddresses = FakeSubaddressSource()
        val deviceFree = CompletableDeferred<Unit>()
        val wrapper = failedLedgerWrapper(busyLedgerGateway(deviceFree), subaddresses)
        val earlier = wrapper.getSubaddresses()
        // Only a native read outside the lock could see this one.
        subaddresses.create()

        val refresh = launch { wrapper.refresh() }
        runCurrent()
        val read = async { wrapper.getSubaddresses() }
        runCurrent()

        assertTrue(read.isCompleted)
        assertEquals(earlier, read.await())
        deviceFree.complete(Unit)
        refresh.join()
    }

    @Test
    fun getSubaddresses_ledgerSyncWaitingForDeviceNoEarlierList_waitsForSync() = runTest {
        val deviceFree = CompletableDeferred<Unit>()
        val wrapper = failedLedgerWrapper(busyLedgerGateway(deviceFree))

        val refresh = launch { wrapper.refresh() }
        runCurrent()
        val read = async { wrapper.getSubaddresses() }
        runCurrent()

        assertFalse(read.isCompleted)
        deviceFree.complete(Unit)
        refresh.join()
        assertEquals(FakeSubaddressSource().read(), read.await())
    }

    @Test
    fun getSubaddresses_ledgerOpenedFirstSyncFailedLaterSyncWaiting_returnsListFromOpen() = runTest {
        val subaddresses = FakeSubaddressSource()
        var deviceFree = CompletableDeferred(Unit)
        val gateway = mockk<MoneroHardwareGateway> {
            coEvery { executeUnattended<Any?>(ledgerAccount, any()) } coAnswers {
                deviceFree.await()
                enter(mutableListOf(), "unattended_enter", secondArg())
            }
            coEvery { executeYielding<Any?>(ledgerAccount, any()) } coAnswers {
                deviceFree.await()
                enter(mutableListOf(), "yielding_enter", secondArg())
            }
        }
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true) { every { isOk } returns true }
        every { service.startPaused("wallet", "password") } returns status
        val wrapper = spyk(createWrapper(service, ledgerAccount, gateway = gateway, subaddressSource = subaddresses))
        every { wrapper.refreshProxyWalletLeaseOwned() } throws
            HardwareWalletOperationException(HardwareWalletErrorCode.Disconnected, "unplugged")
        invokeActivateReconciliationSession(wrapper)
        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
        setStarted(wrapper)
        val opened = subaddresses.read()
        deviceFree = CompletableDeferred()

        val refresh = launch { wrapper.refresh() }
        runCurrent()
        val read = async { wrapper.getSubaddresses() }
        runCurrent()

        assertTrue(read.isCompleted)
        assertEquals(opened, read.await())
        refresh.cancelAndJoin()
    }

    @Test
    fun getSubaddresses_createdSubaddressThenSyncWaiting_endsWithNewAddress() = runTest {
        val deviceFree = CompletableDeferred<Unit>()
        val wrapper = failedLedgerWrapper(busyLedgerGateway(deviceFree), FakeSubaddressSource())
        wrapper.getSubaddresses()

        val created = wrapper.createNewSubaddress()
        val refresh = launch { wrapper.refresh() }
        runCurrent()
        val read = async { wrapper.getSubaddresses() }
        runCurrent()

        assertTrue(read.isCompleted)
        assertEquals(MoneroSubaddressInfo(index = 1, address = created, receivedAmount = 0), read.await().last())
        deviceFree.complete(Unit)
        refresh.join()
    }

    @Test
    fun send_ledgerWallet_createsAndSendsWhileDeviceSessionHeld() = runTest {
        val events = mutableListOf<String>()
        val wrapper = spyk(createWrapper(mockService(), ledgerAccount, gateway = ledgerGateway(events)))
        every { wrapper.prepareAndSendTransaction(BigDecimal.ONE, "address", null) } answers {
            events += "create_and_send"
            "tx-id"
        }
        setStarted(wrapper)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        val txId = wrapper.send(BigDecimal.ONE, "address", null)

        assertEquals("tx-id", txId)
        assertEquals(listOf("gateway_enter", "create_and_send", "gateway_exit"), events)
    }

    @Test
    fun send_ledgerWalletNotSynced_neverOpensDeviceSession() = runTest {
        val gateway = mockk<MoneroHardwareGateway>(relaxed = true)
        val wrapper = createWrapper(mockService(), ledgerAccount, gateway = gateway)
        setStarted(wrapper)

        assertTrue(runCatching { wrapper.send(BigDecimal.ONE, "address", null) }.isFailure)

        coVerify(exactly = 0) { gateway.execute<Any?>(any(), any()) }
    }

    @Test
    fun refresh_ledgerSyncYieldedAfterSynced_restoresSyncedAndReportsYield() = runTest {
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val wrapper = syncingLedgerWrapper(mutableListOf(), userOperations)
        val states = mutableListOf<AdapterState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { wrapper.syncState.collect { states += it } }
        setSyncState(wrapper, AdapterState.Synced)
        every { wrapper.refreshProxyWalletLeaseOwned() } answers {
            setSyncState(wrapper, AdapterState.Syncing(progress = 40.0))
            throw LedgerSessionYieldedException()
        }

        wrapper.refresh()
        wrapper.refresh()

        assertEquals(AdapterState.Synced, wrapper.syncState.value)
        assertTrue(states.none { it is AdapterState.NotSynced })
        assertEquals(listOf(LEDGER_MONERO_APP, LEDGER_MONERO_APP), notices)
        verify(exactly = 2) { wrapper.refreshProxyWalletLeaseOwned() }
    }

    @Test
    fun startAccountService_ledgerSyncDeferredAfterOpen_startsConnecting() = runTest {
        assertFirstSyncPutOffStartsConnecting(LedgerSessionDeferredException(), expectedNotices = emptyList())
    }

    @Test
    fun startAccountService_ledgerSyncYieldedAfterOpen_startsConnectingAndReportsYield() = runTest {
        assertFirstSyncPutOffStartsConnecting(
            LedgerSessionYieldedException(),
            expectedNotices = listOf(LEDGER_MONERO_APP),
        )
    }

    private suspend fun TestScope.assertFirstSyncPutOffStartsConnecting(
        putOff: Exception,
        expectedNotices: List<String>,
    ) {
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val gateway = ledgerGateway(mutableListOf())
        coEvery { gateway.executeYielding<Any?>(ledgerAccount, any()) } throws putOff
        val service = mockService()
        every { service.startPaused("wallet", "password") } returns
            mockk(relaxed = true) { every { isOk } returns true }
        val wrapper = createWrapper(service, ledgerAccount, gateway = gateway, userOperations = userOperations)
        invokeActivateReconciliationSession(wrapper)

        assertTrue(wrapper.startAccountServiceOrOpenLocally("wallet", "password", fixIfCorruptedFile = true))
        setStarted(wrapper)
        wrapper.refresh()

        assertEquals(AdapterState.Connecting, wrapper.syncState.value)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertEquals(expectedNotices, notices)
    }

    @Test
    fun yieldToLedgerUser_blocksReportedDuringUserOperation_stopsRefreshOnEachBlock() = runTest {
        val userOperations = LedgerUserOperations()
        val service = mockService()
        val wrapper = createWrapper(service, ledgerAccount, userOperations = userOperations)
        val userOperationDone = CompletableDeferred<Unit>()
        var stops = 0

        reportBlocksDuringProxyRefresh(
            service,
            wrapper,
            onNewBlock = { wrapper.yieldToLedgerUser { stops++ } },
        ) { reportBlock ->
            reportBlock()
            launch(UnconfinedTestDispatcher(testScheduler)) { userOperations.run { userOperationDone.await() } }
            reportBlock()
            reportBlock()
        }
        userOperationDone.complete(Unit)

        assertEquals(2, stops)
        assertFalse(userOperations.isActive)
        assertTrue(wrapper.incompleteProxySyncError() is LedgerSessionYieldedException)
    }

    @Test
    fun yieldToLedgerUser_blocksAfterUserOperationEnded_stopNoMoreAndSyncStaysYielded() = runTest {
        val userOperations = LedgerUserOperations()
        val service = mockService()
        val wrapper = createWrapper(service, ledgerAccount, userOperations = userOperations)
        val userOperationDone = CompletableDeferred<Unit>()
        var stops = 0

        reportBlocksDuringProxyRefresh(
            service,
            wrapper,
            onNewBlock = { wrapper.yieldToLedgerUser { stops++ } },
        ) { reportBlock ->
            launch(UnconfinedTestDispatcher(testScheduler)) { userOperations.run { userOperationDone.await() } }
            reportBlock()
            reportBlock()
            userOperationDone.complete(Unit)
            assertFalse(userOperations.isActive)
            reportBlock()
            reportBlock()
        }

        assertEquals(2, stops)
        assertTrue(wrapper.incompleteProxySyncError() is LedgerSessionYieldedException)
    }

    @Test
    fun yieldToLedgerUser_noUserOperation_neverStopsRefreshAndPartialSyncIsNodeError() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, ledgerAccount)
        var stops = 0

        reportBlocksDuringProxyRefresh(
            service,
            wrapper,
            onNewBlock = { wrapper.yieldToLedgerUser { stops++ } },
        ) { reportBlock ->
            reportBlock()
            reportBlock()
        }

        assertEquals(0, stops)
        val error = wrapper.incompleteProxySyncError() as HardwareWalletOperationException
        assertSame(HardwareWalletErrorCode.Network, error.error)
    }

    private fun reportBlocksDuringProxyRefresh(
        service: MoneroWalletService,
        wrapper: MoneroKitWrapper,
        onNewBlock: () -> Unit,
        refresh: (reportBlock: () -> Unit) -> Unit,
    ) {
        var blockObserver: ((Long) -> Unit)? = null
        every { service.setControlledRefreshProgressObserver(any()) } answers { blockObserver = firstArg() }
        invokeActivateReconciliationSession(wrapper)
        wrapper.refreshHardwareKeyImagesWithProgress(1_000, { 1_000 }, onNewBlock) {
            refresh { blockObserver?.invoke(500) }
        }
    }

    @Test
    fun send_ledgerWalletWaitingForSync_isUserOperationAndSyncYields() = runTest {
        assertLedgerOperationWaitingForSyncIsUserOperation { wrapper ->
            setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
            every { wrapper.prepareAndSendTransaction(BigDecimal.ONE, "address", null) } returns "tx-id"
            wrapper.send(BigDecimal.ONE, "address", null)
        }
    }

    @Test
    fun displayAddressOnDevice_ledgerWalletWaitingForSync_isUserOperationAndSyncYields() = runTest {
        assertLedgerOperationWaitingForSyncIsUserOperation { wrapper -> wrapper.displayAddressOnDevice(1) }
    }

    private suspend fun TestScope.assertLedgerOperationWaitingForSyncIsUserOperation(
        operation: suspend (MoneroKitWrapper) -> Unit,
    ) {
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val deviceFree = CompletableDeferred<Unit>()
        val gateway = busyLedgerGateway(deviceFree)
        coEvery { gateway.execute<Any?>(ledgerAccount, any()) } coAnswers
            { enter(mutableListOf(), "enter", secondArg()) }
        // A real service without a wallet: display stops there, only waiting for the lock matters here.
        val wrapper = spyk(
            createWrapper(
                MoneroWalletService(File("unused")),
                ledgerAccount,
                gateway = gateway,
                userOperations = userOperations,
            ),
        )
        // Stands in for the in-thread stop of the real refresh.
        every { wrapper.refreshProxyWalletLeaseOwned() } answers {
            if (userOperations.isActive) throw LedgerSessionYieldedException()
            100L
        }
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        val sync = launch { wrapper.refresh() }
        runCurrent()

        val userOperation = launch { runCatching { operation(wrapper) } }
        runCurrent()

        assertTrue(userOperations.isActive)
        assertFalse(userOperation.isCompleted)
        deviceFree.complete(Unit)
        sync.join()
        userOperation.join()
        assertFalse(userOperations.isActive)
        assertEquals(listOf(LEDGER_MONERO_APP), notices)
        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun send_trezorWalletWaitingForLifecycle_isNotLedgerUserOperation() = runTest {
        val userOperations = LedgerUserOperations()
        val wrapper = createWrapper(mockService(), trezorAccount, userOperations = userOperations)
        val lifecycleMutex = privateField(wrapper, "lifecycleMutex").get(wrapper) as Mutex
        lifecycleMutex.lock()

        val send = launch { runCatching { wrapper.send(BigDecimal.ONE, "address", null) } }
        runCurrent()

        assertFalse(send.isCompleted)
        assertFalse(userOperations.isActive)
        lifecycleMutex.unlock()
        send.join()
    }

    @Test
    fun normalizedLiveRefreshState_durableRecoveryObligationsAreNeverDowngraded() {
        assertSame(
            MoneroSpentReconciliationState.MigrationReplayPending,
            MoneroSpentReconciliationState.MigrationReplayPending.normalizedLiveRefreshState(),
        )
        assertSame(
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending.normalizedLiveRefreshState(),
        )
    }

    @Test
    fun explicitColdRecovery_doesNotRequireControlledLiveRefreshFinalization() {
        assertFalse(
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending
                .requiresControlledRefreshFinalization(),
        )
        assertTrue(
            MoneroSpentReconciliationState.LiveRefreshPending
                .requiresControlledRefreshFinalization(),
        )
        assertTrue(
            MoneroSpentReconciliationState.MigrationReplayPending
                .requiresControlledRefreshFinalization(),
        )
    }

    @Test
    fun reconciliationFailure_remainsActionableForTrezorPreparation() {
        assertTrue(MoneroSpendReadiness.ReconciliationFailed.requiresTrezorPreparation())
        assertTrue(MoneroSpendReadiness.NeedsKeyImageSync.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Syncing.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.CheckingKeyImages.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.ReconcilingSpentStatus.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Ready.requiresTrezorPreparation())
    }

    @Test
    fun handleStartFailure_explicitColdRecovery_clearsSessionMarkerBeforeRetry() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        setExplicitColdRecoveryPending(wrapper, true)

        runCatching {
            invokeHandleStartFailure(wrapper, IllegalStateException("cold recovery failed"))
        }

        assertFalse(explicitColdRecoveryPending(wrapper))
    }

    @Test
    fun saveSynced_syncingAfterQueuedSyncedEvent_doesNotStoreOrClearRescan() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Syncing())

        val stored = wrapper.saveSynced()

        assertFalse(stored)
        verify(exactly = 0) { service.pause() }
        verify(exactly = 0) {
            restoreSettingsManager.clearPendingMoneroRescan(account)
        }
    }

    @Test
    fun abandonFaultedWallet_readyHardwareWallet_invalidatesStateAndOwnership() {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        val failure = HardwareWalletOperationException(
            HardwareWalletErrorCode.StoreFailed,
            "store failed",
        )
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        invokeAbandonFaultedWallet(wrapper, failure)

        verify(exactly = 1) { service.abandonFaultedWallet() }
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun startFailure_hardwareCleanupFailureRetainsNativeOwnershipButFailsClosed() {
        val wrapper = spyk(createWrapper(mockService(), trezorAccount), recordPrivateCalls = true)
        val failure = IllegalStateException("start failed")
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns false
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        val thrown = runCatching { invokeHandleStartFailure(wrapper, failure) }.exceptionOrNull()

        assertSame(failure, (thrown as InvocationTargetException).targetException)
        assertTrue(privateField(wrapper, "isStarted").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isPaused").getBoolean(wrapper))
        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
    }

    @Test
    fun startFailure_hardwareGatewayPreflightFailure_exposesRetryableTerminalReadiness() {
        val failure = IllegalStateException("device unavailable")
        val wrapper = spyk(createWrapper(mockService(), trezorAccount), recordPrivateCalls = true)
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns true

        runCatching { invokeHandleStartFailure(wrapper, failure) }

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
    }

    @Test
    fun stop_readyHardwareWallet_invalidatesReadinessBeforeItCanReopen() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every { service.stop(true) } returns true
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        wrapper.stop()

        verify(exactly = 1) { service.stop(true) }
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun send_pendingHardwareReconciliation_failsBeforePauseGatewayOrPreparation() = runTest {
        val service = mockService()
        val gateway = mockk<MoneroHardwareGateway>(relaxed = true)
        val wrapper = createWrapper(service, trezorAccount, gateway = gateway)
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.LiveRefreshPending
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertTrue(runCatching { wrapper.send(BigDecimal.ONE, "address", null) }.isFailure)

        verify(exactly = 0) { service.pause() }
        verify(exactly = 0) { service.prepareTransaction(any()) }
        coVerify(exactly = 0) { gateway.execute<Any?>(trezorAccount, any()) }
    }

    @Test
    fun submitSignedRawTransaction_pendingHardwareReconciliation_neverSubmitsOrLoadsWallet() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every {
            restoreSettingsManager.moneroSpentReconciliationState(trezorAccount)
        } returns MoneroSpentReconciliationState.LiveRefreshPending
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.ReconcilingSpentStatus)

        val failure = try {
            wrapper.submitSignedRawTransaction(byteArrayOf(1, 2, 3))
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertTrue(failure != null)
        coVerify(exactly = 0) { service.submitSignedRawTransaction(any()) }
    }

    @Test
    fun completedSubmittedRawTransaction_resumeFailureFailsClosedButPreservesResult() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        val result = RawMoneroBroadcastResult.Submitted("tx-id")
        val failure = IllegalStateException("resume failed")
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
        val actual = wrapper.completeHardwareOperation(result, failure, true)
        assertSame(result, actual)
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
    }

    @Test
    fun stop_callbackDuringNativeClose_cannotRecreateReconciliationWork() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        var callbackAccepted: Boolean? = null
        every { service.stop(true) } answers {
            callbackAccepted = wrapper.onRefreshed(wallet = null, full = true)
            true
        }
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
        invokeActivateReconciliationSession(wrapper)

        wrapper.stop()

        assertFalse(callbackAccepted ?: true)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun reconciliationReadiness_legacyAndPendingNeverExposeReady() {
        assertFalse(
            canExposeMoneroSpendReady(
                hardwareWallet = true,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                durableState = MoneroSpentReconciliationState.LiveRefreshPending,
            ),
        )
        assertTrue(
            canExposeMoneroSpendReady(
                hardwareWallet = true,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                durableState = MoneroSpentReconciliationState.Ready,
            ),
        )
    }

    @Test
    fun reconciliationCallback_matchingIsGenerationBased() {
        assertFalse(isMatchingReconciliationCallback(4, 3))
        assertTrue(isMatchingReconciliationCallback(4, 4))
    }

    @Test
    fun walletStartedCallback_activeSessionIsAcceptedDuringItsStartOrAfterward() {
        assertTrue(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = false,
                startInProgress = true,
            ),
        )
        assertTrue(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = true,
                startInProgress = false,
            ),
        )
        assertFalse(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = false,
                startInProgress = false,
            ),
        )
        assertFalse(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = false,
                isStarted = false,
                startInProgress = true,
            ),
        )
    }

    @Test
    fun walletStartedSyncState_nativeConnectedBeforeServiceStatePublished_staysSyncingUntilHealthy() {
        assertTrue(
            walletStartedSyncState(
                healthyWalletHealth(
                    serviceConnectionIsConnected = false,
                    walletIsSynchronized = false,
                ),
            ) is AdapterState.Syncing,
        )
        assertSame(
            AdapterState.Synced,
            walletStartedSyncState(healthyWalletHealth()),
        )
    }

    @Test
    fun nativeHealthFailure_serviceStateLagIsTransientButNativeFailuresRemainErrors() {
        assertFalse(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(
                    serviceConnectionIsConnected = false,
                    walletIsSynchronized = false,
                ),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(callbackWalletIsCurrent = false),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(nativeStatusIsOk = false),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(nativeConnectionIsConnected = false),
            ),
        )
    }

    @Test
    fun reconciliationCallback_failedNativeStatusIsConsumedFailClosed() {
        val callbackIsSuccessful = canFinalizeSpentReconciliation(
            healthyWalletHealth(nativeStatusIsOk = false),
        )

        assertEquals(
            ReconciliationCallbackDisposition.FailClosed,
            reconciliationCallbackDisposition(
                awaitingGeneration = 4,
                callbackGeneration = 4,
                callbackIsSuccessful = callbackIsSuccessful,
            ),
        )
    }

    @Test
    fun reconciliationCallback_requiresSuccessfulNativeStatusConnectionAndSynchronization() {
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(nativeStatusIsOk = false),
            ),
        )
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(serviceConnectionIsConnected = false),
            ),
        )
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(walletIsSynchronized = false),
            ),
        )
        assertTrue(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(),
            ),
        )
    }

    @Test
    fun coldKeyImageSync_trustedAndUntrustedResultsTakeDifferentSafePaths() {
        assertEquals(
            ColdKeyImageSyncNextStep.TrustedReady,
            coldKeyImageSyncNextStep(
                spentStatusVerified = true,
                hasUnknownKeyImages = false,
            ),
        )
        assertEquals(
            ColdKeyImageSyncNextStep.PreserveKeyImagesRescan,
            coldKeyImageSyncNextStep(
                spentStatusVerified = false,
                hasUnknownKeyImages = false,
            ),
        )
    }

    @Test
    fun coldKeyImageSync_trustedResultWithUnknownKeyImagesRemainsFailClosed() {
        assertEquals(
            ColdKeyImageSyncNextStep.NeedsKeyImageSync,
            coldKeyImageSyncNextStep(
                spentStatusVerified = true,
                hasUnknownKeyImages = true,
            ),
        )
    }

    @Test
    fun trustedKeyImageSyncFinalization_onlyReadyCanAuthorizeSpending() {
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.Ready,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Synced,
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(nativeStatusIsOk = false),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Synced,
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Syncing(),
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(nativeStatusIsOk = false),
                hasUnknownKeyImages = true,
                syncState = AdapterState.Syncing(),
            ),
        )
    }

    @Test
    fun spentStatusRequestRetry_isWiredToTerminalFailure() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)

        wrapper.applySpentStatusRequestResult(session, MoneroSpentStatusRequestResult.Retry)

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
    }

    @Test
    fun spentStatusRequestNeedsKeyImageSync_isTerminalAndClearsOperation() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        assertTrue(reconciler.beginRecovery(session) != null)

        wrapper.applySpentStatusRequestResult(session, MoneroSpentStatusRequestResult.NeedsKeyImageSync)

        assertSame(MoneroSpendReadiness.NeedsKeyImageSync, wrapper.spendReadiness.value)
        assertFalse(reconciler.hasActiveOperation(session))
    }

    @Test
    fun terminalReadiness_clearsOnlyItsMatchingKeyImageSyncMarker() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)

        listOf(
            MoneroSpendReadiness.Ready,
            MoneroSpendReadiness.NeedsKeyImageSync,
            MoneroSpendReadiness.ReconciliationFailed,
        ).forEach { terminalReadiness ->
                setKeyImageSyncSession(wrapper, session)
                invokeSetSpendReadinessForSession(wrapper, session, terminalReadiness)

                assertSame(terminalReadiness, wrapper.spendReadiness.value)
                assertEquals(null, keyImageSyncSession(wrapper))
            }

        invokeActivateReconciliationSession(wrapper)
        val newerSession = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, newerSession)

        invokeSetSpendReadinessForSession(wrapper, session, MoneroSpendReadiness.Ready)

        assertEquals(newerSession, keyImageSyncSession(wrapper))
    }

    @Test
    fun postSyncReadinessFailure_isTerminalWhenKeyImageSyncWasBegun() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, session)

        invokeFailClosedAfterPostSyncError(wrapper, session, IllegalStateException("probe failed"))

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertEquals(null, keyImageSyncSession(wrapper))
    }

    @Test
    fun keyImageSync_preservingRescanProgress_keepsAwaitedReconciliationAlive() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        val session = checkNotNull(reconciler.activeSession())
        val generation = checkNotNull(reconciler.beginRequest(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))
        setKeyImageSyncSession(wrapper, session)
        setSyncState(wrapper, AdapterState.Syncing())

        assertTrue(
            wrapper.applyHardwareReadinessSnapshot(
                session = session,
                health = healthyWalletHealth(walletIsSynchronized = false),
                hasUnknownKeyImages = null,
            ),
        )

        assertSame(MoneroSpendReadiness.ReconcilingSpentStatus, wrapper.spendReadiness.value)
        assertTrue(reconciler.hasActiveOperation(session))
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
        assertEquals(session, keyImageSyncSession(wrapper))

        setSyncState(wrapper, AdapterState.Synced)
        assertFalse(
            wrapper.applyHardwareReadinessSnapshot(
                session = session,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
            ),
        )
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
    }

    @Test
    fun onRefreshed_staleWalletProvenanceLeavesCurrentSessionUntouched() {
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
            healthReader = fakeHealthReader(healthyWalletHealth(callbackWalletIsCurrent = false)),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        assertSame(AdapterState.Synced, wrapper.syncState.value)
        assertSame(MoneroSpendReadiness.Ready, wrapper.spendReadiness.value)
    }

    @Test
    fun onRefreshed_fullyHandledSoftwareWalletCallbackReturnsTrue() {
        val wrapper = createWrapper(
            service = mockService(),
            healthReader = fakeHealthReader(healthyWalletHealth()),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        assertTrue(wrapper.onRefreshed(wallet = null, full = true))
    }

    @Test
    fun refreshCallbackReturn_hardwareTerminalPathsAreHandledButRetryablePathsAreNot() {
        assertTrue(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.Ready,
            ),
        )
        assertTrue(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.NeedsKeyImageSync,
            ),
        )
        assertTrue(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.ReconciliationFailed,
            ),
        )
        assertFalse(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.ReconcilingSpentStatus,
            ),
        )
        assertFalse(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = true,
                spendReadiness = MoneroSpendReadiness.Ready,
            ),
        )
    }

    @Test
    fun reconciliationFailure_isTerminalFailClosedReadiness() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        val session = checkNotNull(reconciler.activeSession())

        invokeFailClosedAfterReconciliationError(wrapper, session, IllegalStateException("store failed"))

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertFalse(reconciler.hasActiveOperation(session))
        assertFalse(
            isHardwareSpendLifecycleReady(
                syncState = AdapterState.Synced,
                durableState = MoneroSpentReconciliationState.LiveRefreshPending,
                spendReadiness = wrapper.spendReadiness.value,
            ),
        )
    }

    @Test
    fun onRefreshed_awaitedConnectedNativeStatusFailure_remainsRetryable() {
        val service = mockService()
        val nativeStatusError = "rescan blockchain failed"
        var health = healthyWalletHealth(
            nativeStatusIsOk = false,
            nativeStatusError = nativeStatusError,
        )
        val wrapper = createWrapper(
            service = service,
            account = trezorAccount,
            healthReader = object : MoneroWalletHealthReader {
                override fun snapshot(callbackWallet: Wallet?) = health
            },
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        val session = checkNotNull(reconciler.activeSession())
        setKeyImageSyncSession(wrapper, session)
        val generation = checkNotNull(reconciler.beginRequest(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        assertTrue(wrapper.syncState.value is AdapterState.Syncing)
        assertSame(MoneroSpendReadiness.ReconcilingSpentStatus, wrapper.spendReadiness.value)
        assertTrue(reconciler.hasActiveOperation(session))
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
        assertEquals(session, keyImageSyncSession(wrapper))
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }

        health = healthyWalletHealth()
        assertSame(
            ReconciliationCallbackDisposition.Finalize,
            reconciler.callbackDisposition(
                session = session,
                generation = generation,
                callbackIsSuccessful = canFinalizeSpentReconciliation(health),
            ),
        )
    }

    @Test
    fun onRefreshed_unsynchronizedNativeStatusFailure_preservesNativeError() {
        val nativeStatusError = "rescan blockchain failed"
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeStatusIsOk = false,
                    walletIsSynchronized = false,
                    nativeStatusError = nativeStatusError,
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        wrapper.onRefreshed(wallet = null, full = true)

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertEquals(nativeStatusError, state?.error?.message)
    }

    @Test
    fun onRefreshed_connectedSynchronizedNativeStatusFailure_keepsSyncPresentationFailClosed() {
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeStatusIsOk = false,
                    nativeStatusError = "Device state not initialized",
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertEquals("Device state not initialized", state?.error?.message)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    @Test
    fun terminalReady_transientUnhealthyThenHealthyCallback_canRestoreDurableReady() {
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
        )
        every {
            restoreSettingsManager.moneroSpentReconciliationState(trezorAccount)
        } returns MoneroSpentReconciliationState.Ready
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, session)
        invokeSetSpendReadinessForSession(wrapper, session, MoneroSpendReadiness.Ready)
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.applyHardwareReadinessSnapshot(
            session,
            healthyWalletHealth(nativeStatusIsOk = false),
            hasUnknownKeyImages = null,
        )
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)

        setSyncState(wrapper, AdapterState.Synced)
        wrapper.applyHardwareReadinessSnapshot(
            session,
            healthyWalletHealth(),
            hasUnknownKeyImages = false,
        )

        assertSame(MoneroSpendReadiness.Ready, wrapper.spendReadiness.value)
    }

    @Test
    fun lifecycleActivationAndDeactivation_invalidateKeyImageSyncMarker() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, activeReconciliationSession(wrapper))

        invokeDeactivateReconciliationSession(wrapper)
        assertEquals(null, keyImageSyncSession(wrapper))

        invokeActivateReconciliationSession(wrapper)
        assertEquals(null, keyImageSyncSession(wrapper))
    }

    @Test
    fun onRefreshed_activeHardwareSessionWithNativeWrongVersion_failsClosedWithoutReadyPersistence() {
        val service = mockService()
        val wrapper = createWrapper(
            service = service,
            account = trezorAccount,
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeConnectionIsConnected = false,
                    nativeConnectionStatus = ConnectionStatus.ConnectionStatus_WrongVersion,
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    // recordNativeConnectionError is exercised directly (not through onRefreshed/statusInfo) because
    // those paths touch the native Wallet class (moneroWalletService.wallet), whose static
    // initializer loads libmonerujo and cannot run on the JVM. The helper has no such dependency.

    @Test
    fun recordNativeConnectionError_disconnectedRepeated_recordsOnce() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, "boom")
        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, "boom")

        verify(exactly = 1) { tracker.record(BlockchainType.Monero, account.id, any()) }
    }

    @Test
    fun recordNativeConnectionError_wrongVersion_recordsWithStatusName() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_WrongVersion, null)

        verify(exactly = 1) {
            tracker.record(
                BlockchainType.Monero,
                account.id,
                match { it.method == "ConnectionStatus_WrongVersion" }
            )
        }
    }

    @Test
    fun recordNativeConnectionError_connected_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Connected, null)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun recordNativeConnectionError_nullStatus_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, null, null)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun recordNativeConnectionError_trackerThrows_doesNotPropagate() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        every { tracker.record(any(), any(), any()) } throws RuntimeException("boom")
        val wrapper = createWrapper(mockService(), tracker = tracker)

        // Must not throw: the helper wraps recording in tryOrNull.
        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, null)
    }

    // The device is offline while the kit still reports ConnectionStatus_Connected: MoneroWalletService
    // derives that status from the local chain height and never contacts the daemon.

    @Test
    fun refreshedStatePath_deviceOfflineWalletReportsSynced_forcesNotSynced() {
        val wrapper = createWrapper(mockService(), connectivityManager = connectivity(connected = false))

        val state =
            invokeResolve(wrapper, nativeConnected = true, isSynchronized = true, currentHeight = 0L, totalHeight = 0L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun refreshedStatePath_deviceOfflineNativeConnected_forcesNotSynced() {
        val wrapper = createWrapper(mockService(), connectivityManager = connectivity(connected = false))

        val state =
            invokeResolve(wrapper, nativeConnected = true, isSynchronized = false, currentHeight = 0L, totalHeight = 0L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun refreshedStatePath_deviceOnlineNativeConnected_keepsSyncing() {
        val wrapper = createWrapper(mockService())

        val state = invokeResolve(
            wrapper, nativeConnected = true, isSynchronized = false, currentHeight = 50L, totalHeight = 100L
        )
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.Syncing)
    }

    @Test
    fun resolveSyncState_nativeDisconnected_returnsNotSynced() {
        val wrapper = createWrapper(mockService())

        val state = invokeResolve(
            wrapper, nativeConnected = false, isSynchronized = false, currentHeight = 0L, totalHeight = 0L
        )

        assertTrue(state is AdapterState.NotSynced)
    }

    @Test
    fun onNetworkLost_deviceStillReportsConnected_setsNotSynced() {
        val wrapper = createWrapper(mockService())
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.onNetworkLost()

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun appendNetworkErrors_afterRecord_mergesIntoStatus() {
        // Contract that MoneroKitWrapper.statusInfo() relies on: a recorded error surfaces as
        // "Recent Network Error ..." keys in the merged status map.
        val tracker = NetworkErrorTracker()
        tracker.record(
            BlockchainType.Monero,
            account.id,
            NetworkErrorInfo(
                source = "Monero",
                method = "ConnectionStatus_Disconnected",
                url = "",
                host = "",
                resolvedIps = emptyList(),
                throwable = IllegalStateException("Not connected"),
            )
        )

        val merged = tracker.appendNetworkErrors(mapOf("isStarted" to true), BlockchainType.Monero, account.id)

        assertTrue(merged.keys.any { it.startsWith("Recent Network Error") })
    }

    private fun ledgerGateway(events: MutableList<String>): MoneroHardwareGateway = mockk {
        coEvery { execute<Any?>(ledgerAccount, any()) } coAnswers { enter(events, "gateway_enter", secondArg()) }
        coEvery { executeUnattended<Any?>(ledgerAccount, any()) } coAnswers {
            enter(events, "unattended_enter", secondArg())
        }
        coEvery { executeYielding<Any?>(ledgerAccount, any()) } coAnswers {
            enter(events, "yielding_enter", secondArg())
        }
    }

    private fun enter(events: MutableList<String>, entry: String, block: (String) -> Any?): Any? {
        events += entry
        return try {
            block("wallet-key")
        } finally {
            events += "gateway_exit"
        }
    }

    private fun syncingLedgerWrapper(
        events: MutableList<String>,
        userOperations: LedgerUserOperations = LedgerUserOperations(),
    ): MoneroKitWrapper = startedLedgerWrapper(ledgerGateway(events), events, userOperations = userOperations)

    private fun startedLedgerWrapper(
        gateway: MoneroHardwareGateway,
        events: MutableList<String> = mutableListOf(),
        subaddresses: MoneroSubaddressSource = FakeSubaddressSource(),
        userOperations: LedgerUserOperations = LedgerUserOperations(),
    ): MoneroKitWrapper {
        val wrapper = spyk(
            createWrapper(
                mockService(),
                ledgerAccount,
                gateway = gateway,
                subaddressSource = subaddresses,
                userOperations = userOperations,
            ),
        )
        every { wrapper.refreshProxyWalletLeaseOwned() } answers {
            events += "sync"
            100L
        }
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        return wrapper
    }

    private fun busyLedgerGateway(deviceFree: CompletableDeferred<Unit>): MoneroHardwareGateway = mockk {
        coEvery { executeYielding<Any?>(ledgerAccount, any()) } coAnswers {
            deviceFree.await()
            enter(mutableListOf(), "yielding_enter", secondArg())
        }
    }

    private fun failedLedgerWrapper(
        gateway: MoneroHardwareGateway,
        subaddresses: MoneroSubaddressSource = FakeSubaddressSource(),
    ): MoneroKitWrapper = startedLedgerWrapper(gateway, subaddresses = subaddresses)
        .also { setSyncState(it, AdapterState.NotSynced(LedgerNotFoundException())) }

    private fun TestScope.collectNotices(userOperations: LedgerUserOperations): List<String> {
        val notices = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            userOperations.yieldedNotices.collect { notices += it }
        }
        return notices
    }

    private class FakeSubaddressSource : MoneroSubaddressSource {
        private val addresses =
            mutableListOf(MoneroSubaddressInfo(index = 0, address = "address-0", receivedAmount = 0))

        override fun read(): List<MoneroSubaddressInfo> = addresses.toList()

        override fun create(): String = "address-${addresses.size}".also {
            addresses += MoneroSubaddressInfo(index = addresses.size, address = it, receivedAmount = 0)
        }
    }

    private fun moneroFetcher(): LedgerAppKeyFetchers = mockk {
        every { fetcherFor(any()) } returns mockk { every { appName } returns LEDGER_MONERO_APP }
    }

    private fun storedMoneroKey(): IHardwarePublicKeyStorage = mockk {
        coEvery { getKey(any(), any(), any()) } returns mockk<HardwarePublicKey> {
            every { blockchainType } returns BlockchainType.Monero.uid
            every { tokenType } returns TokenType.Native
        }
    }

    /** Answers the app-name query as the Monero app. */
    private class MoneroLedgerStub {
        var exchanges = 0

        val provider = LedgerTransportProvider {
            object : LedgerTransport {
                override suspend fun exchange(apdu: ByteArray): ByteArray {
                    exchanges++
                    return byteArrayOf(1) + field(LEDGER_MONERO_APP) + field("2.0.0") + byteArrayOf(0x90.toByte(), 0)
                }

                override fun close() = Unit
            }
        }

        private fun field(value: String) = byteArrayOf(value.length.toByte()) + value.encodeToByteArray()
    }

    private fun realLedgerGateway(
        appAccess: LedgerAppAccess,
        userOperations: LedgerUserOperations,
        backgroundManager: IBackgroundManager = mockk { every { inForeground } returns true },
        devicePresent: Boolean = true,
    ) = MoneroLedgerOperationGateway(
        appAccess,
        backgroundManager,
        dispatcherProvider,
        userOperations,
        mockk { every { isDevicePresent } returns devicePresent },
    )

    private fun mockService(): MoneroWalletService {
        return mockk(relaxed = true)
    }

    private fun connectivity(connected: Boolean): IConnectivityManager = mockk {
        every { isConnected } returns MutableStateFlow(connected)
    }

    private fun createWrapper(
        service: MoneroWalletService,
        account: Account = this.account,
        tracker: NetworkErrorTracker = mockk(relaxed = true),
        connectivityManager: IConnectivityManager = connectivity(connected = true),
        gateway: MoneroHardwareGateway = mockk(relaxed = true),
        healthReader: MoneroWalletHealthReader = fakeHealthReader(healthyWalletHealth()),
        daemonSelector: MoneroDaemonSelector = mockk(relaxed = true),
        subaddressSource: MoneroSubaddressSource = FakeSubaddressSource(),
        userOperations: LedgerUserOperations = LedgerUserOperations(),
        ledgerSessions: MoneroLedgerOperationGateway = realLedgerGateway(mockk(), userOperations),
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = service,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = tracker,
            moneroHardwareGateway = gateway,
            connectivityManager = connectivityManager,
            ledgerUserOperations = userOperations,
            ledgerGateway = ledgerSessions,
            walletHealthReader = healthReader,
            daemonSelector = daemonSelector,
            subaddressSource = subaddressSource,
        )
    }

    private fun invokeResolve(
        wrapper: MoneroKitWrapper,
        nativeConnected: Boolean,
        isSynchronized: Boolean,
        currentHeight: Long,
        totalHeight: Long,
    ): AdapterState = MoneroKitWrapper::class.java.getDeclaredMethod(
        "resolveSyncState",
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
    ).apply { isAccessible = true }
        .invoke(wrapper, nativeConnected, isSynchronized, currentHeight, totalHeight) as AdapterState

    private fun invokePublish(wrapper: MoneroKitWrapper, state: AdapterState) {
        MoneroKitWrapper::class.java.getDeclaredMethod("publishSyncState", AdapterState::class.java)
            .apply { isAccessible = true }
            .invoke(wrapper, state)
    }

    private fun fakeHealthReader(health: MoneroWalletHealthSnapshot): MoneroWalletHealthReader =
        object : MoneroWalletHealthReader {
            override fun snapshot(callbackWallet: Wallet?) = health
        }

    private fun healthyWalletHealth(
        callbackWalletIsCurrent: Boolean = true,
        nativeStatusIsOk: Boolean = true,
        nativeConnectionIsConnected: Boolean = true,
        serviceConnectionIsConnected: Boolean = true,
        walletIsSynchronized: Boolean = true,
        nativeConnectionStatus: ConnectionStatus? = ConnectionStatus.ConnectionStatus_Connected,
        nativeStatusError: String? = null,
    ) = MoneroWalletHealthSnapshot(
        callbackWalletIsCurrent = callbackWalletIsCurrent,
        nativeStatusIsOk = nativeStatusIsOk,
        nativeConnectionIsConnected = nativeConnectionIsConnected,
        serviceConnectionIsConnected = serviceConnectionIsConnected,
        walletIsSynchronized = walletIsSynchronized,
        nativeConnectionStatus = nativeConnectionStatus,
        nativeStatusError = nativeStatusError,
    )

    private fun invokeRecord(
        wrapper: MoneroKitWrapper,
        status: ConnectionStatus?,
        errorString: String?
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "recordNativeConnectionError", ConnectionStatus::class.java, String::class.java
        ).apply { isAccessible = true }.invoke(wrapper, status, errorString)
    }

    private fun invokeAbandonFaultedWallet(
        wrapper: MoneroKitWrapper,
        failure: HardwareWalletOperationException,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "abandonFaultedWallet",
            HardwareWalletOperationException::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, failure)
    }

    private fun invokeHandleStartFailure(wrapper: MoneroKitWrapper, failure: Exception) {
        MoneroKitWrapper::class.java.getDeclaredMethod("handleStartFailure", Exception::class.java)
            .apply { isAccessible = true }
            .invoke(wrapper, failure)
    }

    private fun invokeAbortControlledHardwareWallet(wrapper: MoneroKitWrapper, error: Throwable) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "abortControlledHardwareWallet",
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, error)
    }

    private fun invokeActivateReconciliationSession(wrapper: MoneroKitWrapper) {
        MoneroKitWrapper::class.java.getDeclaredMethod("activateReconciliationSession")
            .apply { isAccessible = true }
            .invoke(wrapper)
    }

    private fun invokeDeactivateReconciliationSession(wrapper: MoneroKitWrapper) {
        MoneroKitWrapper::class.java.getDeclaredMethod("deactivateReconciliationSession")
            .apply { isAccessible = true }
            .invoke(wrapper)
    }

    private fun invokeSetSpendReadinessForSession(
        wrapper: MoneroKitWrapper,
        session: Long,
        readiness: MoneroSpendReadiness,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "setSpendReadinessForSession",
            Long::class.javaPrimitiveType,
            MoneroSpendReadiness::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, readiness)
    }

    private fun invokeFailClosedAfterReconciliationError(
        wrapper: MoneroKitWrapper,
        session: Long,
        error: Throwable,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "failClosedAfterReconciliationError",
            Long::class.javaPrimitiveType,
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, error)
    }

    private fun invokeFailClosedAfterPostSyncError(
        wrapper: MoneroKitWrapper,
        session: Long,
        error: Throwable,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "failClosedAfterPostSyncError",
            Long::class.javaPrimitiveType,
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, error)
    }

    private fun activeReconciliationSession(wrapper: MoneroKitWrapper): Long {
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        return checkNotNull(reconciler.activeSession())
    }

    private fun setKeyImageSyncSession(wrapper: MoneroKitWrapper, session: Long) {
        privateField(wrapper, "keyImageSyncSession").set(wrapper, session)
    }

    private fun keyImageSyncSession(wrapper: MoneroKitWrapper): Long? =
        privateField(wrapper, "keyImageSyncSession").get(wrapper) as Long?

    private fun setStarted(wrapper: MoneroKitWrapper) {
        privateField(wrapper, "isStarted").set(wrapper, true)
    }

    private fun setExplicitColdRecoveryPending(wrapper: MoneroKitWrapper, pending: Boolean) {
        privateField(wrapper, "explicitColdRecoveryPending").set(wrapper, pending)
    }

    private fun explicitColdRecoveryPending(wrapper: MoneroKitWrapper): Boolean =
        privateField(wrapper, "explicitColdRecoveryPending").getBoolean(wrapper)

    @Suppress("UNCHECKED_CAST")
    private fun setSyncState(wrapper: MoneroKitWrapper, state: AdapterState) {
        val syncState = privateField(wrapper, "_syncState")
            .get(wrapper) as MutableStateFlow<AdapterState>
        syncState.value = state
    }

    @Suppress("UNCHECKED_CAST")
    private fun setSpendReadiness(
        wrapper: MoneroKitWrapper,
        readiness: MoneroSpendReadiness,
    ) {
        val spendReadiness = privateField(wrapper, "_spendReadiness")
            .get(wrapper) as MutableStateFlow<MoneroSpendReadiness>
        spendReadiness.value = readiness
    }

    private fun testControlledRefreshOperation(
        refresh: suspend (ControlledHardwareRefreshAborter) -> Unit = {},
        finalize: suspend () -> Unit = {},
        abort: (Throwable) -> Unit = {},
    ): ControlledHardwareRefreshOperation = object : ControlledHardwareRefreshOperation {
        override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
            try {
                refresh(aborter)
                finalize()
            } catch (error: Throwable) {
                aborter.abort(error)
                throw error
            }
        }

        override fun abort(error: Throwable) {
            abort(error)
        }
    }

    private fun TestScope.controlledRefreshCoordinator(): ControlledHardwareRefreshCoordinator {
        val supervisor = SupervisorJob()
        controlledRefreshCoordinatorJobs += supervisor
        return ControlledHardwareRefreshCoordinator(
            CoroutineScope(coroutineContext.minusKey(Job) + supervisor),
        )
    }

    private fun coordinatorMutex(coordinator: ControlledHardwareRefreshCoordinator): Mutex =
        coordinator.javaClass.getDeclaredField("mutex").apply {
            isAccessible = true
        }.get(coordinator) as Mutex

    private fun scanningLedgerWrapper(
        scan: LedgerScan,
        gateway: MoneroHardwareGateway = ledgerGateway(mutableListOf()),
        subaddresses: MoneroSubaddressSource = FakeSubaddressSource(),
        userOperations: LedgerUserOperations = LedgerUserOperations(),
        ledgerSessions: MoneroLedgerOperationGateway = realLedgerGateway(mockk(), userOperations),
    ): MoneroKitWrapper = spyk(
        createWrapper(
            scan.service,
            ledgerAccount,
            gateway = gateway,
            subaddressSource = subaddresses,
            userOperations = userOperations,
            ledgerSessions = ledgerSessions,
        ),
        recordPrivateCalls = true,
    ).also {
        setStarted(it)
        invokeActivateReconciliationSession(it)
        setSyncState(it, AdapterState.Synced)
    }

    /** An open Ledger wallet [batches] batches behind the daemon tip; a pause lands at the end of the running batch. */
    private class LedgerScan(private val events: MutableList<String>, batches: Int = 3) {
        // A node behind the network: a pending rescan waits for it and no scan reaches its tip.
        var daemonSynced = true
        private var height = TIP - batches * BATCH
        private var restoreHeight = 0L
        private var keysRestoreHeight = 0L
        private var rescanPending = false
        private var paused = false
        private var observer: ((Long) -> Unit)? = null
        private var onBatch: () -> Unit = {}
        var afterBlockObserved: () -> Unit = {}
        val wallet = nativeWallet()
        val service = mockk<MoneroWalletService>(relaxed = true)

        init {
            every { wallet.blockChainHeight } answers { height }
            every { wallet.daemonBlockChainHeight } returns TIP
            every { wallet.daemonBlockChainTargetHeight } answers { if (daemonSynced) TIP else TIP + BATCH }
            every { wallet.restoreHeight } answers { restoreHeight }
            every { wallet.restoreHeight = any() } answers { restoreHeight = firstArg() }
            every { wallet.rescanBlockchainAsync() } answers {
                rescanPending = true
                events += "rescanFrom@$restoreHeight"
            }
            every { wallet.refresh() } answers { scan() }
            every { wallet.pauseRefreshAndDrain() } answers {
                paused = true
                true
            }
            every { wallet.storeSafe() } answers { stored("store") }
            every { wallet.storeWithKeysSafe() } answers {
                keysRestoreHeight = restoreHeight
                stored("storeKeys")
            }
            every { service.wallet } returns wallet
            every { service.daemonHeight } returns TIP
            every { service.setControlledRefreshProgressObserver(any()) } answers { observer = firstArg() }
            // A reopen reads the restore height from the keys file.
            every { service.stop(any()) } answers {
                events += "close(save=${firstArg<Boolean>()})"
                restoreHeight = keysRestoreHeight
                true
            }
            every { service.startPaused(any(), any()) } returns mockk(relaxed = true) { every { isOk } returns true }
            every { service.pause() } answers { events += "pause" }
        }

        // Runs on the refresh thread right after the first batch, before the block observer sees it.
        fun atFirstBatch(action: () -> Unit) {
            onBatch = {
                onBatch = {}
                action()
            }
        }

        // A new wallet2 refresh clears an earlier pause; a pending rescan runs only against a synced node.
        private fun scan(): Boolean {
            if (rescanPending && daemonSynced) {
                rescanPending = false
                height = restoreHeight
            }
            events += "refresh@$height"
            if (rescanPending) return true
            paused = false
            while (height < TIP && !paused) {
                height += BATCH
                onBatch()
                observer?.invoke(height)
                afterBlockObserved()
            }
            return true
        }

        private fun stored(kind: String): Int {
            events += "$kind@$height"
            return MONERO_STORE_OK
        }

        private companion object {
            const val TIP = 100L
            const val BATCH = 10L

            // MockK cannot stub native methods, so it mocks a subclass that overrides them with plain ones.
            // Wallet's static initializer loads the native library, which host tests do not have.
            fun nativeWallet(): Wallet {
                mockkStatic(MoneroNative::class)
                try {
                    every { MoneroNative.load() } just runs
                    val overridable = ByteBuddy()
                        .subclass(Wallet::class.java, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                        .method(isNative<MethodDescription>().and(isPublic()).and(not(isStatic())))
                        .intercept(StubMethod.INSTANCE)
                        .make()
                        .load(Wallet::class.java.classLoader, ClassLoadingStrategy.Default.WRAPPER)
                        .loaded
                    return mockkClass(overridable.kotlin, relaxed = true)
                } finally {
                    unmockkStatic(MoneroNative::class)
                }
            }
        }
    }

    private fun ledgerBirthday(height: Long? = null): RestoreSettings =
        RestoreSettings().apply { birthdayHeight = height }.also {
            every { restoreSettingsManager.settings(ledgerAccount, BlockchainType.Monero) } returns it
        }

    /** After a stop that persisted the rescan from 60 at 70: the reopened wallet resumes at 70 without a rescan. */
    private suspend fun reopenAndAssertRescanKept(wrapper: MoneroKitWrapper, events: List<String>) {
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        wrapper.refresh()
        awaitProxySync(wrapper)
        assertEquals(
            listOf(
                "rescanFrom@60", "refresh@60", "store@70", "storeKeys@70",
                "close(save=true)", "refresh@70", "store@100",
            ),
            events,
        )
    }

    private fun closedLedgerWrapper(
        scan: LedgerScan,
        gateway: MoneroHardwareGateway = ledgerGateway(mutableListOf()),
        userOperations: LedgerUserOperations = LedgerUserOperations(),
    ): MoneroKitWrapper = scanningLedgerWrapper(scan, gateway, userOperations = userOperations)
        .also { privateField(it, "isStarted").set(it, false) }

    /** The Ledger asks for the view-key export while the native wallet loads, online or offline alike. */
    private fun assertViewKeyExportAwaitedOnlyWhileLoading(localOnly: Boolean) = runTest {
        val userOperations = LedgerUserOperations()
        val scan = LedgerScan(mutableListOf())
        val wrapper = closedLedgerWrapper(scan, userOperations = userOperations)
        var awaitedWhileLoading = false
        val recordAwaiting = { awaitedWhileLoading = runBlocking { userOperations.awaitingViewKeyExport.first() } }
        every { scan.service.startPaused(any(), any()) } answers {
            recordAwaiting()
            mockk(relaxed = true) { every { isOk } returns true }
        }
        every { scan.service.startOffline(any(), any()) } answers {
            recordAwaiting()
            LocalOpenResult.Opened
        }

        withWalletRecord { wrapper.start(localOnly = localOnly) }

        assertTrue(awaitedWhileLoading)
        assertFalse(userOperations.awaitingViewKeyExport.first())
    }

    private fun sessionEnteringGateway(onEnter: () -> Unit): MoneroHardwareGateway = mockk {
        coEvery { executeYielding<Any?>(ledgerAccount, any()) } coAnswers {
            onEnter()
            secondArg<(String) -> Any?>()("wallet-key")
        }
    }

    private suspend fun <T> withWalletRecord(block: suspend () -> T): T {
        val dao = mockk<MoneroFileDao> {
            coEvery { getAssociatedRecord(ledgerAccount.id) } returns
                MoneroFileRecord(ledgerAccount.id, SecretString("wallet"), SecretString("password"))
        }
        startKoin { modules(module { single { dao } }) }
        try {
            return block()
        } finally {
            stopKoin()
        }
    }

    /** The Ledger as the sessions see it: intact until [failure] records what wallet2 may swallow. */
    private class LedgerLink {
        var failure: Exception? = null

        val sessions = mockk<MoneroLedgerOperationGateway> {
            every { isDevicePresent } returns true
            every { isSessionIntact } answers { failure == null }
            every { requireIntactSession() } answers { failure?.let { throw it } }
        }

        // Like the real gateway, a session whose device failed ends with that failure.
        val gateway = mockk<MoneroHardwareGateway> {
            coEvery { executeYielding<Any?>(any(), any()) } coAnswers {
                val result = try {
                    secondArg<(String) -> Any?>()("wallet-key")
                } catch (error: Exception) {
                    throw failure ?: error
                }
                failure?.let { throw it }
                result
            }
        }
    }

    private fun proxySync(wrapper: MoneroKitWrapper): Job? = privateField(wrapper, "proxySync").get(wrapper) as Job?

    // Triggers only start the Ledger sync: it runs, with any pass coalesced behind it, in the wrapper's own job.
    private suspend fun awaitProxySync(wrapper: MoneroKitWrapper) {
        while (true) proxySync(wrapper)?.join() ?: return
    }

    private fun privateField(wrapper: MoneroKitWrapper, name: String) =
        wrapper.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }

}
