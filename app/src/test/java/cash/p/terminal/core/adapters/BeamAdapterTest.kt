package cash.p.terminal.core.adapters

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamFailure
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamRestorePhase
import cash.p.beam.BeamRestoreProgress
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.R
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.math.BigDecimal
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class BeamAdapterTest {
    private val sdkState = MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
    private val sdkBalance = MutableStateFlow(BeamBalance())
    private val sdk = mockk<BeamWalletSession> {
        every { state } returns sdkState
        every { balance } returns sdkBalance
        every { transactions } returns MutableStateFlow(emptyList())
    }
    private val session = mockk<BeamSessionOwner.Session> {
        every { accountId } returns "beam-account"
        every { wallet } returns sdk
        every { receiveAddress } returns
            BeamAddress("public-offline-test", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
    }
    private val owner = mockk<BeamSessionOwner>(relaxed = true) {
        every { current } returns session
    }

    @Before
    fun mockTranslations() {
        mockkObject(Translator)
        every { Translator.getString(R.string.beam_sync_network_error) } returns NETWORK_ERROR
    }

    @After
    fun clearTranslations() = unmockkObject(Translator)

    @Test
    fun balanceUpdates_initialAndPausedSnapshots_preserveOnlyAuthoritativeBalanceIncludingZero() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        val updates = mutableListOf<BalanceData?>()
        val collector = backgroundScope.launch {
            adapter.balanceUpdatedFlow.collect { updates += adapter.lastKnownBalanceData }
        }
        try {
            adapter.attachLocalData()
            runCurrent()
            assertEquals(null, adapter.lastKnownBalanceData)
            sdkBalance.value = BeamBalance(available = 100, isAuthoritative = true)
            runCurrent()
            val saved = adapter.balanceData
            sdkBalance.value = BeamBalance()
            runCurrent()
            assertEquals(saved, adapter.lastKnownBalanceData)
            sdkBalance.value = BeamBalance(isAuthoritative = true)
            runCurrent()
            assertEquals(0, requireNotNull(adapter.lastKnownBalanceData).total.compareTo(BigDecimal.ZERO))
            assertEquals(listOf(null, saved, adapter.lastKnownBalanceData), updates)
        } finally {
            collector.cancel()
            adapter.close()
        }
    }

    @Test
    fun refresh_failedDownloadDuringCleanup_coalescesRetryAndAwaitsSerializedRestart() = runTest {
        val stopGate = deferStop()
        val startGate = CompletableDeferred<Unit>()
        var attempts = 0
        coEvery { owner.start(session) } coAnswers {
            if (++attempts == 1) throw BeamFailure.Download("private-sdk-detail")
            startGate.await()
            sdkState.value = BeamWalletState.Ready(100)
        }
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            sdkState.value = BeamWalletState.Stopped
            runCurrent()
            assertEquals(NETWORK_ERROR, (adapter.balanceState as AdapterState.NotSynced).error.message)
            val first = async { adapter.refresh() }
            val second = async { adapter.refresh() }
            runCurrent()
            assertEquals(1, attempts)
            assertFalse(first.isCompleted)
            stopGate.complete(Unit)
            runCurrent()
            assertEquals(2, attempts)
            assertFalse(first.isCompleted)
            startGate.complete(Unit)
            first.await()
            second.await()
            runCurrent()
            assertEquals(AdapterState.Synced, adapter.balanceState)
            adapter.refresh()
            assertEquals(2, attempts)
            coVerify(exactly = 0) { sdk.start() }
            coVerify(exactly = 0) { sdk.close() }
        } finally {
            stopGate.complete(Unit)
            startGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun refresh_nodeErrorAfterStartup_restartsAndSanitizesRepeatedFailure() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            sdkState.value = BeamWalletState.Error(BeamFailure.Node("private-node-detail"))
            runCurrent()
            assertEquals(NETWORK_ERROR, (adapter.balanceState as AdapterState.NotSynced).error.message)
            coEvery { owner.start(session) } throws BeamFailure.Download("private-download-detail")
            adapter.refresh()
            runCurrent()
            coVerify(exactly = 2) { owner.start(session) }
            assertEquals(null, (adapter.balanceState as AdapterState.NotSynced).error.cause)
            assertFalse(adapter.statusInfo.toString().contains("private-"))
        } finally {
            adapter.close()
        }
    }

    @Test
    fun refresh_pendingStartupAndCancelledCaller_doesNotRestartOrCancelSharedStart() = runTest {
        val startGate = CompletableDeferred<Unit>()
        coEvery { owner.start(session) } coAnswers { startGate.await() }
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            val refresh = async { adapter.refresh() }
            runCurrent()
            assertFalse(refresh.isCompleted)
            refresh.cancelAndJoin()
            assertFalse(startGate.isCancelled)
            startGate.complete(Unit)
            runCurrent()
            coVerify(exactly = 1) { owner.start(session) }
            coVerify(exactly = 0) { owner.stop(session) }
        } finally {
            startGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun refresh_queuedRetryThenPause_doesNotStartAfterCleanup() = runTest {
        val stopGate = deferStop()
        coEvery { owner.start(session) } throws BeamFailure.Download("private-sdk-detail")
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            val retry = async { adapter.refresh() }
            runCurrent()
            adapter.pauseNetwork()
            stopGate.complete(Unit)
            retry.await()
            runCurrent()
            adapter.refresh()
            assertTrue(adapter.isNetworkPaused)
            coVerify(exactly = 1) { owner.start(session) }
            adapter.stop()
            adapter.refresh()
            coVerify(exactly = 1) { owner.start(session) }
        } finally {
            stopGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun refresh_queuedRetryThenAccountReplacement_doesNotStartOrStopReplacement() = runTest {
        val stopGate = deferStop()
        coEvery { owner.start(session) } throws BeamFailure.Download("private-sdk-detail")
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            val retry = async { adapter.refresh() }
            runCurrent()
            val replacement = mockk<BeamSessionOwner.Session>()
            every { owner.current } returns replacement
            stopGate.complete(Unit)
            retry.await()
            runCurrent()
            coVerify(exactly = 1) { owner.start(session) }
            coVerify(exactly = 0) { owner.start(replacement) }
            coVerify(exactly = 0) { owner.stop(replacement) }
        } finally {
            stopGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun attachLocalData_beforeSync_exposesCachedAddressWithoutStartingNetwork() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            runCurrent()
            assertEquals("public-offline-test", adapter.receiveAddress)
            assertTrue(adapter.isMainNet)
            coVerify(exactly = 0) { owner.start(any()) }
            coVerify(exactly = 0) { owner.stop(any()) }
            coVerify(exactly = 0) { sdk.receiveAddress(any()) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun receiveAddress_afterAccountSwitch_returnsCachedPublicOfflineAddress() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            every { owner.current } returns mockk()
            every { session.receiveAddress } throws IllegalStateException("Stale session")

            assertEquals("public-offline-test", adapter.receiveAddress)
            assertTrue(adapter.isMainNet)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun balanceUpdates_atomicAmounts_preservePrecisionAndExcludeShieldedSubset() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            sdkBalance.value = BeamBalance(
                available = 123456789,
                receiving = 1,
                sending = 900000000,
                maturing = 200000001,
                shielded = 100000000,
                isAuthoritative = true,
            )
            runCurrent()
            assertEquals(BigDecimal("1.23456789"), adapter.balanceData.available)
            assertEquals(BigDecimal("0.00000001"), adapter.balanceData.pending)
            assertEquals(BigDecimal("2.00000001"), adapter.balanceData.timeLocked)
            assertEquals(BigDecimal("3.23456791"), adapter.balanceData.total)
            sdkBalance.value = BeamBalance(available = Long.MAX_VALUE, isAuthoritative = true)
            runCurrent()
            assertEquals(BigDecimal("92233720368.54775807"), adapter.balanceData.available)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun stateUpdates_syncAndRestoreProgress_mapToAdapterState() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            val cases = listOf(
                BeamWalletState.Connecting to AdapterState.Connecting,
                BeamWalletState.Syncing(25, 100) to AdapterState.Syncing(25.0, blocksRemained = 75),
                BeamWalletState.Syncing(0, 0) to AdapterState.Syncing(),
                BeamWalletState.Ready(100) to AdapterState.Synced,
            )
            cases.forEach { (sdkValue, expected) ->
                sdkState.value = sdkValue
                runCurrent()
                assertEquals(expected, adapter.balanceState)
            }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun stateUpdates_allRestorePhases_mapStageProgressAndBytes() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            val cases = listOf(
                BeamRestorePhase.ResolvingBirthday to AdapterState.SnapshotRestoreStage.ResolvingBirthday,
                BeamRestorePhase.DownloadingSnapshot to AdapterState.SnapshotRestoreStage.DownloadingSnapshot,
                BeamRestorePhase.ValidatingSnapshot to AdapterState.SnapshotRestoreStage.ValidatingSnapshot,
                BeamRestorePhase.CountingShieldedOutputs to AdapterState.SnapshotRestoreStage.CountingShieldedOutputs,
                BeamRestorePhase.ScanningWalletOutputs to AdapterState.SnapshotRestoreStage.ScanningWalletOutputs,
                BeamRestorePhase.ImportingSnapshot to AdapterState.SnapshotRestoreStage.ImportingSnapshot,
                BeamRestorePhase.CatchingUp to AdapterState.SnapshotRestoreStage.CatchingUp,
            )

            cases.forEach { (sdkPhase, adapterStage) ->
                sdkState.value = BeamWalletState.Restoring(
                    BeamRestoreProgress(
                        phase = sdkPhase,
                        currentHeight = 5,
                        targetHeight = 20,
                        downloadedBytes = 30,
                        totalBytes = 40,
                    )
                )
                runCurrent()

                assertEquals(
                    AdapterState.Syncing(
                        progress = if (sdkPhase == BeamRestorePhase.DownloadingSnapshot) 75.0 else 25.0,
                        substatus = AdapterState.Substatus.SnapshotRestore(
                            stage = adapterStage,
                            downloadedBytes = 30,
                            totalBytes = 40,
                        ),
                    ),
                    adapter.balanceState,
                )
            }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun stateUpdates_restoreUnknownTotal_keepsCountersAndHasUnknownProgress() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            sdkState.value = BeamWalletState.Restoring(
                BeamRestoreProgress(
                    phase = BeamRestorePhase.DownloadingSnapshot,
                    downloadedBytes = 30,
                )
            )
            runCurrent()

            assertEquals(
                AdapterState.Syncing(
                    substatus = AdapterState.Substatus.SnapshotRestore(
                        stage = AdapterState.SnapshotRestoreStage.DownloadingSnapshot,
                        downloadedBytes = 30,
                    ),
                ),
                adapter.balanceState,
            )
        } finally {
            adapter.close()
        }
    }

    @Test
    fun stateUpdates_restoreComplete_remainsSyncingUntilReady() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            sdkState.value = BeamWalletState.Restoring(
                BeamRestoreProgress(
                    phase = BeamRestorePhase.CatchingUp,
                    currentHeight = 100,
                    targetHeight = 100,
                )
            )
            runCurrent()
            assertTrue(adapter.balanceState is AdapterState.Syncing)

            sdkState.value = BeamWalletState.Ready(100)
            runCurrent()
            assertEquals(AdapterState.Synced, adapter.balanceState)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun stateUpdates_unavailableStates_hideSdkMessages() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            listOf(BeamWalletState.Closed, BeamWalletState.Stopped, BeamWalletState.Offline(10),
                BeamWalletState.Error(BeamFailure.Node("private-sdk-detail"))).forEach {
                sdkState.value = it
                runCurrent()
                assertTrue(adapter.balanceState is AdapterState.NotSynced)
                assertFalse(adapter.statusInfo.toString().contains("private-sdk-detail"))
                assertFalse(adapter.statusInfo.toString().contains(adapter.receiveAddress))
            }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun pauseNetwork_duringStartup_awaitsStopBeforeResume() = runTest {
        val stopGate = deferStop()
        coEvery { owner.start(session) } coAnswers { awaitCancellation() }
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            adapter.pauseNetwork()
            runCurrent()
            adapter.resumeNetwork()
            runCurrent()
            coVerify(exactly = 1) { owner.start(session) }
            stopGate.complete(Unit)
            runCurrent()
            coVerify(exactly = 2) { owner.start(session) }
        } finally {
            stopGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun pauseNetworkAndAwait_duringStartup_waitsForStopCompletion() = runTest {
        sdkState.value = BeamWalletState.Syncing(25, 100)
        val stopGate = deferStop()
        coEvery { owner.start(session) } coAnswers { awaitCancellation() }
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            assertFalse(adapter.isNetworkPaused)
            adapter.start()
            assertFalse(adapter.isNetworkPaused)
            runCurrent()
            val pause = async { adapter.pauseNetworkAndAwait() }
            runCurrent()
            assertFalse(pause.isCompleted)
            assertFalse(adapter.isNetworkPaused)

            stopGate.complete(Unit)
            pause.await()
            assertTrue(adapter.isNetworkPaused)
            assertEquals(BeamWalletState.Syncing(25, 100), sdkState.value)
            adapter.resumeNetwork()
            assertFalse(adapter.isNetworkPaused)
            runCurrent()
            coVerify(exactly = 2) { owner.start(session) }
        } finally {
            stopGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun pauseNetworkAndAwait_stopFails_returnsOperationFailureWithoutConfirmingOffline() = runTest {
        coEvery { owner.stop(session) } throws IllegalStateException("SDK stop failed")
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            val failure = assertFailsWith<IllegalStateException> { adapter.pauseNetworkAndAwait() }
            assertFalse(failure is CancellationException)
            assertFalse(adapter.isNetworkPaused)
            coVerify(exactly = 1) { owner.stop(session) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun pauseNetworkAndAwait_adapterStops_returnsOperationFailureWithoutConfirmingOffline() = runTest {
        coEvery { owner.stop(session) } coAnswers { awaitCancellation() }
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            val pause = async { assertFailsWith<IllegalStateException> { adapter.pauseNetworkAndAwait() } }
            runCurrent()
            assertFalse(pause.isCompleted)
            adapter.stop()

            assertFalse(pause.await() is CancellationException)
            assertFalse(adapter.isNetworkPaused)
            coVerify(exactly = 1) { owner.stop(session) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun pauseNetworkAndAwait_callerCancelled_preservesCancellation() = runTest {
        val stopGate = deferStop()
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        val failure = CompletableDeferred<Exception>()
        try {
            val pause = launch {
                failure.complete(assertFailsWith<Exception> { adapter.pauseNetworkAndAwait() })
            }
            runCurrent()
            assertFalse(failure.isCompleted)
            assertFalse(adapter.isNetworkPaused)
            pause.cancelAndJoin()

            assertTrue(failure.await() is CancellationException)
            assertFalse(adapter.isNetworkPaused)
        } finally {
            stopGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun pauseNetworkAndAwait_afterAccountSwitch_doesNotConfirmPauseOrStopReplacement() = runTest {
        sdkState.value = BeamWalletState.Syncing(25, 100)
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        val replacement = mockk<BeamSessionOwner.Session>()
        every { owner.current } returns replacement
        coEvery { owner.stop(session) } throws IllegalStateException("Stale session")
        try {
            val failure = try {
                adapter.pauseNetworkAndAwait()
                null
            } catch (error: Exception) {
                error
            }
            assertTrue(failure != null)
            assertFalse(adapter.isNetworkPaused)
            coVerify(exactly = 0) { owner.stop(replacement) }
        } finally {
            adapter.close()
        }
    }

    @Test
    fun pauseNetworkAndAwait_supersededByResume_finishesWithoutConfirmingOffline() = runTest {
        val stopGate = deferStop()
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.start()
            runCurrent()
            val pause = async {
                try {
                    adapter.pauseNetworkAndAwait()
                    null
                } catch (error: IllegalStateException) {
                    error
                }
            }
            runCurrent()
            adapter.resumeNetwork()
            runCurrent()
            assertFalse(pause.isCompleted)
            assertFalse(adapter.isNetworkPaused)
            coVerify(exactly = 1) { owner.start(session) }

            stopGate.complete(Unit)
            assertTrue(pause.await() is IllegalStateException)
            runCurrent()
            assertFalse(adapter.isNetworkPaused)
            coVerify(exactly = 2) { owner.start(session) }
        } finally {
            stopGate.complete(Unit)
            adapter.close()
        }
    }

    @Test
    fun stop_lateSdkUpdates_doesNotPublishOrCloseReplacement() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        adapter.attachLocalData()
        runCurrent()
        val originalState = adapter.balanceState
        adapter.stop()
        every { owner.current } returns mockk()
        sdkState.value = BeamWalletState.Ready(100)
        sdkBalance.value = BeamBalance(available = 100)
        runCurrent()
        adapter.close()
        assertEquals(originalState, adapter.balanceState)
        assertEquals(0, adapter.balanceData.available.compareTo(BigDecimal.ZERO))
        coVerify(exactly = 0) { owner.close() }
    }

    private fun adapter(dispatcher: CoroutineDispatcher) = testBeamAdapter(owner, session, dispatcher)

    private fun deferStop() = CompletableDeferred<Unit>().also { stopGate ->
        coEvery { owner.stop(session) } coAnswers { withContext(NonCancellable) { stopGate.await() } }
    }

    private companion object {
        const val NETWORK_ERROR = "BEAM connection failed. Please try again."
    }
}
