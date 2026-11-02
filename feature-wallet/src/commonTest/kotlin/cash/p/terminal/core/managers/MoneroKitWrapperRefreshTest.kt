package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.requiresTrezorPreparation
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Wallet as WalletMember
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.entities.MoneroFileRecord
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.service.DaemonConnectResult
import com.m2049r.xmrwallet.service.LocalOpenResult
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.MoneroNative
import com.piratecash.monero.signer.ExternalSignerRegistration
import com.piratecash.monero.signer.HardwareKeyImageRefreshResult
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import kotlin.test.assertFailsWith
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
        val balancesReadDuringStop = recordBalanceReadsDuringStop(service, wrapper)
        setStarted(wrapper)

        wrapper.stop()

        assertEquals(listOf<Long?>(null), balancesReadDuringStop)
    }

    @Test
    fun getBalance_duringControlledRefreshAbort_returnsNull() {
        val service = closingWalletService()
        val wrapper = createWrapper(service, trezorAccount)
        val balancesReadDuringStop = recordBalanceReadsDuringStop(service, wrapper)
        setStarted(wrapper)

        invokeAbortControlledHardwareWallet(wrapper, IllegalStateException("device detached"))

        assertEquals(listOf<Long?>(null), balancesReadDuringStop)
    }

    @Test
    fun getBalance_duringFailedStartCleanup_returnsNull() {
        val service = closingWalletService()
        val wrapper = createWrapper(service, trezorAccount)
        val balancesReadDuringStop = recordBalanceReadsDuringStop(service, wrapper)
        setStarted(wrapper)

        runCatching { invokeHandleStartFailure(wrapper, IllegalStateException("start failed")) }

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
    fun refreshHardwareKeyImagesWithProgress_reportsNativeProgressPerBlockAndClearsObserver() {
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
            onNewBlock = { events += "block" },
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
        assertEquals(listOf("fallback", "register", "refresh", "block", "block", "clear"), events)
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
    fun rescan_hardwareGatewayWaiting_publishesSyncingAndIgnoresNativeCallback() = runTest {
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
    fun abortControlledHardwareWallet_readyWallet_marksReconciliationFailed() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        invokeAbortControlledHardwareWallet(wrapper, IllegalStateException("refresh failed"))

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
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
        assertFalse(privateField(wrapper, "isPaused").getBoolean(wrapper))
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
    fun reconciliationFailure_remainsActionableForTrezorPreparation() {
        assertTrue(MoneroSpendReadiness.ReconciliationFailed.requiresTrezorPreparation())
        assertTrue(MoneroSpendReadiness.NeedsKeyImageSync.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Syncing.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.CheckingKeyImages.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.ReconcilingSpentStatus.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Ready.requiresTrezorPreparation())
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
    fun stop_callbackDuringNativeClose_cannotRecreateReconciliationWork() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)
        var callbackAccepted: Boolean? = null
        every { service.stop(true) } answers {
            callbackAccepted = wrapper.onRefreshed(wallet = null, full = true)
            true
        }
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        invokeActivateReconciliationSession(wrapper)

        wrapper.stop()

        assertFalse(callbackAccepted ?: true)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
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
    fun onRefreshed_staleWalletProvenanceLeavesCurrentSessionUntouched() {
        val wrapper = createWrapper(
            service = mockService(),
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
    fun onRefreshed_unsynchronizedNativeStatusFailure_preservesNativeError() {
        val nativeStatusError = "rescan blockchain failed"
        val wrapper = createWrapper(
            service = mockService(),
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
    fun onRefreshed_connectedSynchronizedNativeStatusFailure_publishesNativeError() {
        val wrapper = createWrapper(
            service = mockService(),
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeStatusIsOk = false,
                    nativeStatusError = "Device state not initialized",
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        wrapper.onRefreshed(wallet = null, full = true)

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertEquals("Device state not initialized", state?.error?.message)
    }

    @Test
    fun onRefreshed_hardwareWallet_leavesStateToDevicePasses() {
        val wrapper = createWrapper(
            service = mockService(),
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
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertTrue(wrapper.onRefreshed(wallet = null, full = true))

        assertSame(AdapterState.Synced, wrapper.syncState.value)
        assertSame(MoneroSpendReadiness.Ready, wrapper.spendReadiness.value)
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

    @Test
    fun refresh_trezorPassReachesTip_publishesReadyWithoutNativeResume() = runTest {
        val trezor = TrezorWallet(durableState = MoneroSpentReconciliationState.LiveRefreshPending)
        trezor.results += passResult(TREZOR_TIP)

        trezor.wrapper.refresh()

        assertEquals(
            listOf(HardwareKeyImageRefreshResult.Request(CONTINUE, startProtocolEagerly = true, TREZOR_RESTORE)),
            trezor.requests,
        )
        assertEquals(
            listOf("execute", "store", "setSynchronized", "durable=Ready", "refreshHistory", "release"),
            trezor.events,
        )
        assertSame(AdapterState.Synced, trezor.wrapper.syncState.value)
        assertSame(MoneroSpendReadiness.Ready, trezor.wrapper.spendReadiness.value)
        verify(exactly = 0) { trezor.service.resume(any()) }
    }

    @Test
    fun syncUnattended_nodeBehind_showsErrorOnlyAfterFourPasses() = runTest {
        val trezor = TrezorWallet()
        trezor.daemonTargetHeight = TREZOR_TIP + 10
        repeat(4) { trezor.results += passResult(TREZOR_TIP) }

        repeat(3) {
            trezor.wrapper.syncUnattended()
            assertFalse(trezor.wrapper.syncState.value is AdapterState.NotSynced)
        }
        trezor.wrapper.syncUnattended()

        assertTrue(trezor.wrapper.syncState.value is AdapterState.NotSynced)
        assertSame(MoneroSpendReadiness.Syncing, trezor.wrapper.spendReadiness.value)
        assertTrue(trezor.requests.none { it.startProtocolEagerly })
        assertEquals(4, trezor.events.count { it == "executeYielding" })
        assertFalse("setSynchronized" in trezor.events)
    }

    @Test
    fun refresh_incompletePass_storesCheckpointAndNextPassContinues() = runTest {
        val trezor = TrezorWallet(durableState = MoneroSpentReconciliationState.LiveRefreshPending)
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)

        trezor.wrapper.refresh()

        assertTrue("store" in trezor.events)
        assertTrue(trezor.wrapper.syncState.value is AdapterState.Syncing)
        assertSame(MoneroSpendReadiness.Syncing, trezor.wrapper.spendReadiness.value)
        assertSame(MoneroSpentReconciliationState.LiveRefreshPending, trezor.durableState)

        trezor.wrapper.refresh()

        assertEquals(CONTINUE, trezor.requests[1].mode)
        assertSame(AdapterState.Synced, trezor.wrapper.syncState.value)
    }

    @Test
    fun refresh_unchangedCompletedPass_isNotStoredAgainButDetachedSuffixIs() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP)
        trezor.results += passResult(TREZOR_TIP)
        trezor.results += passResult(TREZOR_TIP, suffixDetached = true)

        repeat(3) { trezor.wrapper.refresh() }

        assertEquals(2, trezor.events.count { it == "store" })
    }

    @Test
    fun refresh_migrationReplayStoppedEarly_resetsAndKeepsLiveRefreshPending() = runTest {
        val trezor = TrezorWallet(durableState = MoneroSpentReconciliationState.MigrationReplayRequired)
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)

        trezor.wrapper.refresh()

        assertEquals(RESET, trezor.requests.single().mode)
        assertTrue("storeKeys" in trezor.events)
        assertSame(MoneroSpentReconciliationState.LiveRefreshPending, trezor.durableState)
        verifyOrder {
            restoreSettingsManager.savePendingMoneroRescan(trezorAccount, TREZOR_RESTORE)
            restoreSettingsManager.clearPendingMoneroRescan(trezorAccount)
        }
    }

    @Test
    fun syncKeyImages_readyWallet_rescansFromRestoreHeightUnderDevice() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP)

        trezor.wrapper.syncKeyImages()

        assertEquals(RESET, trezor.requests.single().mode)
        assertTrue(trezor.requests.single().startProtocolEagerly)
        assertTrue("storeKeys" in trezor.events)
        assertSame(MoneroSpentReconciliationState.Ready, trezor.durableState)
    }

    @Test
    fun syncUnattended_userOperationWaiting_stopsAtEveryBlockAndStoresBeforeRelease() = runTest {
        val trezor = TrezorWallet()
        trezor.userOperationWaiting = true
        trezor.onPass = { observer ->
            observer(TREZOR_TIP - 60)
            observer(TREZOR_TIP - 50)
        }
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)

        trezor.wrapper.syncUnattended()

        assertEquals(listOf("executeYielding", "stop", "stop", "store", "release"), trezor.events)
        assertSame(MoneroSpendReadiness.Syncing, trezor.wrapper.spendReadiness.value)
    }

    @Test
    fun refresh_duringUnattendedPass_endsItEarlyThenRunsAttendedPass() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)
        var attended: Job? = null
        trezor.onPass = { observer ->
            trezor.onPass = {}
            attended = launch(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.refresh() }
            observer(TREZOR_TIP - 50)
        }

        trezor.wrapper.syncUnattended()
        checkNotNull(attended).join()

        assertEquals(listOf(false, true), trezor.requests.map { it.startProtocolEagerly })
        assertEquals(
            listOf("executeYielding", "stop", "store", "release", "execute"),
            trezor.events.take(5),
        )
        assertSame(AdapterState.Synced, trezor.wrapper.syncState.value)
    }

    @Test
    fun syncUnattended_checkpointDue_storesAndContinuesInSameDeviceSession() = runTest {
        val time = TestTimeSource()
        val trezor = TrezorWallet(timeSource = time)
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)
        trezor.onPass = { observer ->
            trezor.onPass = {}
            time += 3.minutes
            observer(TREZOR_TIP - 50)
        }

        trezor.wrapper.syncUnattended()

        assertEquals(listOf(CONTINUE, CONTINUE), trezor.requests.map { it.mode })
        assertEquals(
            listOf("executeYielding", "stop", "store", "store", "setSynchronized", "refreshHistory", "release"),
            trezor.events,
        )
        assertSame(AdapterState.Synced, trezor.wrapper.syncState.value)
    }

    @Test
    fun syncUnattended_deviceDetachedDuringPass_endsAtNextBlockWithoutContinuing() = runTest {
        val time = TestTimeSource()
        val trezor = TrezorWallet(timeSource = time)
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)
        trezor.onPass = { observer ->
            trezor.wrapper.onTrezorDetached()
            time += 3.minutes
            observer(TREZOR_TIP - 50)
        }

        trezor.wrapper.syncUnattended()

        assertEquals(1, trezor.requests.size)
        assertEquals(listOf("executeYielding", "stop"), trezor.events.take(2))
        assertFalse("execute" in trezor.events)
        val error = (trezor.wrapper.syncState.value as AdapterState.NotSynced).error
        assertEquals(HardwareWalletErrorCode.DeviceNotFound, (error as HardwareWalletOperationException).error)
    }

    @Test
    fun syncUnattended_detachedWhileIdleThenReattached_runsWholePass() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP)
        trezor.onPass = { observer -> observer(TREZOR_TIP - 50) }
        trezor.wrapper.onTrezorDetached()

        trezor.wrapper.syncUnattended()

        assertFalse("stop" in trezor.events)
        assertSame(AdapterState.Synced, trezor.wrapper.syncState.value)
    }

    @Test
    fun syncUnattended_appBackgroundedDuringPass_endsAtNextBlockWithStore() = runTest {
        val time = TestTimeSource()
        val trezor = TrezorWallet(timeSource = time)
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)
        trezor.onPass = { observer ->
            trezor.inForeground = false
            time += 3.minutes
            observer(TREZOR_TIP - 50)
        }

        trezor.wrapper.syncUnattended()

        assertEquals(1, trezor.requests.size)
        assertEquals(listOf("executeYielding", "stop", "store", "release"), trezor.events)
        assertTrue(trezor.wrapper.syncState.value is AdapterState.Syncing)
    }

    @Test
    fun displayAddressOnDevice_duringUnattendedPass_endsPassAndRunsBeforeNextPass() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)
        var display: Job? = null
        trezor.onPass = { observer ->
            trezor.onPass = {}
            display = launch(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.displayAddressOnDevice(0) }
            observer(TREZOR_TIP - 50)
        }

        trezor.wrapper.syncUnattended()
        trezor.wrapper.syncUnattended()
        checkNotNull(display).join()

        assertEquals(
            listOf(
                "executeYielding", "stop", "store", "release",
                "execute", "showAddress", "release",
                "executeYielding",
            ),
            trezor.events.take(8),
        )
    }

    @Test
    fun rescan_duringUnattendedPass_endsPassAtNextBlock() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP - 50, completed = false)
        trezor.results += passResult(TREZOR_TIP)
        var rescan: Job? = null
        trezor.onPass = { observer ->
            trezor.onPass = {}
            rescan = launch(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.rescan(42) }
            observer(TREZOR_TIP - 50)
        }

        trezor.wrapper.syncUnattended()
        checkNotNull(rescan).join()

        assertEquals(listOf("executeYielding", "stop", "store", "release", "execute"), trezor.events.take(5))
    }

    @Test
    fun syncKeyImages_whileContinuePassCompletes_runsItsOwnResetPass() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP)
        trezor.results += passResult(TREZOR_TIP)
        var recovery: Job? = null
        trezor.onPass = {
            trezor.onPass = {}
            recovery = launch(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.syncKeyImages() }
        }

        trezor.wrapper.refresh()
        checkNotNull(recovery).join()

        assertEquals(listOf(CONTINUE, RESET), trezor.requests.map { it.mode })
        assertTrue("storeKeys" in trezor.events)
        assertTrue("durable=ExplicitColdRecoveryPending" in trezor.events)
        assertSame(MoneroSpentReconciliationState.Ready, trezor.durableState)
    }

    @Test
    fun refresh_attendedPassWithCheckedProtocol_marksLiveRefreshConfirmed() = runTest {
        val trezor = TrezorWallet()
        repeat(2) { trezor.results += passResult(TREZOR_TIP, protocolStarted = true) }

        trezor.wrapper.syncUnattended()
        assertFalse("confirmed" in trezor.events)
        trezor.wrapper.refresh()

        assertEquals(1, trezor.events.count { it == "confirmed" })
    }

    @Test
    fun refresh_deviceFailureDuringPass_closesWalletUnsavedAndNeedsPreparation() = runTest {
        val trezor = TrezorWallet()
        val failure = HardwareWalletOperationException(HardwareWalletErrorCode.Disconnected, "unplugged")
        trezor.passFailure = failure

        trezor.wrapper.refresh()

        assertTrue("close(save=false)" in trezor.events)
        assertFalse("store" in trezor.events)
        assertFalse(privateField(trezor.wrapper, "isStarted").getBoolean(trezor.wrapper))
        assertSame(failure, (trezor.wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.ReconciliationFailed, trezor.wrapper.spendReadiness.value)
    }

    @Test
    fun resumeNetwork_offlineAfterRestartWithEmptyCache_bringsWalletOnlineThroughAttendedPass() = runTest {
        withTrezorWalletFiles {
            val trezor = TrezorWallet()
            trezor.cacheConfirmed = false
            trezor.wrapper.pause()
            trezor.results += passResult(TREZOR_TIP, protocolStarted = true)

            assertTrue(trezor.wrapper.resumeNetwork())

            assertEquals(listOf("close(save=false)", "selectDaemon", "execute", "open"), trezor.events.take(4))
            assertTrue("confirmed" in trezor.events)
            assertTrue(trezor.wrapper.isNetworkOnline)
        }
    }

    @Test
    fun resumeNetwork_offlineRequestedWhileDeviceConfirms_neverGoesOnline() = runTest {
        withTrezorWalletFiles {
            val trezor = TrezorWallet()
            trezor.cacheConfirmed = false
            trezor.wrapper.pause()
            trezor.results += passResult(TREZOR_TIP, protocolStarted = true)
            val deviceConfirmed = CompletableDeferred<Unit>()
            trezor.holdAttendedSession = deviceConfirmed
            val moneroKitManager = mockk<MoneroKitManager>(relaxed = true) {
                every { currentAccount } returns trezorAccount
                every { moneroKitWrapper } returns trezor.wrapper
                coEvery { pauseNetwork(trezorAccount) } coAnswers { trezor.wrapper.pause() }
            }
            val controller = offlineNetworkController(moneroKitManager)
            val member = mockk<WalletMember> {
                every { account } returns trezorAccount
                every { token.blockchainType } returns BlockchainType.Monero
            }

            assertTrue(trezor.wrapper.resumeNetwork())
            val eventsBeforeOffline = trezor.events.size
            val offline = async(start = CoroutineStart.UNDISPATCHED) { controller.pause(member) }
            deviceConfirmed.complete(Unit)
            offline.await()

            val eventsAfterOffline = trezor.events.drop(eventsBeforeOffline)
            assertFalse("open" in eventsAfterOffline)
            assertFalse("selectDaemon" in eventsAfterOffline)
            assertTrue(trezor.requests.isEmpty())
            assertFalse(privateField(trezor.wrapper, "isStarted").getBoolean(trezor.wrapper))
            assertTrue(controller.isOffline(member))
        }
    }

    private fun offlineNetworkController(moneroKitManager: MoneroKitManager) = OfflineNetworkController(
        adapterManager = mockk(relaxed = true),
        evmBlockchainManager = mockk(relaxed = true),
        solanaKitManager = mockk(relaxed = true),
        tronKitManager = mockk(relaxed = true),
        tonKitManager = mockk(relaxed = true),
        stellarKitManager = mockk(relaxed = true),
        moneroKitManager = moneroKitManager,
        thorchainKitManagers = mockk(relaxed = true),
    )

    @Test
    fun refresh_queuedBehindUnattendedPassThenBackgroundedAndStopped_neitherOpensNorTouchesDevice() = runTest {
        withTrezorWalletFiles {
            val trezor = TrezorWallet()
            val unattendedHeld = CompletableDeferred<Unit>()
            trezor.holdYieldingSession = unattendedHeld
            trezor.results += passResult(TREZOR_TIP)
            val unattended = async(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.syncUnattended() }
            val attended = async(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.refresh() }
            trezor.inForeground = false
            val stop = async(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.stop() }

            unattendedHeld.complete(Unit)
            listOf(unattended, stop, attended).forEach { it.await() }

            assertEquals(listOf("executeYielding", "close(save=true)"), trezor.events.filterNot { it.isPassDetail() })
        }
    }

    @Test
    fun refresh_queuedBehindRelease_neitherOpensNorTouchesDevice() = runTest {
        withTrezorWalletFiles {
            val trezor = TrezorWallet()
            trezor.results += passResult(TREZOR_TIP)
            val lifecycleMutex = privateField(trezor.wrapper, "lifecycleMutex").get(trezor.wrapper) as Mutex
            lifecycleMutex.lock()
            val release = async(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.release() }
            val queuedPass = async(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.refresh() }
            lifecycleMutex.unlock()
            listOf(release, queuedPass).awaitAll()

            assertTrue(trezor.events.none { it in setOf("execute", "executeYielding", "open") })
            assertTrue(trezor.requests.isEmpty())
        }
    }

    @Test
    fun syncUnattended_queuedBeforeWaitingPause_runsNoDeviceWork() = runTest {
        val trezor = TrezorWallet()

        runQueuedBeforeWaitingPause(trezor) { trezor.wrapper.syncUnattended() }

        assertFalse("executeYielding" in trezor.events)
        assertTrue(trezor.requests.isEmpty())
    }

    @Test
    fun refresh_queuedBeforeWaitingPause_stillReachesDevice() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP)

        runQueuedBeforeWaitingPause(trezor) { trezor.wrapper.refresh() }

        assertTrue("execute" in trezor.events)
    }

    // The pass queues on the lifecycle lock first, then pause() starts waiting behind it.
    private suspend fun TestScope.runQueuedBeforeWaitingPause(trezor: TrezorWallet, pass: suspend () -> Unit) {
        val lifecycleMutex = privateField(trezor.wrapper, "lifecycleMutex").get(trezor.wrapper) as Mutex
        lifecycleMutex.lock()
        val queuedPass = async(start = CoroutineStart.UNDISPATCHED) { pass() }
        val pause = async(start = CoroutineStart.UNDISPATCHED) { trezor.wrapper.pause() }
        lifecycleMutex.unlock()
        listOf(queuedPass, pause).awaitAll()
    }

    @Test
    fun syncUnattended_inBackground_neitherOpensNorTouchesDevice() = runTest {
        val trezor = TrezorWallet()
        trezor.inForeground = false

        trezor.wrapper.syncUnattended()

        assertTrue(trezor.events.isEmpty())
        assertTrue(trezor.requests.isEmpty())
    }

    @Test
    fun syncAskingDevice_attendedPassFinishedSinceCue_doesNotAskAgain() = runTest {
        val trezor = TrezorWallet()
        trezor.cacheConfirmed = false
        val userAnswers = CompletableDeferred<Unit>()
        trezor.holdAttendedSession = userAnswers
        val foregroundCue = trezor.wrapper.attendedPassesFinished
        val foreground = launch { trezor.wrapper.askDevice(foregroundCue) }
        runCurrent()
        val attachCue = trezor.wrapper.attendedPassesFinished

        trezor.gatewayFailure = userRejected()
        userAnswers.complete(Unit)
        foreground.join()
        trezor.wrapper.syncAskingDevice(attachCue)

        assertEquals(1, trezor.attendedAttempts)
    }

    @Test
    fun syncAskingDevice_cueAfterFinishedAttendedPass_asksDevice() = runTest {
        val trezor = TrezorWallet()
        trezor.cacheConfirmed = false
        trezor.gatewayFailure = userRejected()
        trezor.wrapper.askDevice(trezor.wrapper.attendedPassesFinished)

        assertFailsWith<HardwareWalletOperationException> {
            trezor.wrapper.syncAskingDevice(trezor.wrapper.attendedPassesFinished)
        }

        assertEquals(2, trezor.attendedAttempts)
    }

    @Test
    fun refresh_failedStartRetainedWallet_runsNoPass() = runTest {
        val trezor = TrezorWallet()
        setStarted(trezor.wrapper)
        privateField(trezor.wrapper, "isPaused").set(trezor.wrapper, true)

        trezor.wrapper.refresh()

        assertTrue(trezor.requests.isEmpty())
        assertTrue(trezor.events.none { it.startsWith("execute") })
    }

    @Test
    fun refresh_networkPausedAndWalletClosed_runsNoPass() = runTest {
        withTrezorWalletFiles {
            val trezor = TrezorWallet()
            trezor.wrapper.pause()
            trezor.wrapper.stop()
            trezor.events.clear()

            trezor.wrapper.refresh()

            assertTrue(trezor.events.isEmpty())
            assertTrue(trezor.requests.isEmpty())
        }
    }

    @Test
    fun syncKeyImages_restoreHeightZero_resetsFromBlockOne() = runTest {
        val trezor = TrezorWallet(restoreHeight = 0)
        trezor.results += passResult(TREZOR_TIP)

        trezor.wrapper.syncKeyImages()

        assertEquals(RESET, trezor.requests.single().mode)
        assertEquals(1L, trezor.requests.single().restoreHeight)
        verify { restoreSettingsManager.savePendingMoneroRescan(trezorAccount, 1L) }
    }

    @Test
    fun syncUnattended_restoreHeightZeroNoPendingState_continues() = runTest {
        val trezor = TrezorWallet(restoreHeight = 0)
        trezor.results += passResult(TREZOR_TIP)

        trezor.wrapper.syncUnattended()

        assertEquals(CONTINUE, trezor.requests.single().mode)
    }

    @Test
    fun send_hardwareSendSucceeds_publishesHistoryWithoutDevicePass() = runTest {
        val trezor = TrezorWallet()
        trezor.results += passResult(TREZOR_TIP)
        trezor.wrapper.syncUnattended()
        every { trezor.service.sendTransaction(null) } returns "tx-hash"
        var updates = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            trezor.wrapper.transactionsStateUpdatedFlow.collect { updates++ }
        }
        trezor.events.clear()

        val txId = trezor.wrapper.send(BigDecimal.ONE, "address", null)

        assertEquals("tx-hash", txId)
        assertEquals(listOf("execute", "release", "refreshHistory"), trezor.events)
        assertEquals(1, updates)
    }

    @Test
    fun syncUnattended_sessionNotConfirmedOnDevice_keepsWalletOpenAndNeedsPreparation() = runTest {
        val trezor = TrezorWallet()
        val failure = HardwareWalletOperationException(
            HardwareWalletErrorCode.PendingDeviceVerification,
            "Trezor session must be confirmed on the device",
        )
        trezor.gatewayFailure = failure

        trezor.wrapper.syncUnattended()

        assertTrue(trezor.requests.isEmpty())
        assertFalse(trezor.events.any { it.startsWith("close") })
        assertTrue(privateField(trezor.wrapper, "isStarted").getBoolean(trezor.wrapper))
        assertSame(failure, (trezor.wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.ReconciliationFailed, trezor.wrapper.spendReadiness.value)
    }

    @Test
    fun controlledRefreshCoordinator_attendedDuringUnattended_waitsThenRunsOwnOperation() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val unattendedGate = CompletableDeferred<Unit>()
        var attendedRuns = 0
        val unattended = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(testControlledRefreshOperation(refresh = { unattendedGate.await() }))
        }
        val attended = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(testControlledRefreshOperation(attended = true, refresh = { attendedRuns += 1 }))
        }

        assertEquals(0, attendedRuns)
        unattendedGate.complete(Unit)
        unattended.await()
        attended.await()

        assertEquals(1, attendedRuns)
    }

    @Test
    fun controlledRefreshCoordinator_resetDuringNonResetRun_waitsThenRunsOwnOperation() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val continueGate = CompletableDeferred<Unit>()
        var resetRuns = 0
        val running = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(testControlledRefreshOperation(attended = true, refresh = { continueGate.await() }))
        }
        val reset = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(
                testControlledRefreshOperation(attended = true, resets = true, refresh = { resetRuns += 1 }),
            )
        }

        assertEquals(0, resetRuns)
        continueGate.complete(Unit)
        running.await()
        reset.await()

        assertEquals(1, resetRuns)
    }

    @Test
    fun controlledRefreshCoordinator_attendedDuringAttended_joinsRunningOperation() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val operation = { testControlledRefreshOperation(attended = true, refresh = { runs += 1; gate.await() }) }
        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation()) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation()) }

        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, runs)
    }

    @Test
    fun needsStore_passOutcome_storesOnlyWhatChanged() {
        assertFalse(passResult(TREZOR_TIP).needsStore(CONTINUE, storedChainHeight = TREZOR_TIP))
        assertTrue(passResult(TREZOR_TIP).needsStore(CONTINUE, storedChainHeight = TREZOR_TIP - 1))
        assertTrue(passResult(TREZOR_TIP).needsStore(CONTINUE, storedChainHeight = null))
        assertTrue(passResult(TREZOR_TIP, suffixDetached = true).needsStore(CONTINUE, TREZOR_TIP))
        assertTrue(passResult(TREZOR_TIP).needsStore(RESET, TREZOR_TIP))
        assertTrue(passResult(TREZOR_TIP, completed = false).needsStore(CONTINUE, TREZOR_TIP))
    }

    private fun mockService(): MoneroWalletService {
        return mockk(relaxed = true)
    }

    // The kit's native call gate reads 0 while the wallet is closing.
    private fun closingWalletService(): MoneroWalletService = mockService().also { service ->
        val wallet = nativeWallet()
        every { wallet.balance } returns 0L
        every { service.wallet } returns wallet
    }

    private fun recordBalanceReadsDuringStop(
        service: MoneroWalletService,
        wrapper: MoneroKitWrapper,
    ): List<Long?> {
        val balances = mutableListOf<Long?>()
        every { service.stop(any()) } answers {
            balances += wrapper.getBalance()
            true
        }
        return balances
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
        timeSource: TimeSource = TimeSource.Monotonic,
        backgroundManager: IBackgroundManager = mockk { every { inForeground } returns true },
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = service,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = tracker,
            moneroTrezorGateway = gateway,
            connectivityManager = connectivityManager,
            walletHealthReader = healthReader,
            daemonSelector = daemonSelector,
            timeSource = timeSource,
            backgroundManager = backgroundManager,
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

    private fun activeReconciliationSession(wrapper: MoneroKitWrapper): Long {
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        return checkNotNull(reconciler.activeSession())
    }

    private fun setStarted(wrapper: MoneroKitWrapper) {
        privateField(wrapper, "isStarted").set(wrapper, true)
    }

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
        attended: Boolean = false,
        resets: Boolean = false,
    ): ControlledHardwareRefreshOperation = object : ControlledHardwareRefreshOperation {
        override val attended = attended
        override val resets = resets

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

    private fun userRejected() = HardwareWalletOperationException(HardwareWalletErrorCode.Cancelled, "rejected")

    private fun privateField(wrapper: MoneroKitWrapper, name: String) =
        wrapper.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }

    private fun passResult(
        finalHeight: Long,
        completed: Boolean = true,
        suffixDetached: Boolean = false,
        protocolStarted: Boolean = false,
    ) = HardwareKeyImageRefreshResult(
        startHeight = 0,
        finalHeight = finalHeight,
        suffixDetached = suffixDetached,
        protocolStarted = protocolStarted,
        finalAckChecked = protocolStarted,
        unknownKeyImagesRemain = false,
        completed = completed,
    )

    private fun String.isPassDetail() = this in setOf("store", "release", "setSynchronized", "refreshHistory") ||
        startsWith("durable=")

    // Opening a closed wallet resolves its files through Koin.
    private suspend fun withTrezorWalletFiles(block: suspend () -> Unit) {
        val fileDao = mockk<MoneroFileDao> {
            coEvery { getAssociatedRecord(trezorAccount.id) } returns
                MoneroFileRecord(trezorAccount.id, SecretString("trezor-wallet"), SecretString("password"))
        }
        startKoin { modules(module { single { fileDao } }) }
        try {
            block()
        } finally {
            stopKoin()
        }
    }

    /** An open Trezor wallet; each device pass returns the next of [results]. */
    private inner class TrezorWallet(
        var durableState: MoneroSpentReconciliationState = MoneroSpentReconciliationState.Ready,
        timeSource: TimeSource = TimeSource.Monotonic,
        restoreHeight: Long = TREZOR_RESTORE,
    ) {
        val events = mutableListOf<String>()
        val requests = mutableListOf<HardwareKeyImageRefreshResult.Request>()
        val results = ArrayDeque<HardwareKeyImageRefreshResult>()
        var daemonTargetHeight = TREZOR_TIP
        var userOperationWaiting = false
        var passFailure: Throwable? = null
        var inForeground = true
        var gatewayFailure: Throwable? = null
        // False models a restarted app: the gateway's in-memory session cache is empty.
        var cacheConfirmed = true
        var holdYieldingSession: CompletableDeferred<Unit>? = null
        // Models the attended pass waiting for the user to confirm on the device.
        var holdAttendedSession: CompletableDeferred<Unit>? = null
        var attendedAttempts = 0
        private var walletOpen = true
        // Runs on the refresh thread with the block observer, before the pass returns.
        var onPass: ((Long) -> Unit) -> Unit = {}
        val service = mockService()
        private val wallet = nativeWallet()
        private var blockObserver: (Long) -> Unit = {}
        private val gateway = object : MoneroHardwareGateway {
            override suspend fun <T> execute(account: Account, block: (String) -> T): T {
                attendedAttempts++
                holdAttendedSession?.await()
                return session("execute", block)
            }

            override suspend fun <T> executeYielding(account: Account, block: (String) -> T): T {
                holdYieldingSession?.await()
                if (!cacheConfirmed) {
                    throw HardwareWalletOperationException(HardwareWalletErrorCode.PendingDeviceVerification, "unconfirmed")
                }
                return session("executeYielding", block)
            }

            override val isUserOperationWaiting: Boolean get() = userOperationWaiting

            override fun markLiveRefreshConfirmed(account: Account) {
                cacheConfirmed = true
                events += "confirmed"
            }

            private fun <T> session(kind: String, block: (String) -> T): T {
                gatewayFailure?.let { throw it }
                events += kind
                return block("wallet-key").also { events += "release" }
            }
        }
        val wrapper = createWrapper(
            service,
            trezorAccount,
            gateway = gateway,
            timeSource = timeSource,
            backgroundManager = mockk { every { this@mockk.inForeground } answers { this@TrezorWallet.inForeground } },
            daemonSelector = mockk { coEvery { selectDaemon() } answers { events += "selectDaemon" } },
        )

        init {
            every { wallet.restoreHeight } returns restoreHeight
            every { wallet.daemonBlockChainHeight } returns TREZOR_TIP
            every { wallet.daemonBlockChainTargetHeight } answers { daemonTargetHeight }
            every { wallet.refreshWithHardwareKeyImages(any()) } answers {
                requests += firstArg<HardwareKeyImageRefreshResult.Request>()
                passFailure?.let { throw it }
                onPass(blockObserver)
                results.removeFirst()
            }
            every { wallet.requestControlledRefreshStop() } answers { events += "stop" }
            every { wallet.storeSafe() } answers { events += "store"; MONERO_STORE_OK }
            every { wallet.storeWithKeysSafe() } answers { events += "storeKeys"; MONERO_STORE_OK }
            every { wallet.setSynchronized() } answers { events += "setSynchronized" }
            every { wallet.refreshHistory() } answers { events += "refreshHistory" }
            every { wallet.deviceShowAddress(0, any(), "") } answers { events += "showAddress" }
            every { service.wallet } answers { wallet.takeIf { walletOpen } }
            every { service.startPaused(any(), any()) } answers {
                events += "open"
                walletOpen = true
                mockk<Wallet.Status> {
                    every { isOk } returns true
                    every { connectionStatus } returns null
                    every { errorString } returns null
                }
            }
            every { service.daemonHeight } returns TREZOR_TIP
            every { service.setControlledRefreshProgressObserver(any()) } answers { blockObserver = firstArg() }
            every { service.stop(any()) } answers {
                events += "close(save=${firstArg<Boolean>()})"
                walletOpen = false
                true
            }
            every { restoreSettingsManager.settings(trezorAccount, BlockchainType.Monero) } returns
                RestoreSettings().apply { birthdayHeight = restoreHeight }
            every { restoreSettingsManager.pendingMoneroRescanHeight(trezorAccount) } returns null
            every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } answers { durableState }
            every { restoreSettingsManager.saveMoneroSpentReconciliationState(trezorAccount, any()) } answers {
                durableState = secondArg()
                events += "durable=$durableState"
            }
            setStarted(wrapper)
            invokeActivateReconciliationSession(wrapper)
        }
    }

    private companion object {
        const val TREZOR_TIP = 1_000L
        const val TREZOR_RESTORE = 100L
        val CONTINUE = HardwareKeyImageRefreshResult.Mode.Continue
        val RESET = HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight

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
