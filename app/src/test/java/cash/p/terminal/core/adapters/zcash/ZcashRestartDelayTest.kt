package cash.p.terminal.core.adapters.zcash

import org.junit.Assert.assertEquals
import org.junit.Test

class ZcashRestartDelayTest {

    @Test
    fun zcashRestartDelayFor_growingAttempt_doublesUpToTheCap() {
        assertEquals(20L, zcashRestartDelayFor(0, 20L, 100L))
        assertEquals(40L, zcashRestartDelayFor(1, 20L, 100L))
        assertEquals(80L, zcashRestartDelayFor(2, 20L, 100L))
        assertEquals(100L, zcashRestartDelayFor(3, 20L, 100L))
        assertEquals(100L, zcashRestartDelayFor(4, 20L, 100L))
    }
}
