package cash.p.terminal.core.managers

import io.horizontalsystems.core.BackgroundManagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAppForegroundStateTest {

    @Test
    fun toForegroundOrNull_enterForeground_true() {
        assertEquals(true, BackgroundManagerState.EnterForeground.toForegroundOrNull())
    }

    @Test
    fun toForegroundOrNull_enterBackground_false() {
        assertEquals(false, BackgroundManagerState.EnterBackground.toForegroundOrNull())
    }

    @Test
    fun toForegroundOrNull_allActivitiesDestroyed_false() {
        assertEquals(false, BackgroundManagerState.AllActivitiesDestroyed.toForegroundOrNull())
    }

    @Test
    fun toForegroundOrNull_unknown_null() {
        assertNull(BackgroundManagerState.Unknown.toForegroundOrNull())
    }
}
