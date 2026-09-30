package cash.p.terminal.core.managers

import cash.p.beam.BeamFailure
import cash.p.beam.BeamRestorePhase
import cash.p.beam.BeamRestoreProgress
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamTransactionStatus
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.adapters.BeamAdapter
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BeamStartupReconciliationTest : BeamSendTestFixture() {
    private val connected = MutableStateFlow(true)
    private val foreground = MutableStateFlow(BackgroundManagerState.EnterForeground)
    private var monitoringLeases = 0

    @Test
    fun startup_processRestart_recoversEachDurableStateWithoutNewIdentity() = runTest {
        val cases = listOf(
            BeamSendResolution.Prepared(TX_ID),
            BeamSendResolution.Committing(TX_ID),
            BeamSendResolution.Indeterminate(TX_ID),
            BeamSendResolution.Submitted(TX_ID),
        )
        for (resolution in cases) {
            seedOperation(resolution)
            owner.close()
            restart()
            openSession()
            val adapter = startupAdapter()
            try {
                runCurrent()
                val resumes = resolution is BeamSendResolution.Prepared || resolution is BeamSendResolution.Committing
                assertEquals(if (resumes) listOf(OPERATION_ID) else emptyList(), sdk.commits)
                assertTrue(sdk.recoveries > 0)
                assertTrue(sdk.prepares.isEmpty())
                assertEquals(OPERATION_ID, row().operationId)
                assertEquals(TX_ID, row().transactionId)
            } finally {
                adapter.close()
                clearFixture()
            }
        }
    }

    @Test
    fun startup_restoreOfflineAndReadyReentry_defersUntilReadyAndRetriesOnce() = runTest {
        seedOperation()
        sdk.state.value = BeamWalletState.Restoring(BeamRestoreProgress(
            BeamRestorePhase.ScanningWalletOutputs, currentHeight = 1, targetHeight = 2,
        ))
        val adapter = startupAdapter()
        try {
            runCurrent()
            assertTrue(sdk.recoveries == 0)
            sdk.state.value = BeamWalletState.Offline(1)
            runCurrent()
            assertTrue(sdk.recoveries == 0)
            sdk.state.value = BeamWalletState.Ready(2)
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
            sdk.state.value = BeamWalletState.Offline(2)
            runCurrent()
            val observations = sdk.recoveries
            sdk.state.value = BeamWalletState.Ready(2)
            runCurrent()
            assertTrue(sdk.recoveries > observations)
            assertEquals(listOf(OPERATION_ID), sdk.commits)
        } finally { adapter.close() }
    }

    @Test
    fun startup_terminalTransactionReorg_reactivatesWithoutRebroadcast() = runTest {
        seedOperation(
            BeamSendResolution.Terminal(TX_ID, BeamTransactionStatus.Completed),
        )
        sdk.core[TX_ID] = transaction(status = BeamTransactionStatus.Completed)
        val adapter = startupAdapter()
        try {
            runCurrent()
            assertTrue(row().resolution is BeamSendResolution.Terminal)
            sdk.resolutions[OPERATION_ID] = BeamSendResolution.Submitted(TX_ID)
            sdk.core[TX_ID] = transaction()
            sdk.transactions.value = sdk.core.values.toList()
            runCurrent()
            assertEquals(BeamSendResolution.Submitted(TX_ID), row().resolution)
            assertTrue(sdk.commits.isEmpty())
        } finally { adapter.close() }
    }

    @Test
    fun startup_duplicateHistoryTriggers_conflatesWithoutOverlappingRunners() = runTest {
        seedOperation()
        val gate = CompletableDeferred<Unit>()
        sdk.beforeRecovery = { gate.await() }
        val adapter = startupAdapter()
        try {
            runCurrent()
            assertEquals(1, sdk.recoveries)
            repeat(20) {
                sdk.transactions.value = listOf(transaction().copy(createdAtEpochSeconds = it.toLong()))
                runCurrent()
                assertEquals(1, sdk.recoveries)
            }
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
            // One initial recovery and one conflated follow-up.
            assertEquals(2, sdk.recoveries)
        } finally { gate.complete(Unit); adapter.close() }
    }

    @Test
    fun startup_connectivityLossDuringCommit_cancelsAndRecoversSameOperationOnResume() = runTest {
        seedOperation()
        sdk.atCommitBoundary = { awaitCancellation() }
        val adapter = startupAdapter()
        try {
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
            assertEquals(1, monitoringLeases)
            connected.value = false
            runCurrent()
            assertEquals(0, monitoringLeases)
            assertTrue(adapter.isNetworkPaused)
            assertEquals(BeamSendResolution.Committing(TX_ID), row().resolution)
            assertEquals(0, sdk.registrations)
            sdk.atCommitBoundary = {}
            connected.value = true
            runCurrent()
            assertEquals(listOf(OPERATION_ID, OPERATION_ID), sdk.commits)
            assertEquals(1, sdk.registrations)
        } finally { adapter.close() }
    }

    @Test
    fun startup_pauseDuringObservation_drainsWithoutLeaseStopDeadlock() = runTest {
        seedOperation()
        sdk.beforeRecovery = { awaitCancellation() }
        val adapter = startupAdapter()
        try {
            runCurrent()
            val pause = async { adapter.pauseNetworkAndAwait() }
            runCurrent()
            assertTrue(pause.isCompleted)
            pause.await()
            assertEquals(0, monitoringLeases)
            assertTrue(sdk.commits.isEmpty())
            sdk.beforeRecovery = {}
            adapter.resumeNetwork()
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
        } finally { adapter.close() }
    }

    @Test
    fun startup_accountSwitchDuringNativeDrain_waitsWithoutBlockingStopCaller() = runTest {
        seedOperation()
        val gate = CompletableDeferred<Unit>()
        sdk.atCommitBoundary = { withContext(NonCancellable) { gate.await() } }
        val adapter = startupAdapter()
        runCurrent()
        adapter.stop()
        val close = async { adapter.close() }
        runCurrent()
        assertFalse(close.isCompleted)
        assertEquals(0, sdk.closes)
        gate.complete(Unit)
        close.await()
        assertEquals(0, monitoringLeases)
        openSession("replacement")
        val replacement = session
        sdk.transactions.value = listOf(transaction())
        runCurrent()
        assertTrue(owner.current === replacement)
        assertEquals(listOf(OPERATION_ID), sdk.commits)
        owner.close()
    }

    @Test
    fun startup_indeterminateEvidence_waitsForExternalTriggerWithoutAppReplay() = runTest {
        seedOperation(BeamSendResolution.Indeterminate(TX_ID))
        val adapter = startupAdapter()
        try {
            runCurrent()
            assertEquals(BeamSendResolution.Indeterminate(TX_ID), row().resolution)
            assertTrue(sdk.commits.isEmpty())
            val initialObservations = sdk.recoveries
            assertTrue(initialObservations in 1..2)
            runCurrent()
            assertEquals(initialObservations, sdk.recoveries)
            sdk.resolutions[OPERATION_ID] = BeamSendResolution.Submitted(TX_ID)
            sdk.core[TX_ID] = transaction()
            sdk.transactions.value = sdk.core.values.toList()
            runCurrent()
            assertEquals(initialObservations + 1, sdk.recoveries)
            assertTrue(sdk.commits.isEmpty())
        } finally { adapter.close() }
    }

    @Test
    fun startup_readyLeavesDuringObservation_cancelsUntilReadyReturns() = runTest {
        seedOperation()
        sdk.beforeRecovery = { awaitCancellation() }
        val adapter = startupAdapter()
        try {
            runCurrent()
            sdk.state.value = BeamWalletState.Syncing(1, 2)
            runCurrent()
            assertEquals(0, monitoringLeases)
            assertTrue(sdk.commits.isEmpty())
            assertEquals(BeamSendResolution.Prepared(TX_ID), row().resolution)
            sdk.beforeRecovery = {}
            sdk.state.value = BeamWalletState.Ready(2)
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
        } finally { adapter.close() }
    }

    @Test
    fun startup_backgroundDuringCommit_holdsCriticalLeaseUntilHandoffFinishes() = runTest {
        seedOperation()
        val gate = CompletableDeferred<Unit>()
        sdk.beforeCommit = { gate.await() }
        val adapter = startupAdapter()
        try {
            runCurrent()
            val stopsBeforeBackground = sdk.stops
            foreground.value = BackgroundManagerState.EnterBackground
            runCurrent()
            assertEquals(1, monitoringLeases)
            assertEquals(stopsBeforeBackground, sdk.stops)
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
            assertEquals(0, monitoringLeases)
            assertTrue(adapter.isNetworkPaused)
        } finally { gate.complete(Unit); adapter.close() }
    }

    @Test
    fun startup_nativeAdmissionDeferred_retriesOnHistoryChangeWithoutNewPreparation() = runTest {
        seedOperation()
        sdk.beforeCommit = { throw BeamFailure.SendAdmissionDeferred("private-token", secretFailure()) }
        val adapter = startupAdapter()
        try {
            runCurrent()
            assertTrue(sdk.commits.isEmpty())
            assertEquals(BeamSendResolution.Prepared(TX_ID), row().resolution)
            sdk.beforeCommit = {}
            sdk.transactions.value = listOf(transaction())
            runCurrent()
            assertEquals(listOf(OPERATION_ID), sdk.commits)
            assertTrue(sdk.prepares.isEmpty())
        } finally { adapter.close() }
    }

    private suspend fun seedOperation(
        resolution: BeamSendResolution = BeamSendResolution.Prepared(TX_ID),
    ) {
        openSession()
        sdk.operations[OPERATION_ID] = BeamSendOperation(
            OPERATION_ID, TX_ID, "a".repeat(64), 100, 10, resolution,
        )
        sdk.resolutions[OPERATION_ID] = resolution
        if (resolution is BeamSendResolution.Submitted) sdk.core[TX_ID] = transaction()
    }

    private fun TestScope.startupAdapter(): BeamAdapter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
        val lifecycle = BeamLifecycleCoordinator(
            mockk { every { stateFlow } returns foreground },
            mockk { every { keepAliveBlockchains } returns MutableStateFlow(emptySet()) },
            mockk {
                every { isConnected } returns connected
                every { acquireMonitoringLease() } answers {
                    monitoringLeases++
                    AutoCloseable { monitoringLeases-- }
                }
                coEvery { refreshAndAwaitValidation() } answers { connected.value }
            },
            mockk {
                every { effectiveFlow } returns MutableStateFlow(emptySet())
                every { stateFlow } returns MutableStateFlow(emptyMap())
                every { isNetworkPaused(any()) } returns false
            },
        )
        return BeamAdapter(owner, session, dispatchers, mockk(relaxed = true), lifecycle, coordinator)
            .also { it.start() }
    }

}
