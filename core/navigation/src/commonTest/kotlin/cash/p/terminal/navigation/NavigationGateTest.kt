package cash.p.terminal.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class NavigationGateTest {

    private val timeSource = TestTimeSource()
    private val gate = NavigationGate(650.milliseconds, timeSource)

    @Test
    fun acquire_withinCooldown_isRejectedUntilCooldownPasses() {
        assertTrue(gate.acquire())
        timeSource += 649.milliseconds
        assertFalse(gate.acquire())
        timeSource += 1.milliseconds
        assertTrue(gate.acquire())
    }

    @Test
    fun runIfAllowed_navigationDidNotHappen_releasesGate() {
        assertFalse(gate.runIfAllowed { false })
        assertTrue(gate.runIfAllowed { true })
        assertFalse(gate.runIfAllowed { true })
    }
}
