package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.manager.IConnectivityManager
import io.horizontalsystems.bitcoincore.core.IConnectionManagerListener
import io.horizontalsystems.core.BackgroundManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class BitcoinKitConnectionManagerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val state = MutableStateFlow(false)
    private val connectivityManager = mockk<IConnectivityManager> {
        every { isConnected } returns state
        every { refresh() } answers { state.value }
    }
    private val backgroundManager = mockk<BackgroundManager> {
        every { inForeground } returns false
    }
    private val manager = BitcoinKitConnectionManager(
        connectivityManager,
        backgroundManager,
        TestDispatcherProvider(dispatcher, scope),
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun addListener_currentAndChangedState_deliversMonotonicValues() {
        val values = mutableListOf<Boolean>()
        val listener = object : IConnectionManagerListener {
            override fun onConnectionChange(isConnected: Boolean) {
                values.add(isConnected)
            }
        }

        manager.addListener(listener)
        state.value = true
        scheduler.runCurrent()

        assertEquals(listOf(false, true), values)
    }

    @Test
    fun addListener_sameInstanceTwice_doesNotDuplicateCallbacks() {
        val listener = mockk<IConnectionManagerListener>(relaxed = true)

        manager.addListener(listener)
        manager.addListener(listener)
        state.value = true
        scheduler.runCurrent()

        verify(exactly = 1) { listener.onConnectionChange(false) }
        verify(exactly = 1) { listener.onConnectionChange(true) }
    }

    @Test
    fun removeListener_followingStateChange_doesNotNotifyRemovedListener() {
        val listener = mockk<IConnectionManagerListener>(relaxed = true)
        manager.addListener(listener)

        manager.removeListener(listener)
        state.value = true
        scheduler.runCurrent()

        verify(exactly = 1) { listener.onConnectionChange(false) }
        verify(exactly = 0) { listener.onConnectionChange(true) }
    }

    @Test
    fun connectionChange_throwingListener_keepsCollectorAndOtherListenersActive() {
        val throwingListener = object : IConnectionManagerListener {
            override fun onConnectionChange(isConnected: Boolean) {
                check(!isConnected) { "listener failed" }
            }
        }
        val values = mutableListOf<Boolean>()
        val recordingListener = object : IConnectionManagerListener {
            override fun onConnectionChange(isConnected: Boolean) {
                values.add(isConnected)
            }
        }
        manager.addListener(throwingListener)
        manager.addListener(recordingListener)

        state.value = true
        scheduler.runCurrent()
        state.value = false
        scheduler.runCurrent()

        assertEquals(listOf(false, true, false), values)
    }

    @Test
    fun onEnterForeground_backgroundPoll_usesFreshConnectivitySynchronously() {
        val listener = mockk<IConnectionManagerListener>(relaxed = true)
        manager.addListener(listener)
        every { connectivityManager.refresh() } returns true

        manager.onEnterForeground()

        assertTrue(manager.isConnected)
        verify(exactly = 1) { connectivityManager.refresh() }
        verify(exactly = 1) { listener.onConnectionChange(true) }
    }

    @Test
    fun onEnterForeground_activityLifecycle_doesNotDuplicateSharedRefresh() {
        every { backgroundManager.inForeground } returns true

        manager.onEnterForeground()

        verify(exactly = 0) { connectivityManager.refresh() }
    }

    @Test
    fun addListener_connectionChangesDuringInitialCallback_deliversInitialValueFirst() {
        val executorDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val raceScope = CoroutineScope(SupervisorJob() + executorDispatcher)
        val raceState = MutableStateFlow(false)
        val raceConnectivityManager = mockk<IConnectivityManager> {
            every { isConnected } returns raceState
        }
        val raceManager = BitcoinKitConnectionManager(
            raceConnectivityManager,
            backgroundManager,
            TestDispatcherProvider(executorDispatcher, raceScope),
        )
        val initialCallbackStarted = CountDownLatch(1)
        val releaseInitialCallback = CountDownLatch(1)
        val changedCallbackReceived = CountDownLatch(1)
        val values = Collections.synchronizedList(mutableListOf<Boolean>())
        val listener = object : IConnectionManagerListener {
            override fun onConnectionChange(isConnected: Boolean) {
                values.add(isConnected)
                if (isConnected) {
                    changedCallbackReceived.countDown()
                } else {
                    initialCallbackStarted.countDown()
                    releaseInitialCallback.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
        }

        try {
            val addThread = Thread { raceManager.addListener(listener) }
            addThread.start()
            assertTrue(initialCallbackStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            raceState.value = true
            releaseInitialCallback.countDown()

            addThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
            assertTrue(changedCallbackReceived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertEquals(listOf(false, true), values)
        } finally {
            releaseInitialCallback.countDown()
            raceScope.cancel()
            executorDispatcher.close()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 2L
    }
}
