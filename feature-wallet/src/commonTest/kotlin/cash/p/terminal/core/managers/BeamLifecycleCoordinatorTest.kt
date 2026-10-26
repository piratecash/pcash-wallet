package cash.p.terminal.core.managers

import cash.p.terminal.entities.OfflineBlockchain
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BeamLifecycleCoordinatorTest {
    private val foreground = MutableStateFlow(BackgroundManagerState.Unknown)
    private val keepAlive = MutableStateFlow<Set<BlockchainType>>(emptySet())
    private val connected = MutableStateFlow(true)
    private val offline = MutableStateFlow<Set<OfflineKey>>(emptySet())
    private val persisted = MutableStateFlow<Map<OfflineKey, OfflineBlockchain>>(emptyMap())
    private var monitoringLeases = 0
    private val transitions = mutableListOf<Boolean>()
    private val coordinator = BeamLifecycleCoordinator(
        mockk { every { stateFlow } returns foreground },
        mockk { every { keepAliveBlockchains } returns keepAlive },
        mockk {
            every { isConnected } returns connected
            coEvery { refreshAndAwaitValidation() } answers { connected.value }
            every { acquireMonitoringLease() } answers {
                monitoringLeases++
                AutoCloseable { monitoringLeases-- }
            }
        },
        mockk {
            every { effectiveFlow } returns offline
            every { stateFlow } returns persisted
            every { isNetworkPaused(any()) } answers { firstArg<OfflineKey>() in offline.value }
        },
    )

    @Test
    fun retry_failedTransition_rearmsOnceAndSharesPendingStartup() = runTest {
        foreground.value = BackgroundManagerState.EnterForeground
        val failed = CompletableDeferred<Unit>().also { it.cancel() }
        val retry = CompletableDeferred<Unit>()
        val binding = coordinator.bind("account", backgroundScope) { enabled ->
            transitions += enabled
            if (transitions.size == 1) failed else retry
        }
        binding.resume()
        val first = async { binding.retry { true } }
        val second = async { binding.retry { true } }
        runCurrent()
        assertEquals(listOf(true, true), transitions)
        assertFalse(first.isCompleted)
        first.cancelAndJoin()
        assertFalse(retry.isCancelled)
        retry.complete(Unit)
        second.await()
        binding.retry { false }
        assertEquals(listOf(true, true), transitions)
    }

    @Test
    fun retry_noDemandOrManualVetoOrDisconnectedOrStopped_doesNotRestart() = runTest {
        val binding = bind()
        binding.resume()
        binding.retry { true }
        foreground.value = BackgroundManagerState.EnterForeground
        runCurrent()
        connected.value = false
        binding.retry { true }
        runCurrent()
        connected.value = true
        val key = OfflineKey("account", BlockchainType.Beam)
        persisted.value = mapOf(
            key to OfflineBlockchain("account", BlockchainType.Beam, true, null, null)
        )
        binding.retry { true }
        runCurrent()
        binding.pause()
        persisted.value = emptyMap()
        binding.retry { true }
        runCurrent()
        binding.stop()
        binding.retry { true }
        assertEquals(listOf(false, true, false, false), transitions)
    }

    @Test
    fun retry_criticalLeaseKeepsDemandUntilRelease_thenStops() = runTest {
        val binding = bind()
        binding.withLease(BeamLifecycleCoordinator.Lease.CriticalOperation) {
            binding.retry { true }
            assertTrue(binding.canRun)
            assertEquals(1, monitoringLeases)
        }
        binding.retry { true }
        assertEquals(listOf(true, true, false), transitions)
        assertEquals(0, monitoringLeases)
    }

    @Test
    fun resume_foregroundAndBackground_onlyRunsWithDemand() = runTest {
        val binding = bind()
        binding.resume()
        assertEquals(listOf(false), transitions)
        foreground.value = BackgroundManagerState.EnterForeground
        runCurrent()
        foreground.value = BackgroundManagerState.EnterBackground
        runCurrent()
        foreground.value = BackgroundManagerState.EnterForeground
        runCurrent()
        assertEquals(listOf(false, true, false, true), transitions)
    }

    @Test
    fun connectivity_lossAndValidatedReturn_stopsAndRestartsExistingLease() = runTest {
        foreground.value = BackgroundManagerState.EnterForeground
        val binding = bind()
        binding.resume()
        connected.value = false
        runCurrent()
        assertFalse(binding.canRun)
        connected.value = true
        runCurrent()
        assertTrue(binding.canRun)
        assertEquals(listOf(true, false, true), transitions)
    }

    @Test
    fun manualOffline_allLeaseKinds_cannotOverrideVeto() = runTest {
        foreground.value = BackgroundManagerState.EnterForeground
        keepAlive.value = setOf(BlockchainType.Beam)
        val binding = bind()
        binding.resume()
        offline.value = setOf(OfflineKey("account", BlockchainType.Beam))
        runCurrent()
        binding.withLease(BeamLifecycleCoordinator.Lease.Polling) {
            binding.withLease(BeamLifecycleCoordinator.Lease.CriticalOperation) {
                binding.resume()
                assertFalse(binding.canRun)
            }
        }
        assertEquals(listOf(true, false), transitions)
        offline.value = emptySet()
        runCurrent()
        assertEquals(listOf(true, false, true), transitions)
    }

    @Test
    fun persistedManualOffline_overridesTemporaryOnline() = runTest {
        foreground.value = BackgroundManagerState.EnterForeground
        val key = OfflineKey("account", BlockchainType.Beam)
        persisted.value = mapOf(
            key to OfflineBlockchain("account", BlockchainType.Beam, true, null, null)
        )
        val binding = bind()
        binding.resume()
        binding.withLease(BeamLifecycleCoordinator.Lease.Polling) {
            assertFalse(binding.canRun)
            assertEquals(1, monitoringLeases)
        }
        assertEquals(0, monitoringLeases)
        assertEquals(listOf(false), transitions)
    }

    @Test
    fun polling_unknownThenDestroyedActivities_keepsWorkerUntilRelease() = runTest {
        val binding = bind()
        val worker = launch {
            binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() }
        }
        runCurrent()
        assertEquals(listOf(true), transitions)
        foreground.value = BackgroundManagerState.AllActivitiesDestroyed
        runCurrent()
        assertTrue(worker.isActive)
        assertEquals(listOf(true), transitions)
        worker.cancelAndJoin()
        assertEquals(listOf(true, false), transitions)
    }

    @Test
    fun polling_overlappingPollsAndNotification_onlyLastLeaseStopsNetwork() = runTest {
        val binding = bind()
        val first = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        val second = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        runCurrent()
        keepAlive.value = setOf(BlockchainType.Beam)
        runCurrent()
        first.cancelAndJoin()
        second.cancelAndJoin()
        assertEquals(listOf(true), transitions)
        keepAlive.value = emptySet()
        runCurrent()
        assertEquals(listOf(true, false), transitions)
    }

    @Test
    fun polling_cancellationDuringStartup_awaitsLastLeaseStop() = runTest {
        val start = CompletableDeferred<Unit>()
        val stop = CompletableDeferred<Unit>()
        val binding = coordinator.bind("account", backgroundScope) { enabled ->
            transitions += enabled
            if (enabled) start else stop
        }
        val worker = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        runCurrent()
        worker.cancel()
        runCurrent()
        assertFalse(worker.isCompleted)
        assertEquals(listOf(true, false), transitions)
        stop.complete(Unit)
        worker.join()
        assertTrue(worker.isCancelled)
    }

    @Test
    fun polling_cancellationAfterConnectivityLoss_awaitsExistingStop() = runTest {
        val stop = CompletableDeferred<Unit>()
        val binding = coordinator.bind("account", backgroundScope) { enabled ->
            transitions += enabled
            if (enabled) CompletableDeferred(Unit) else stop
        }
        val worker = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        runCurrent()
        connected.value = false
        runCurrent()
        assertEquals(listOf(true, false), transitions)
        worker.cancel()
        runCurrent()
        assertFalse(worker.isCompleted)
        stop.complete(Unit)
        worker.join()
        assertEquals(0, monitoringLeases)
    }

    @Test
    fun polling_cancelOneDuringSharedStartup_doesNotAwaitOtherLease() = runTest {
        val start = CompletableDeferred<Unit>()
        val binding = coordinator.bind("account", backgroundScope) { enabled ->
            transitions += enabled
            if (enabled) start else CompletableDeferred(Unit)
        }
        val first = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        val second = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        runCurrent()
        first.cancelAndJoin()
        assertTrue(second.isActive)
        assertEquals(listOf(true), transitions)
        second.cancelAndJoin()
        assertEquals(listOf(true, false), transitions)
    }

    @Test
    fun stop_activeWorkerAndLatePolicyUpdates_cancelsWorkerWithoutRestart() = runTest {
        val binding = bind()
        val worker = launch { binding.withLease(BeamLifecycleCoordinator.Lease.Polling) { awaitCancellation() } }
        runCurrent()
        binding.stop()
        runCurrent()
        assertTrue(worker.isCancelled)
        foreground.value = BackgroundManagerState.EnterForeground
        binding.resume()
        runCurrent()
        assertFalse(binding.canRun)
        assertEquals(listOf(true), transitions)
    }

    @Test
    fun stop_criticalDurableHandoff_drainsBeforeCloseMayProceed() = runTest {
        val binding = bind()
        val handoff = CompletableDeferred<Unit>()
        val operation = launch {
            binding.withLease(BeamLifecycleCoordinator.Lease.CriticalOperation) {
                withContext(NonCancellable) { handoff.await() }
            }
        }
        runCurrent()
        binding.stop()
        val drain = async { binding.awaitOperations() }
        runCurrent()
        assertFalse(drain.isCompleted)
        handoff.complete(Unit)
        drain.await()
        operation.join()
        assertTrue(operation.isCancelled)
    }

    private fun TestScope.bind() = coordinator.bind("account", backgroundScope) { enabled ->
        transitions += enabled
        CompletableDeferred(Unit)
    }
}
