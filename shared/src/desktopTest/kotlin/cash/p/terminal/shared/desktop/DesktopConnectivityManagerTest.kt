package cash.p.terminal.shared.desktop

import cash.p.terminal.core.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopConnectivityManagerTest {

    private class FakeProbe(var names: Set<String>) : NetworkInterfaceProbe {
        override fun activeInterfaceNames() = names
    }

    private val probe = FakeProbe(setOf("en0"))

    @Test
    fun init_activeInterface_isConnected() = runTest {
        val manager = createManager()

        assertTrue(manager.isConnected.value)
        assertEquals(setOf<Any>("en0"), manager.validatedNetworksFlow.value)
    }

    @Test
    fun init_noActiveInterface_isDisconnected() = runTest {
        probe.names = emptySet()

        assertFalse(createManager().isConnected.value)
    }

    @Test
    fun poll_interfaceGoesDown_disconnectsAndEmits() = runTest {
        val manager = createManager()
        val emitted = collectAvailability(manager)

        probe.names = emptySet()
        advanceTimeBy(POLL_INTERVAL_MS + 1)
        runCurrent()

        assertFalse(manager.isConnected.value)
        assertEquals(listOf(false), emitted)
    }

    @Test
    fun refresh_interfaceComesUp_connectsAtOnce() = runTest {
        probe.names = emptySet()
        val manager = createManager()
        val emitted = collectAvailability(manager)

        probe.names = setOf("wlan0")

        assertTrue(manager.refresh())
        assertTrue(manager.isConnected.value)
        assertEquals(listOf(true), emitted)
    }

    @Test
    fun refresh_stateUnchanged_doesNotEmit() = runTest {
        val manager = createManager()
        val emitted = collectAvailability(manager)

        manager.refresh()

        assertEquals(emptyList(), emitted)
    }

    private fun TestScope.createManager() = DesktopConnectivityManager(
        probe,
        TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
    )

    private fun TestScope.collectAvailability(manager: DesktopConnectivityManager): List<Boolean> {
        val emitted = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.networkAvailabilityFlow.toList(emitted)
        }
        return emitted
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
