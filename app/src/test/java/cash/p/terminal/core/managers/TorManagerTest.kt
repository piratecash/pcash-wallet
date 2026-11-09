package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.tor.TorControlClient
import cash.p.terminal.core.tor.TorServiceGateway
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.modules.settings.security.tor.TorStatus
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IBackgroundManager
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class TorManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val gateway = mockk<TorServiceGateway>(relaxed = true)
    private val client = mockk<TorControlClient>(relaxed = true)
    private val connectivityManager = mockk<IConnectivityManager>(relaxed = true)
    private val backgroundManager = mockk<IBackgroundManager>()
    private val uptimeProvider = mockk<UptimeProvider>()
    private val lease = mockk<AutoCloseable>(relaxed = true)

    private val gatewayEvents = MutableSharedFlow<TorServiceGateway.Event>()
    private val appState = MutableStateFlow(BackgroundManagerState.Unknown)
    private val validatedNetworks = MutableStateFlow<Set<Any>>(emptySet())
    private var torRunning = false

    // Blocking control calls need a real thread, so a hang can be interrupted by the timeout
    private val ioExecutor = Executors.newCachedThreadPool()
    private val realIo = ioExecutor.asCoroutineDispatcher()
    private val hangStarted = CountDownLatch(1)
    private val hangInterrupted = AtomicBoolean(false)

    private var savedHost: String? = null
    private var savedPort: String? = null

    @Before
    fun setUp() {
        savedHost = System.getProperty(HOST_PROPERTY)
        savedPort = System.getProperty(PORT_PROPERTY)
        System.clearProperty(HOST_PROPERTY)
        System.clearProperty(PORT_PROPERTY)

        every { localStorage.torEnabled } returns true
        every { gateway.events } returns gatewayEvents
        every { gateway.isTorRunning } answers { torRunning }
        every { gateway.bind() } answers { torRunning = true; true }
        every { gateway.openControlClient() } returns client
        every { gateway.controlClient() } returns client
        every { client.bootstrapProgress() } returns 100
        every { client.hasBuiltCircuit() } returns true
        every { connectivityManager.validatedNetworksFlow } returns validatedNetworks
        every { connectivityManager.acquireMonitoringLease() } returns lease
        every { backgroundManager.stateFlow } returns appState
        every { uptimeProvider.uptime } answers { dispatcher.scheduler.currentTime }
    }

    @After
    fun tearDown() {
        ioExecutor.shutdownNow()
        restoreProperty(HOST_PROPERTY, savedHost)
        restoreProperty(PORT_PROPERTY, savedPort)
    }

    @Test
    fun init_torEnabled_setsSocksPropsBeforeConnected() = runTest(dispatcher) {
        val manager = createManager()

        assertEquals("127.0.0.1", System.getProperty(HOST_PROPERTY))
        assertTrue(socksPort() > 0)
        assertEquals(TorStatus.Closed, manager.torStatusFlow.value)
    }

    @Test
    fun init_torDisabled_leavesPropsUnset() = runTest(dispatcher) {
        every { localStorage.torEnabled } returns false

        val manager = createManager()
        advanceUntilIdle()

        assertNull(System.getProperty(HOST_PROPERTY))
        assertNull(System.getProperty(PORT_PROPERTY))
        assertEquals(TorStatus.Closed, manager.torStatusFlow.value)
        verify(exactly = 0) { gateway.bind() }
        verify(exactly = 0) { connectivityManager.acquireMonitoringLease() }
    }

    @Test
    fun start_bootstrapThenCircuit_opensSocksAndReportsConnected() = runTest(dispatcher) {
        every { client.bootstrapProgress() } returnsMany listOf(40, 100)
        every { client.hasBuiltCircuit() } returnsMany listOf(false, true)

        val manager = createManager()
        runCurrent()
        assertEquals(TorStatus.Connecting, manager.torStatusFlow.value)
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verifyOrder {
            gateway.bind()
            client.authenticate()
            client.bootstrapProgress()
            client.hasBuiltCircuit()
            client.openSocks(socksPort())
        }
    }

    @Test
    fun start_bootstrapTimeout_reportsFailedAndSocksClosed() = runTest(dispatcher) {
        every { client.bootstrapProgress() } returns 10

        val manager = createManager()
        advanceTimeBy(BOOTSTRAP_WAIT_MILLIS - 1_000)
        assertEquals(TorStatus.Connecting, manager.torStatusFlow.value)
        advanceUntilIdle()

        assertEquals(TorStatus.Failed, manager.torStatusFlow.value)
        verify { client.closeSocks() }
        verify { client.setNetworkEnabled(false) }
        verify(exactly = 0) { client.openSocks(any()) }
    }

    @Test
    fun start_afterBootstrapTimeout_reenablesTorNetwork() = runTest(dispatcher) {
        val manager = failedManager()

        manager.start()
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verifyOrder {
            client.setNetworkEnabled(false)
            client.setNetworkEnabled(true)
            client.openSocks(any())
        }
    }

    @Test
    fun start_bootstrapInProgress_reportsProgressThenClearsIt() = runTest(dispatcher) {
        every { client.bootstrapProgress() } returnsMany listOf(30, 60, 100)
        val manager = createManager()
        val progress = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.bootstrapProgressFlow.toList(progress)
        }

        advanceUntilIdle()

        assertEquals(listOf(null, 30, 60, 100, null), progress)
        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
    }

    @Test
    fun enterForeground_afterLongBackground_reconnectsAndClosesSocksUntilCircuit() = runTest(dispatcher) {
        val manager = connectedManager()
        appState.value = BackgroundManagerState.EnterBackground
        advanceTimeBy(RECONNECT_AFTER_BACKGROUND_MILLIS + 1_000)
        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        every { client.hasBuiltCircuit() } returns false

        appState.value = BackgroundManagerState.EnterForeground
        runCurrent()

        assertEquals(TorStatus.Connecting, manager.torStatusFlow.value)
        verifyOrder {
            client.closeSocks()
            client.setNetworkEnabled(false)
            client.setNetworkEnabled(true)
        }
        verify(exactly = 1) { client.openSocks(any()) }

        every { client.hasBuiltCircuit() } returns true
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verify(exactly = 2) { client.openSocks(socksPort()) }
        verify(exactly = 1) { gateway.bind() }
    }

    @Test
    fun enterForeground_shortBackground_doesNotReconnect() = runTest(dispatcher) {
        val manager = connectedManager()
        appState.value = BackgroundManagerState.EnterBackground
        advanceTimeBy(10_000)

        appState.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verify(exactly = 0) { client.setNetworkEnabled(any()) }
    }

    @Test
    fun enterForeground_whenFailed_restarts() = runTest(dispatcher) {
        val manager = failedManager()

        appState.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
    }

    @Test
    fun networkChanges_connectedTransientEmptySet_doesNotReconnect() = runTest(dispatcher) {
        validatedNetworks.value = setOf(NETWORK_A)
        val manager = connectedManager()

        validatedNetworks.value = emptySet()
        advanceUntilIdle()
        validatedNetworks.value = setOf(NETWORK_A)
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verify(exactly = 0) { client.setNetworkEnabled(any()) }
    }

    @Test
    fun networkChanges_connectedDifferentNetwork_reconnects() = runTest(dispatcher) {
        validatedNetworks.value = setOf(NETWORK_A)
        val manager = connectedManager()

        validatedNetworks.value = setOf(NETWORK_B)
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verify { client.setNetworkEnabled(false) }
        verify(exactly = 2) { client.openSocks(any()) }
    }

    @Test
    fun networkChanges_connecting_ignored() = runTest(dispatcher) {
        validatedNetworks.value = setOf(NETWORK_A)
        every { client.bootstrapProgress() } returns 10
        val manager = createManager()
        runCurrent()

        validatedNetworks.value = setOf(NETWORK_B)
        runCurrent()

        assertEquals(TorStatus.Connecting, manager.torStatusFlow.value)
        verify(exactly = 0) { client.setNetworkEnabled(any()) }
        verify(exactly = 1) { gateway.bind() }
    }

    @Test
    fun networkChanges_failedOfflineStartThenNetwork_starts() = runTest(dispatcher) {
        val manager = failedManager()

        validatedNetworks.value = setOf(NETWORK_A)
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
    }

    @Test
    fun networkChanges_failedDuringOutageSameNetwork_starts() = runTest(dispatcher) {
        validatedNetworks.value = setOf(NETWORK_A)
        every { client.bootstrapProgress() } returns 10
        val manager = createManager()
        runCurrent()
        validatedNetworks.value = emptySet()
        advanceUntilIdle()
        assertEquals(TorStatus.Failed, manager.torStatusFlow.value)
        every { client.bootstrapProgress() } returns 100

        validatedNetworks.value = setOf(NETWORK_A)
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
    }

    @Test
    fun stop_duringBootstrap_returnsImmediatelyClearsPropsNoControlCall() = runTest(dispatcher) {
        every { client.bootstrapProgress() } returns 10
        val manager = createManager()
        advanceTimeBy(5_000)
        clearMocks(client, gateway, answers = false)
        val stopCalledAt = currentTime

        manager.stop()

        assertEquals(stopCalledAt, currentTime)
        assertEquals(TorStatus.Closed, manager.torStatusFlow.value)
        assertNull(System.getProperty(HOST_PROPERTY))
        assertNull(System.getProperty(PORT_PROPERTY))
        verify { lease.close() }
        advanceUntilIdle()
        verify {
            client wasNot Called
            gateway wasNot Called
        }
    }

    @Test
    fun stop_whileControlCallHangs_returnsImmediately() = runTest(dispatcher) {
        every { client.bootstrapProgress() } answers { hang() }
        val manager = createManager(io = realIo)
        pumpUntil { hangStarted.count == 0L }
        val stopCalledAt = currentTime

        manager.stop()

        assertEquals(stopCalledAt, currentTime)
        assertEquals(TorStatus.Closed, manager.torStatusFlow.value)
        assertNull(System.getProperty(PORT_PROPERTY))
    }

    @Test
    fun controlCallHangs_timesOutAndReportsFailed() = runTest(dispatcher) {
        every { client.bootstrapProgress() } answers { hang() }
        val manager = createManager(io = realIo)
        pumpUntil { hangStarted.count == 0L }

        advanceTimeBy(CONTROL_CALL_TIMEOUT_MILLIS + 1)
        pumpUntil { manager.torStatusFlow.value == TorStatus.Failed }

        assertTrue(hangInterrupted.get())
    }

    @Test
    fun newCircuits_whileConnecting_returnsImmediatelyWithoutSignal() = runTest(dispatcher) {
        every { client.bootstrapProgress() } returns 10
        val manager = createManager()
        runCurrent()
        val calledAt = currentTime

        manager.newCircuits()

        assertEquals(calledAt, currentTime)
        runCurrent()
        verify(exactly = 0) { client.newCircuits() }
    }

    @Test
    fun newCircuits_failsAfterConnectionReplaced_keepsConnectedAndSocksOpen() = runTest(dispatcher) {
        val manager = connectedManager()
        val replacement = mockk<TorControlClient>(relaxed = true)
        every { client.newCircuits() } answers {
            every { gateway.controlClient() } returns replacement
            throw IOException("Interrupted")
        }

        manager.newCircuits()
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verify(exactly = 0) { replacement.closeSocks() }
    }

    @Test
    fun newCircuits_signalHangs_returnsAfterWaitLimit() = runTest(dispatcher) {
        val manager = createManager(io = realIo)
        pumpUntil { manager.torStatusFlow.value == TorStatus.Connected }
        every { client.newCircuits() } answers { hang() }
        val calledAt = currentTime

        manager.newCircuits()

        assertEquals(NEWNYM_WAIT_MILLIS, currentTime - calledAt)
        assertTrue(hangStarted.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun torExited_reportsFailed() = runTest(dispatcher) {
        val manager = connectedManager()

        torRunning = false
        gatewayEvents.emit(TorServiceGateway.Event.TorExited)
        advanceUntilIdle()

        assertEquals(TorStatus.Failed, manager.torStatusFlow.value)
        verify(exactly = 0) { client.closeSocks() }
    }

    @Test
    fun start_afterTorExited_rebindsService() = runTest(dispatcher) {
        val manager = connectedManager()
        torRunning = false
        gatewayEvents.emit(TorServiceGateway.Event.TorExited)
        advanceUntilIdle()

        manager.start()
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        verifyOrder {
            gateway.bind()
            gateway.unbind()
            gateway.bind()
        }
    }

    @Test
    fun openSocksFails_reportsFailedAndKeepsPort() = runTest(dispatcher) {
        every { client.openSocks(any()) } throws IOException("SETCONF rejected")
        val manager = createManager()
        val port = socksPort()
        advanceUntilIdle()
        assertEquals(TorStatus.Failed, manager.torStatusFlow.value)
        every { client.openSocks(any()) } returns Unit

        manager.start()
        advanceUntilIdle()

        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        assertEquals(port, socksPort())
        verify(exactly = 2) { client.openSocks(port) }
    }

    private fun TestScope.createManager(io: CoroutineDispatcher = dispatcher) = TorManager(
        localStorage = localStorage,
        gateway = gateway,
        connectivityManager = connectivityManager,
        backgroundManager = backgroundManager,
        uptimeProvider = uptimeProvider,
        dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope, io),
    )

    private fun TestScope.connectedManager(): TorManager {
        val manager = createManager()
        advanceUntilIdle()
        assertEquals(TorStatus.Connected, manager.torStatusFlow.value)
        return manager
    }

    private fun TestScope.failedManager(): TorManager {
        every { client.bootstrapProgress() } returns 10
        val manager = createManager()
        advanceUntilIdle()
        assertEquals(TorStatus.Failed, manager.torStatusFlow.value)
        every { client.bootstrapProgress() } returns 100
        return manager
    }

    // Work finished on a real io thread is resumed on the test dispatcher only once it is pumped
    private fun TestScope.pumpUntil(condition: () -> Boolean) {
        repeat(PUMP_TRIES) {
            runCurrent()
            if (condition()) return
            Thread.sleep(PUMP_INTERVAL_MILLIS)
        }
        fail("Condition not met in time")
    }

    // Mirrors jtorctl, which turns an interrupt of its blocked wait into an IOException
    private fun hang(): Nothing {
        hangStarted.countDown()
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            hangInterrupted.set(true)
        }
        throw IOException("Interrupted")
    }

    private fun socksPort(): Int = System.getProperty(PORT_PROPERTY)?.toIntOrNull() ?: -1

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }

    private companion object {
        const val HOST_PROPERTY = "socksProxyHost"
        const val PORT_PROPERTY = "socksProxyPort"
        const val BOOTSTRAP_WAIT_MILLIS = 120_000L
        const val RECONNECT_AFTER_BACKGROUND_MILLIS = 60_000L
        const val CONTROL_CALL_TIMEOUT_MILLIS = 10_000L
        const val NEWNYM_WAIT_MILLIS = 2_000L
        const val PUMP_TRIES = 500
        const val PUMP_INTERVAL_MILLIS = 10L
        val NETWORK_A = Any()
        val NETWORK_B = Any()
    }
}
