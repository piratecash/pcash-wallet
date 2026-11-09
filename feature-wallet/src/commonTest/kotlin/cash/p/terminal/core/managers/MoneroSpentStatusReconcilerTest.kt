package cash.p.terminal.core.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroSpentStatusReconcilerTest {

    @Test
    fun activate_replacementSession_staleSessionIsNoLongerActive() {
        val reconciler = MoneroSpentStatusReconciler()
        val staleSession = reconciler.activate()

        val currentSession = reconciler.activate()

        assertFalse(reconciler.isActive(staleSession))
        assertTrue(reconciler.isActive(currentSession))
    }

    @Test
    fun deactivate_activeSession_leavesNoSessionActive() {
        val reconciler = MoneroSpentStatusReconciler()
        val session = reconciler.activate()

        reconciler.deactivate()

        assertFalse(reconciler.isActive(session))
        assertNull(reconciler.activeSession())
    }
}
