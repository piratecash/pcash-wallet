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
import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.StorageRouter
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
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenType
import io.mockk.confirmVerified
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertNotSame

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
        startKoinWithMoneroFiles(ledgerAccount, dao)
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

        wrapper.release()
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
    fun refresh_ledgerWalletBehindNodeThreePasses_keepsSyncedState() = runTest {
        val events = mutableListOf<String>()
        val userOperations = LedgerUserOperations()
        val notices = collectNotices(userOperations)
        val wrapper = behindNodeLedgerWrapper(LedgerScan(events), userOperations)
        events.clear()

        syncPasses(wrapper, 3)

        assertEquals(AdapterState.Synced, wrapper.syncState.value)
        assertSame(MoneroSpendReadiness.Ready, wrapper.spendReadiness.value)
        assertEquals(List(3) { "refresh@100" }, events)
        assertEquals(emptyList<String>(), notices)
    }

    @Test
    fun refresh_ledgerWalletBehindNodeFourPasses_showsNodeNotSynchronizedError() = runTest {
        val wrapper = behindNodeLedgerWrapper(LedgerScan(mutableListOf()))

        syncPasses(wrapper, 4)

        val error = (wrapper.syncState.value as AdapterState.NotSynced).error as HardwareWalletOperationException
        assertSame(HardwareWalletErrorCode.Network, error.error)
    }

    @Test
    fun refresh_ledgerRescanPendingWhileNodeBehind_showsErrorAtOnce() = runTest {
        val settings = ledgerBirthday()
        val wrapper = behindNodeLedgerWrapper(LedgerScan(mutableListOf()))
        settings.birthdayHeight = 60

        syncPasses(wrapper, 1)

        val error = (wrapper.syncState.value as AdapterState.NotSynced).error as HardwareWalletOperationException
        assertSame(HardwareWalletErrorCode.Network, error.error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
    }

    @Test
    fun refresh_ledgerWalletCaughtUpBetweenLags_resetsStreak() = runTest {
        val scan = LedgerScan(mutableListOf())
        val wrapper = behindNodeLedgerWrapper(scan)
        syncPasses(wrapper, 3)
        scan.daemonSynced = true
        syncPasses(wrapper, 1)
        scan.daemonSynced = false

        syncPasses(wrapper, 3)

        assertEquals(AdapterState.Synced, wrapper.syncState.value)
    }

    @Test
    fun refresh_ledgerWalletBehindNodeAroundYieldedPass_countsOnlyConsecutiveLags() = runTest {
        val userOperations = LedgerUserOperations()
        val wrapper = behindNodeLedgerWrapper(LedgerScan(mutableListOf()), userOperations)
        syncPasses(wrapper, 3)
        userOperations.run { syncPasses(wrapper, 1) }

        syncPasses(wrapper, 1)

        assertEquals(AdapterState.Synced, wrapper.syncState.value)
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
            walletHealthReader = healthReader,
            daemonSelector = daemonSelector,
            timeSource = timeSource,
            backgroundManager = backgroundManager,
            ledgerUserOperations = userOperations,
            ledgerGateway = ledgerSessions,
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
        startKoinWithMoneroFiles(trezorAccount, fileDao)
        try {
            block()
        } finally {
            stopKoin()
        }
    }

    private fun startKoinWithMoneroFiles(account: Account, fileDao: MoneroFileDao) {
        val router = mockk<StorageRouter> {
            every { account(account.id) } returns mockk<AccountDaos> { every { moneroFileDao() } returns fileDao }
        }
        startKoin { modules(module { single { router } }) }
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
        startKoinWithMoneroFiles(ledgerAccount, dao)
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

    /** Synced to the tip once; then the node reports a higher target, so every later pass ends behind it. */
    private suspend fun behindNodeLedgerWrapper(
        scan: LedgerScan,
        userOperations: LedgerUserOperations = LedgerUserOperations(),
    ): MoneroKitWrapper = scanningLedgerWrapper(scan, userOperations = userOperations).also {
        syncPasses(it, 1)
        scan.daemonSynced = false
    }

    private suspend fun syncPasses(wrapper: MoneroKitWrapper, passes: Int) = repeat(passes) {
        wrapper.refresh()
        awaitProxySync(wrapper)
    }

    private fun proxySync(wrapper: MoneroKitWrapper): Job? = privateField(wrapper, "proxySync").get(wrapper) as Job?

    // Triggers only start the Ledger sync: it runs, with any pass coalesced behind it, in the wrapper's own job.
    private suspend fun awaitProxySync(wrapper: MoneroKitWrapper) {
        while (true) proxySync(wrapper)?.join() ?: return
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
