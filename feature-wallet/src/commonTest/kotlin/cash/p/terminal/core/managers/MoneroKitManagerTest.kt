package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.ledger.domain.LedgerAttachments
import cash.p.terminal.ledger.domain.LedgerUserOperations
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.signer.HardwareKeyImageRefreshResult
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Regression coverage for the background lifecycle: entering background must stop and
 * save the Monero wallet (not merely pause it), unless polling is active or a keep-alive
 * request is in effect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneroKitManagerTest : MoneroKitManagerTestFixture() {

    private val ledgerAccount = account.copy(
        type = AccountType.LedgerDevice(deviceId = "device-id", model = "NanoSPlus", walletPublicKey = "key"),
    )

    // The periodic Ledger sync loop never idles, so it must stop before runTest drains the scheduler.
    private suspend fun withLedgerSubscription(manager: MoneroKitManager, block: suspend () -> Unit) {
        MoneroKitManager::class.java.getDeclaredMethod("subscribeToLedger", Account::class.java)
            .apply { isAccessible = true }
            .invoke(manager, ledgerAccount)
        try {
            block()
        } finally {
            cancelManagerScope()
        }
    }

    // The Trezor sync loop never idles, and runTest drains the shared scheduler after the body.
    private fun runTrezorTest(body: suspend TestScope.() -> Unit) = testScope.runTest {
        try {
            body()
        } finally {
            cancelManagerScope()
        }
    }

    private fun invokeSubscribeToEvents(manager: MoneroKitManager, account: Account = this.account) {
        MoneroKitManager::class.java.getDeclaredMethod("subscribeToEvents", Account::class.java)
            .apply { isAccessible = true }
            .invoke(manager, account)
    }

    // Desktop binds no Ledger services at all.
    @Test
    fun createKitInstance_softwareAccountWithoutLedgerBindings_createsWrapper() {
        stopKoin()
        startKoin { modules(trezorBindings) }
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground), wrapper = null)

        val wrapper = MoneroKitManager::class.java.getDeclaredMethod("createKitInstance", Account::class.java)
            .apply { isAccessible = true }
            .invoke(manager, account) as MoneroKitWrapper

        assertSame(account, wrapper.account)
    }

    @Test
    fun enterBackground_idle_stopsAndSavesWallet() = testScope.runTest {
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero) } returns false
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterForeground)
        val manager = createManager(backgroundStateFlow)
        invokeSubscribeToEvents(manager)

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.stop(saveWallet = true) }
        coVerify(exactly = 0) { mockWrapper.pause() }
    }

    @Test
    fun enterBackground_ledgerAccount_requestsCheckpointWithoutStopping() = testScope.runTest {
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero) } returns false
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterForeground)
        val manager = createManager(backgroundStateFlow)
        every { mockWrapper.account } returns ledgerAccount
        invokeSubscribeToEvents(manager)

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.requestCheckpoint() }
        coVerify(exactly = 0) { mockWrapper.stop(any()) }
    }

    @Test
    fun deviceAttached_ledgerInForeground_retriesSync() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        withLedgerSubscription(manager) { ledgerAttached.emit(Unit) }

        coVerify(exactly = 1) { mockWrapper.onDeviceAttached() }
    }

    @Test
    fun enterBackground_keepAlive_doesNotStop() = testScope.runTest {
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero) } returns true
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterForeground)
        val manager = createManager(backgroundStateFlow)
        invokeSubscribeToEvents(manager)

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        coVerify(exactly = 0) { mockWrapper.stop(any()) }
    }

    @Test
    fun startForPolling_reopensStoppedWallet() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        coEvery { mockWrapper.resume() } returns false

        manager.startForPolling()
        advanceUntilIdle()

        coVerify { mockWrapper.resume() }
        coVerify { mockWrapper.start() }
    }

    @Test
    fun stopForPolling_background_stopsAndSaves() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterBackground))

        manager.stopForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.stop(saveWallet = true) }
    }

    @Test
    fun startForPolling_offlinePair_startsLocalOnlyAndStillCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero)) } returns true
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.start(fixIfCorruptedFile = true, localOnly = true) }
        coVerify(exactly = 0) { mockWrapper.resume() }
        coVerify(exactly = 0) { mockWrapper.start(fixIfCorruptedFile = true, localOnly = false) }
        assertEquals(1, (fieldValue(manager, "pollingSessionCount") as AtomicInteger).get())
    }

    @Test
    fun resumeNetwork_offlineTrezorWallet_returnsWithoutAwaitingAttendedPass() = testScope.runTest {
        val wrapper = createPausedTrezorWrapper()
        val passRunning = CompletableDeferred<Unit>()
        coEvery { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), isNull<Long>()) } coAnswers {
            passRunning.complete(Unit)
            awaitCancellation()
        }

        assertTrue(withTimeout(1.seconds) { wrapper.resumeNetwork() })

        assertTrue(passRunning.isCompleted)
        assertFalse(fieldValue(wrapper, "networkPaused") as Boolean)
        coVerify(exactly = 1) { wrapper["stopInternal"](false) }
        coVerify(exactly = 0) { wrapper["startInternal"](true, false) }
        verify(exactly = 0) { moneroWalletService.connectDaemon() }
    }

    @Test
    fun resumeNetwork_attendedPassFails_staysOnline() = testScope.runTest {
        val wrapper = createPausedTrezorWrapper()
        coEvery { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), isNull<Long>()) } throws
            HardwareWalletOperationException(HardwareWalletErrorCode.Protocol, "failed")

        assertTrue(wrapper.resumeNetwork())
        advanceUntilIdle()

        assertFalse(fieldValue(wrapper, "networkPaused") as Boolean)
        coVerify(exactly = 0) { wrapper["startInternal"](true, true) }
    }

    @Test
    fun resumeNetwork_daemonSelectionFails_staysOfflineAndRethrows() =
        testScope.runTest {
            assertFailedTrezorNetworkResumeStaysOffline(
                IllegalStateException("no node"),
                IllegalStateException::class,
            )
        }

    @Test
    fun resumeNetwork_daemonSelectionCancelled_staysOfflineAndRethrows() =
        testScope.runTest {
            assertFailedTrezorNetworkResumeStaysOffline(
                CancellationException("cancelled"),
                CancellationException::class,
            )
        }

    @Test
    fun start_offlineTrezorClosedWallet_staysClosedWithoutDevice() = testScope.runTest {
        val gateway = mockk<MoneroHardwareGateway>(relaxed = true)
        val wrapper = createTrezorWrapperSpy(gateway = gateway, inForeground = true)

        wrapper.start(localOnly = true)

        verify(exactly = 0) { moneroWalletService.startOffline(any(), any()) }
        coVerify(exactly = 0) { gateway.execute<Any?>(any<Account>(), any()) }
        coVerify(exactly = 0) { gateway.executeYielding<Any?>(any(), any()) }
        assertFalse(wrapper.isNetworkOnline)
        assertFalse(wrapper.syncState.value is AdapterState.NotSynced)
    }
    @Test
    fun withNativeWalletReleased_offlineTrezorWallet_staysClosedWithoutThrowing() = testScope.runTest {
        val wrapper = createTrezorWrapperSpy(inForeground = true)
        setWrapperField(wrapper, "isStarted", true)
        setWrapperField(wrapper, "isPaused", true)
        coEvery { wrapper["stopInternal"](true) } coAnswers {
            setWrapperField(wrapper, "isStarted", false)
            setWrapperField(wrapper, "isPaused", false)
        }

        assertEquals("created", wrapper.withNativeWalletReleased(localOnly = true) { "created" })

        verify(exactly = 0) { moneroWalletService.startOffline(any(), any()) }
        assertFalse(fieldValue(wrapper, "isStarted") as Boolean)
    }

    @Test
    fun rescanIfActive_activeAccount_rescansWrapper() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)

        assertTrue(manager.rescanIfActive(account, 123L))

        coVerify(exactly = 1) { mockWrapper.rescan(123L) }
    }

    @Test
    fun rescanIfActive_ledgerAccount_releasesAccountMutexBeforeScan() = testScope.runTest {
        val busyDevice = mockk<MoneroHardwareGateway> {
            coEvery { executeYielding<Any?>(ledgerAccount, any()) } coAnswers { awaitCancellation() }
        }
        val wrapper = realWrapper(ledgerAccount, busyDevice).also { setWrapperField(it, "isStarted", true) }
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground), wrapper = wrapper)
        setField(manager, "currentAccount", ledgerAccount)

        assertTrue(async { manager.rescanIfActive(ledgerAccount, 123L) }.isCompleted)
    }

    @Test
    fun rescanIfActive_inactiveAccount_leavesWrapperUntouched() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account.copy(id = "other-account-id"))

        assertFalse(manager.rescanIfActive(account, 123L))

        coVerify(exactly = 0) { mockWrapper.rescan(any()) }
    }

    // Claiming the account without touching a closed wrapper: the caller must not fall back to
    // deleting the wallet files of the account this manager already owns.
    @Test
    fun rescanIfActive_activeAccountWithoutWrapper_claimsAccount() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        manager.moneroKitWrapper = null

        assertTrue(manager.rescanIfActive(account, 123L))
    }

    @Test
    fun unlink_lastUser_clearsWrapper() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)

        manager.unlink(account)
        advanceUntilIdle()

        // stopKit() must null the wrapper so a later poller restart cannot reuse it with a null factory.
        assertNull(manager.moneroKitWrapper)
        coVerify { mockWrapper.release() }
    }

    @Test
    fun unlink_nativeCloseFails_retainsWrapperOwnershipAndUseCount() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        val closeFailure = IllegalStateException("close failed")
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        coEvery { mockWrapper.release() } throws closeFailure

        val actual = assertFailsWith<IllegalStateException> {
            manager.unlink(account)
        }

        assertEquals(closeFailure.message, actual.message)
        assertSame(mockWrapper, manager.moneroKitWrapper)
        assertSame(account, manager.currentAccount)
        assertEquals(1, (fieldValue(manager, "useCount") as AtomicInteger).get())
    }

    @Test
    fun exclusiveNativeWallet_blocksNormalLifecycleUntilReleased() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery {
            mockWrapper.withNativeWalletReleased<String>(any(), any())
        } coAnswers {
            secondArg<suspend () -> String>().invoke()
        }

        val exclusive = async {
            manager.withExclusiveNativeWallet {
                entered.complete(Unit)
                release.await()
                "created"
            }
        }
        entered.await()
        val unlink = async { manager.unlink(account) }

        advanceUntilIdle()
        assertFalse(unlink.isCompleted)

        release.complete(Unit)
        assertEquals("created", exclusive.await())
        unlink.await()
        coVerify(exactly = 1) { mockWrapper.withNativeWalletReleased<String>(false, any()) }
    }

    @Test
    fun withExclusiveNativeWallet_offlinePair_restoresLocalOnly() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero)) } returns true
        every { mockWrapper.account } returns account
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        coEvery { mockWrapper.withNativeWalletReleased<String>(true, any()) } coAnswers {
            secondArg<suspend () -> String>().invoke()
        }

        assertEquals("created", manager.withExclusiveNativeWallet { "created" })

        coVerify(exactly = 1) { mockWrapper.withNativeWalletReleased<String>(true, any()) }
    }

    @Test
    fun rescan_inactiveTrezorAccount_preservesWalletFilesAndUpdatesHeight() =
        testScope.runTest {
            val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
            val trezorAccount = account.copy(
                type = AccountType.TrezorDevice(
                    deviceId = "device-id",
                    model = "T3T1",
                    firmwareVersion = "2.8.10",
                    walletPublicKey = "wallet-key",
                ),
            )

            manager.rescan(trezorAccount, 3_529_956)

            coVerify(exactly = 0) { removeMoneroWalletFilesUseCase(any<Account>()) }
            coVerify(exactly = 0) { moneroFileDao.deleteAssociatedRecord(any()) }
            verify(exactly = 1) {
                restoreSettingsManager.savePendingMoneroRescan(trezorAccount, 3_529_956)
            }
        }

    @Test
    fun rescan_activeAccount_unlinkWaitsUntilRescanCompletes() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        coEvery { mockWrapper.rescan(3_529_956) } coAnswers {
            entered.complete(Unit)
            release.await()
        }

        val rescan = async { manager.rescan(account, 3_529_956) }
        entered.await()
        val unlink = async { manager.unlink(account) }

        advanceUntilIdle()
        assertFalse(unlink.isCompleted)

        release.complete(Unit)
        rescan.await()
        unlink.await()
        coVerify(exactly = 1) { mockWrapper.rescan(3_529_956) }
    }

    @Test
    fun hardwareRestoreHeight_sameHeight_replaysOnlyPendingRescan() {
        assertFalse(
            shouldApplyHardwareRestoreHeight(
                currentHeight = 3_529_956,
                targetHeight = 3_529_956,
                hasPendingRescan = false,
            ),
        )
        assertTrue(
            shouldApplyHardwareRestoreHeight(
                currentHeight = 3_529_956,
                targetHeight = 3_529_956,
                hasPendingRescan = true,
            ),
        )
    }

    @Test
    fun networkAvailability_false_notifiesWrapper() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeAndSettle(manager)

        networkAvailability.emit(false)
        advanceUntilIdle()

        verify(exactly = 1) { mockWrapper.onNetworkLost() }
        coVerify(exactly = 0) { mockWrapper.refresh() }
    }

    @Test
    fun networkAvailability_trueInForeground_syncsUnattended() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeAndSettle(manager)

        networkAvailability.emit(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.resume() }
        coVerify(exactly = 1) { mockWrapper.syncUnattended() }
    }

    @Test
    fun networkAvailability_trueInBackground_doesNotRefresh() = testScope.runTest {
        val manager = createManager(
            MutableStateFlow(BackgroundManagerState.EnterBackground),
            inForeground = false
        )
        subscribeAndSettle(manager)

        networkAvailability.emit(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockWrapper.syncUnattended() }
        verify(exactly = 0) { mockWrapper.onNetworkLost() }
    }

    @Test
    fun subscribeToEvents_offlinePairOnColdStart_doesNotRefresh() = testScope.runTest {
        // Cold start: the collectors are installed before currentAccount is published, so the gate
        // has to read the account passed into subscribeToEvents, not the still-null field.
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero)) } returns true
        wrapperSyncState.value = AdapterState.NotSynced(IllegalStateException("Not connected"))
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        invokeSubscribeToEvents(manager)
        networkAvailability.emit(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockWrapper.syncUnattended() }
        coVerify(exactly = 0) { mockWrapper.start(fixIfCorruptedFile = true, localOnly = false) }
    }

    @Test
    fun subscribeToEvents_networkLostBeforeSubscription_notifiesWrapper() = testScope.runTest {
        // The loss lands in the startKit() -> subscribeToEvents() gap: the replay-less SharedFlow
        // drops it, and nothing re-emits it while the app stays in foreground.
        connectedState.value = false
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        invokeSubscribeToEvents(manager)
        advanceUntilIdle()

        verify(exactly = 1) { mockWrapper.onNetworkLost() }
    }

    @Test
    fun subscribeToEvents_networkRestoredBeforeSubscription_syncsUnattended() = testScope.runTest {
        // Mirror of the loss case: the kit started offline, the `true` that followed landed in the
        // startKit() -> subscribeToEvents() gap, and resumeOrStartKit alone never refreshes.
        wrapperSyncState.value = AdapterState.NotSynced(IllegalStateException("Not connected"))
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        invokeSubscribeToEvents(manager)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.syncUnattended() }
    }

    @Test
    fun trezorDetached_anyTrezor_wipesSessionsAndEndsRunningPass() = testScope.runTest {
        createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        runCurrent()

        trezorDetached.emit(Unit)
        runCurrent()

        verify(exactly = 1) { trezorGateway.retainSessions(emptySet()) }
        verify(exactly = 1) { mockWrapper.onTrezorDetached() }
    }

    @Test
    fun accountsDeleted_remainingAccount_wipesOnlyDeletedSessions() = testScope.runTest {
        every { accountManager.accounts } returns listOf(account)
        createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        runCurrent()

        accountsDeleted.onNext(Unit)
        runCurrent()

        verify(exactly = 1) { trezorGateway.retainSessions(setOf(account.id)) }
    }

    @Test
    fun trezorSyncLoop_foreground_syncsUnattendedEveryInterval() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)

        advanceTimeBy(31.seconds)
        coVerify(exactly = 1) { mockWrapper.syncUnattended() }
        advanceTimeBy(30.seconds)
        coVerify(exactly = 2) { mockWrapper.syncUnattended() }
    }

    @Test
    fun trezorAttached_stoppedWalletInForeground_startsWalletThenSyncs() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)
        coEvery { mockWrapper.resume() } returns false

        trezorAttached.emit(Unit)
        runCurrent()

        coVerify(exactly = 1) { mockWrapper.start(localOnly = false) }
        coVerify(exactly = 1) { mockWrapper.syncAskingDevice(any()) }
    }

    @Test
    fun trezorAttached_closedWalletUnconfirmedSession_asksDeviceOnce() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)
        stubStartNeedsConfirmation()

        trezorAttached.emit(Unit)
        runCurrent()

        coVerify(exactly = 1) { mockWrapper.askDevice(any()) }
        coVerify(exactly = 0) { mockWrapper.syncAskingDevice(any()) }
    }

    @Test
    fun enterForeground_closedWalletUnconfirmedSession_asksDeviceOnce() = runTrezorTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val manager = createManager(backgroundStateFlow)
        subscribeTrezorAndSettle(manager)
        stubStartNeedsConfirmation()

        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()

        coVerify(exactly = 1) { mockWrapper.start(localOnly = false) }
        coVerify(exactly = 1) { mockWrapper.askDevice(any()) }
    }

    @Test
    fun enterForeground_startFailsOtherwise_doesNotAskDevice() = runTrezorTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val manager = createManager(backgroundStateFlow)
        subscribeTrezorAndSettle(manager)
        stubStartNeedsConfirmation(HardwareWalletErrorCode.DeviceNotFound)

        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()

        coVerify(exactly = 0) { mockWrapper.askDevice(any()) }
    }

    @Test
    fun lifecycleStarts_interleavedPendingFailures_askDeviceOnceAndKeepListening() = runTrezorTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val manager = createManager(backgroundStateFlow)
        subscribeTrezorAndSettle(manager)
        // The losing trigger reads the wrapper while the other trigger's start is still in progress.
        var startsInFlight = 0
        val releaseStarts = CompletableDeferred<Unit>()
        every { mockWrapper.isRestartableAfterFailedStart } answers { startsInFlight == 0 }
        coEvery { mockWrapper.resume() } returns false
        coEvery { mockWrapper.start(localOnly = false) } coAnswers {
            startsInFlight++
            try {
                releaseStarts.await()
                throw pendingDeviceVerification()
            } finally {
                startsInFlight--
            }
        }

        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()
        networkAvailability.emit(true)
        runCurrent()
        releaseStarts.complete(Unit)
        runCurrent()
        networkAvailability.emit(true)
        runCurrent()

        coVerify(exactly = 3) { mockWrapper.start(localOnly = false) }
        coVerify(exactly = 1) { mockWrapper.askDevice(any()) }
    }

    @Test
    fun trezorAttached_whileForegroundPromptShown_keepsCueTakenBeforeIt() = runTrezorTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val manager = createManager(backgroundStateFlow)
        subscribeTrezorAndSettle(manager)
        var attendedFinished = 0L
        var promptFinished: CompletableDeferred<Unit>? = null
        val userAnswers = CompletableDeferred<Unit>()
        every { mockWrapper.attendedPassesFinished } answers { attendedFinished }
        // The attended pass holds the lifecycle lock, so a start queued behind it waits until it finishes.
        coEvery { mockWrapper.resume() } coAnswers {
            promptFinished?.await()
            false
        }
        coEvery { mockWrapper.start(localOnly = false) } throws pendingDeviceVerification()
        coEvery { mockWrapper.askDevice(any()) } coAnswers {
            val finished = CompletableDeferred<Unit>().also { promptFinished = it }
            userAnswers.await()
            attendedFinished++
            finished.complete(Unit)
        }

        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()
        trezorAttached.emit(Unit)
        runCurrent()
        userAnswers.complete(Unit)
        runCurrent()
        trezorAttached.emit(Unit)
        runCurrent()

        coVerify(exactly = 2) { mockWrapper.askDevice(0L) }
        coVerify(exactly = 0) { mockWrapper.askDevice(1L) }
        coVerify(exactly = 1) { mockWrapper.askDevice(2L) }
    }

    @Test
    fun trezorAttached_attendedPassFinishesDuringResume_syncsWithCueTakenBeforeIt() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)
        var attendedFinished = 0L
        every { mockWrapper.attendedPassesFinished } answers { attendedFinished }
        coEvery { mockWrapper.resume() } coAnswers {
            attendedFinished++
            true
        }

        trezorAttached.emit(Unit)
        runCurrent()

        coVerify(exactly = 1) { mockWrapper.syncAskingDevice(0L) }
    }

    @Test
    fun enterForeground_nonRetryableStartFailure_logsItAndKeepsListening() = runTrezorTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val manager = createManager(backgroundStateFlow)
        subscribeTrezorAndSettle(manager)
        val failure = HardwareWalletOperationException(HardwareWalletErrorCode.Protocol, "protocol")
        coEvery { mockWrapper.resume() } returns false
        coEvery { mockWrapper.start(localOnly = false) } throws failure
        val logs = CapturedErrors()

        withLogWriter(logs) {
            backgroundStateFlow.value = BackgroundManagerState.EnterForeground
            runCurrent()
        }
        networkAvailability.emit(true)
        runCurrent()
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        runCurrent()
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()

        assertTrue(failure in logs.errors)
        coVerify(exactly = 3) { mockWrapper.start(localOnly = false) }
        coVerify(exactly = 0) { mockWrapper.askDevice(any()) }
    }

    @Test
    fun trezorSyncLoop_iterationThrows_otherCollectorsKeepListening() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)
        coEvery { mockWrapper.syncUnattended() } throws IllegalStateException("loop failure")

        advanceTimeBy(31.seconds)
        trezorAttached.emit(Unit)
        runCurrent()

        coVerify(exactly = 1) { mockWrapper.syncAskingDevice(any()) }
    }

    @Test
    fun networkRegained_closedWalletUnconfirmedSession_doesNotAskDevice() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)
        stubStartNeedsConfirmation()

        networkAvailability.emit(true)
        runCurrent()

        coVerify(exactly = 1) { mockWrapper.start(localOnly = false) }
        coVerify(exactly = 0) { mockWrapper.askDevice(any()) }
        coVerify(exactly = 0) { mockWrapper.syncAskingDevice(any()) }
    }

    @Test
    fun trezorSyncLoop_unconfirmedSession_neverAsksDevice() = runTrezorTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeTrezorAndSettle(manager)

        advanceTimeBy(61.seconds)

        coVerify(exactly = 2) { mockWrapper.syncUnattended() }
        coVerify(exactly = 0) { mockWrapper.syncAskingDevice(any()) }
        coVerify(exactly = 0) { mockWrapper.askDevice(any()) }
    }

    @Test
    fun syncAskingDevice_confirmedSession_staysUnattended() = testScope.runTest {
        val wrapper = createTrezorWrapperSpy()
        coEvery { wrapper["syncTrezorWallet"](false, isNull<HardwareKeyImageRefreshResult.Mode>(), isNull<Long>()) } returns Unit

        wrapper.syncAskingDevice(0L)

        coVerify(exactly = 1) { wrapper["syncTrezorWallet"](false, isNull<HardwareKeyImageRefreshResult.Mode>(), isNull<Long>()) }
        coVerify(exactly = 0) { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), 0L) }
    }

    @Test
    fun syncAskingDevice_unconfirmedSession_runsOneAttendedPass() = testScope.runTest {
        val wrapper = createTrezorWrapperSpy()
        coEvery { wrapper["syncTrezorWallet"](false, isNull<HardwareKeyImageRefreshResult.Mode>(), isNull<Long>()) } throws
            pendingDeviceVerification()
        coEvery { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), 0L) } returns Unit

        wrapper.syncAskingDevice(0L)

        coVerify(exactly = 1) { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), 0L) }
    }

    @Test
    fun syncAskingDevice_attendedPassRejected_doesNotAskAgain() = testScope.runTest {
        val wrapper = createTrezorWrapperSpy()
        coEvery { wrapper["syncTrezorWallet"](any<Boolean>(), isNull<HardwareKeyImageRefreshResult.Mode>(), any<Long>()) } throws
            pendingDeviceVerification()

        assertFailsWith<HardwareWalletOperationException> { wrapper.syncAskingDevice(0L) }

        coVerify(exactly = 1) { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), 0L) }
    }

    @Test
    fun syncAskingDevice_otherFailure_doesNotAskDevice() = testScope.runTest {
        val wrapper = createTrezorWrapperSpy()
        coEvery { wrapper["syncTrezorWallet"](false, isNull<HardwareKeyImageRefreshResult.Mode>(), isNull<Long>()) } throws
            HardwareWalletOperationException(HardwareWalletErrorCode.DeviceNotFound, "USB unavailable")

        assertFailsWith<HardwareWalletOperationException> { wrapper.syncAskingDevice(0L) }

        coVerify(exactly = 0) { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), 0L) }
    }

    @Test
    fun getMoneroKitWrapper_deviceNotFound_thenForeground_startsWallet() = runTrezorTest {
        retryInitialStartupFailure_startsWallet(
            errorCode = HardwareWalletErrorCode.DeviceNotFound,
            lifecycleFailures = 0,
        )
    }

    @Test
    fun getMoneroKitWrapper_acquireTimeout_thenForeground_startsWallet() = runTrezorTest {
        retryInitialStartupFailure_startsWallet(
            errorCode = HardwareWalletErrorCode.AcquireTimeout,
            lifecycleFailures = 1,
        )
    }

    @Test
    fun getMoneroKitWrapper_trezorRetryableStartupFailure_retainsAndReturnsWrapper() = runTrezorTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(
            backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground),
            wrapper = wrapper,
        )
        val trezorAccount = trezorAccount()
        val startupFailure = HardwareWalletOperationException(
            HardwareWalletErrorCode.DeviceNotFound,
            "USB unavailable",
        )

        every { wrapper.isRestartableAfterFailedStart } returns true
        coEvery { wrapper.start() } throws startupFailure

        val initial = manager.getMoneroKitWrapper(trezorAccount)
        val retained = manager.getMoneroKitWrapper(trezorAccount)

        assertSame(initial, manager.moneroKitWrapper)
        assertSame(initial, retained)
        coVerify(exactly = 1) { wrapper.start() }
        coVerify(exactly = 0) { wrapper.release() }
    }

    @Test
    fun getMoneroKitWrapper_trezorCleanupRetainedOwnership_throwsTransientFailure() = testScope.runTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(MutableStateFlow(BackgroundManagerState.EnterBackground), wrapper)
        val startupFailure = HardwareWalletOperationException(HardwareWalletErrorCode.DeviceNotFound, "USB unavailable")

        every { wrapper.isRestartableAfterFailedStart } returns false
        coEvery { wrapper.start() } throws startupFailure

        assertSame(startupFailure, assertFailsWith<HardwareWalletOperationException> {
            manager.getMoneroKitWrapper(trezorAccount())
        })
    }

    @Test
    fun enterForeground_cleanupRetainedOwnership_retriesOnNextForeground() = testScope.runTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val wrapper = createWrapper()
        val manager = createManager(backgroundStateFlow, wrapper = wrapper)
        every { wrapper.isRestartableAfterFailedStart } returns false
        coEvery { wrapper.resume() } returns false
        coEvery { wrapper.start() } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.AcquireTimeout,
            "USB unavailable",
        )

        invokeSubscribeToEvents(manager)
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        coVerify(exactly = 2) { wrapper.start() }
    }

    @Test
    fun getMoneroKitWrapper_trezorProtocolStartupFailure_throwsAndClearsWrapper() = testScope.runTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(
            backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground),
            wrapper = wrapper,
        )
        val startupFailure = HardwareWalletOperationException(
            HardwareWalletErrorCode.Protocol,
            "Protocol failure",
        )

        coEvery { wrapper.start() } throws startupFailure

        val actual = assertFailsWith<HardwareWalletOperationException> {
            manager.getMoneroKitWrapper(trezorAccount())
        }

        assertSame(startupFailure, actual)
        assertNull(manager.moneroKitWrapper)
        coVerify(exactly = 1) { wrapper.release() }
    }

    // The foreground state collector also calls into the wrapper on subscription, so the recorded
    // calls are dropped before the network emission under test.
    private fun TestScope.subscribeAndSettle(manager: MoneroKitManager) {
        invokeSubscribeToEvents(manager)
        advanceUntilIdle()
        clearMocks(mockWrapper, answers = false)
    }

    // The Trezor loop never idles, so only the work due now is run.
    private fun TestScope.subscribeTrezorAndSettle(manager: MoneroKitManager) {
        every { mockWrapper.hardwareWallet } returns true
        invokeSubscribeToEvents(manager, trezorAccount())
        runCurrent()
        clearMocks(mockWrapper, answers = false)
    }

    private suspend fun TestScope.retryInitialStartupFailure_startsWallet(
        errorCode: HardwareWalletErrorCode,
        lifecycleFailures: Int,
    ) {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(backgroundStateFlow, wrapper)
        every { wrapper.isRestartableAfterFailedStart } returns true
        coEvery { wrapper.resume() } returns false
        val startupFailure = HardwareWalletOperationException(errorCode, "USB unavailable")
        val startStub = coEvery { wrapper.start() } throws startupFailure
        repeat(lifecycleFailures) {
            startStub andThenThrows startupFailure
        }
        startStub andThen Unit

        manager.getMoneroKitWrapper(trezorAccount())
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()
        repeat(lifecycleFailures) {
            backgroundStateFlow.value = BackgroundManagerState.EnterBackground
            backgroundStateFlow.value = BackgroundManagerState.EnterForeground
            runCurrent()
        }

        coVerify(exactly = lifecycleFailures + 2) { wrapper.start() }
    }

    private fun trezorAccount(): Account = account.copy(
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
    )

    private class CapturedErrors : LogWriter() {
        val errors = mutableListOf<Throwable>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (severity == Severity.Error && throwable != null) errors += throwable
        }
    }

    private inline fun withLogWriter(writer: LogWriter, block: () -> Unit) {
        val previous = Logger.config.logWriterList
        Logger.setLogWriters(writer)
        try {
            block()
        } finally {
            Logger.setLogWriters(previous)
        }
    }

    private fun pendingDeviceVerification() =
        HardwareWalletOperationException(HardwareWalletErrorCode.PendingDeviceVerification, "confirm on device")

    private fun stubStartNeedsConfirmation(
        errorCode: HardwareWalletErrorCode = HardwareWalletErrorCode.PendingDeviceVerification,
    ) {
        every { mockWrapper.isRestartableAfterFailedStart } returns true
        coEvery { mockWrapper.resume() } returns false
        coEvery { mockWrapper.start(localOnly = false) } throws HardwareWalletOperationException(errorCode, "failed")
    }

    private fun createPausedTrezorWrapper(
        daemonSelector: MoneroDaemonSelector = mockk(relaxed = true),
    ): MoneroKitWrapper {
        val wrapper = createTrezorWrapperSpy(daemonSelector = daemonSelector)
        setWrapperField(wrapper, "isStarted", true)
        setWrapperField(wrapper, "isPaused", true)
        setWrapperField(wrapper, "networkPaused", true)
        coEvery { wrapper["stopInternal"](false) } coAnswers {
            setWrapperField(wrapper, "isStarted", false)
            setWrapperField(wrapper, "isPaused", false)
        }
        return wrapper
    }

    private fun realWrapper(
        account: Account,
        gateway: MoneroHardwareGateway = mockk(relaxed = true),
        inForeground: Boolean = false,
        daemonSelector: MoneroDaemonSelector = mockk(relaxed = true),
    ) = MoneroKitWrapper(
        moneroWalletService = moneroWalletService,
        restoreSettingsManager = restoreSettingsManager,
        account = account,
        dispatcherProvider = dispatcherProvider,
        networkErrorTracker = mockk(relaxed = true),
        moneroHardwareGateway = gateway,
        connectivityManager = connectivityManager,
        backgroundManager = mockk(relaxed = true) { every { this@mockk.inForeground } returns inForeground },
        ledgerUserOperations = LedgerUserOperations(),
        ledgerGateway = mockk(relaxed = true),
        daemonSelector = daemonSelector,
    )

    private fun createTrezorWrapperSpy(
        gateway: MoneroHardwareGateway = mockk(relaxed = true),
        inForeground: Boolean = false,
        daemonSelector: MoneroDaemonSelector = mockk(relaxed = true),
    ): MoneroKitWrapper =
        spyk(realWrapper(trezorAccount(), gateway, inForeground, daemonSelector), recordPrivateCalls = true)

    private suspend fun <T : Throwable> assertFailedTrezorNetworkResumeStaysOffline(
        failure: T,
        failureClass: KClass<T>,
    ) {
        val daemonSelector = mockk<MoneroDaemonSelector> { coEvery { selectDaemon() } throws failure }
        val wrapper = createPausedTrezorWrapper(daemonSelector)

        val actual = assertFailsWith(failureClass) { wrapper.resumeNetwork() }

        assertSame(failure, actual)
        assertFalse(wrapper.isNetworkOnline)
        assertTrue(fieldValue(wrapper, "networkPaused") as Boolean)
        verify(exactly = 0) { moneroWalletService.startOffline(any(), any()) }
        coVerify(exactly = 0) { wrapper["syncTrezorWallet"](true, isNull<HardwareKeyImageRefreshResult.Mode>(), 0L) }
    }

    private fun setWrapperField(wrapper: MoneroKitWrapper, name: String, value: Any?) {
        MoneroKitWrapper::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(wrapper, value)
    }

    private fun fieldValue(instance: Any, name: String): Any? =
        ((instance as? MoneroKitManager)?.let { MoneroKitManager::class.java }
            ?: MoneroKitWrapper::class.java)
            .getDeclaredField(name).apply {
            isAccessible = true
        }.get(instance)
}

/** Shared by the manager test classes: one manager per test, its background collectors cancelled after it. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MoneroKitManagerTestFixture {
    protected val moneroWalletService = mockk<MoneroWalletService>(relaxed = true)
    protected val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    protected val backgroundKeepAliveManager = mockk<BackgroundKeepAliveManager>(relaxed = true)
    protected val moneroFileDao = mockk<MoneroFileDao>(relaxed = true)
    protected val removeMoneroWalletFilesUseCase =
        mockk<IRemoveMoneroWalletFilesUseCase>(relaxed = true)
    protected val networkAvailability = MutableSharedFlow<Boolean>()
    protected val connectedState = MutableStateFlow(true)
    protected val connectivityManager = mockk<IConnectivityManager>(relaxed = true) {
        every { networkAvailabilityFlow } returns networkAvailability
        every { isConnected } returns connectedState
    }
    protected val wrapperSyncState = MutableStateFlow<AdapterState>(AdapterState.Synced)
    protected val mockWrapper = mockk<MoneroKitWrapper>(relaxed = true) {
        every { syncState } returns wrapperSyncState
    }
    protected val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    protected val account = Account(
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

    protected val dispatcher = UnconfinedTestDispatcher()
    protected val testScope = TestScope(dispatcher)
    protected val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    protected var createdManager: MoneroKitManager? = null
    protected val trezorAttached = MutableSharedFlow<Unit>()
    protected val trezorDetached = MutableSharedFlow<Unit>()
    protected val accountsDeleted = PublishSubject.create<Unit>()
    protected val accountManager = mockk<IAccountManager> {
        every { accountsDeletedFlowable } returns accountsDeleted.toFlowable(BackpressureStrategy.BUFFER)
    }
    protected val trezorGateway = mockk<MoneroHardwareGateway>(relaxed = true)
    protected val ledgerAttached = MutableSharedFlow<Unit>()
    protected val ledgerGateway = mockk<MoneroLedgerOperationGateway>(relaxed = true)

    protected val trezorBindings = module {
        single<TrezorAttachments> {
            object : TrezorAttachments {
                override val attached = trezorAttached
                override val detached = trezorDetached
            }
        }
        single { accountManager }
        single { trezorGateway }
    }

    private val ledgerBindings = module {
        single<LedgerAttachments> {
            object : LedgerAttachments {
                override val attached = ledgerAttached
            }
        }
        single { ledgerGateway }
        single { LedgerUserOperations() }
    }

    @Before
    fun setUp() {
        startKoin { modules(trezorBindings, ledgerBindings) }
    }

    @After
    fun tearDown() {
        stopKoin()
        cancelManagerScope()
    }

    // MoneroKitManager doesn't expose a close/destroy method, so cancel its internal
    // coroutineScope via reflection to avoid leaking the background collectors.
    protected fun cancelManagerScope() {
        createdManager?.let { manager ->
            val scopeField = MoneroKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    protected fun createManager(
        backgroundStateFlow: MutableStateFlow<BackgroundManagerState>,
        inForeground: Boolean = true,
        wrapper: MoneroKitWrapper? = mockWrapper,
    ): MoneroKitManager {
        val backgroundManager = mockk<IBackgroundManager> {
            every { stateFlow } returns backgroundStateFlow
            every { this@mockk.inForeground } returns inForeground
        }
        // A live wrapper always belongs to the account whose wallet file it opened.
        every { mockWrapper.account } returns account
        return MoneroKitManager(
            moneroWalletService = moneroWalletService,
            backgroundManager = backgroundManager,
            restoreSettingsManager = restoreSettingsManager,
            backgroundKeepAliveManager = backgroundKeepAliveManager,
            connectivityManager = connectivityManager,
            dispatcherProvider = dispatcherProvider,
            moneroFileDao = moneroFileDao,
            removeMoneroWalletFilesUseCase = removeMoneroWalletFilesUseCase,
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
        ).apply {
            moneroKitWrapper = wrapper
            createdManager = this
        }
    }

    protected fun createWrapper(): MoneroKitWrapper = mockk(relaxed = true) {
        every { syncState } returns wrapperSyncState
    }

    protected fun createKitInstanceSpy(
        backgroundStateFlow: MutableStateFlow<BackgroundManagerState>,
        wrapper: MoneroKitWrapper,
    ): MoneroKitManager {
        val manager = spyk(
            createManager(backgroundStateFlow, wrapper = null),
            recordPrivateCalls = true,
        )
        every { manager["createKitInstance"](any<Account>()) } returns wrapper
        createdManager = manager
        return manager
    }

    protected fun setField(manager: MoneroKitManager, name: String, value: Any?) {
        MoneroKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }
}
