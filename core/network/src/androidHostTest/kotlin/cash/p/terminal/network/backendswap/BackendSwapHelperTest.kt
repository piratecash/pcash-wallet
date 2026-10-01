package cash.p.terminal.network.backendswap

import cash.p.terminal.network.backendswap.api.BackendSwapHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendSwapHelperTest {

    @Test
    fun trackUrl_changelly_returnsChangellyTrackUrl() {
        val result = BackendSwapHelper.trackUrl("changelly", "ext-1")

        assertEquals("changelly.com" to "https://changelly.com/track/ext-1", result)
    }

    @Test
    fun trackUrl_unknownProvider_returnsNull() {
        assertNull(BackendSwapHelper.trackUrl("unknown", "ext-1"))
    }

    @Test
    fun trackUrl_nullProvider_returnsNull() {
        assertNull(BackendSwapHelper.trackUrl(null, "ext-1"))
    }

    @Test
    fun trackUrl_blankExternalId_returnsNull() {
        assertNull(BackendSwapHelper.trackUrl("changelly", " "))
    }

    @Test
    fun trackUrl_nullExternalId_returnsNull() {
        assertNull(BackendSwapHelper.trackUrl("changelly", null))
    }
}
