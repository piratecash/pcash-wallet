package cash.p.terminal.modules.syncerror

import cash.p.terminal.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncErrorDescriptionTest {

    @Test
    fun syncErrorDescriptionRes_evmBlocked_returnsBlockedText() {
        assertEquals(
            R.string.source_blocked_by_provider_error,
            syncErrorDescriptionRes(
                isMonero = false,
                isEvm = true,
                isBlockedByProvider = true,
                sourceChangeable = true,
            )
        )
    }

    @Test
    fun syncErrorDescriptionRes_evmTimeout_returnsChangeableText() {
        assertEquals(
            R.string.balance_sync_error_changeable_source,
            syncErrorDescriptionRes(
                isMonero = false,
                isEvm = true,
                isBlockedByProvider = false,
                sourceChangeable = true,
            )
        )
    }

    @Test
    fun syncErrorDescriptionRes_moneroWithoutBlock_returnsBlockedText() {
        assertEquals(
            R.string.source_blocked_by_provider_error,
            syncErrorDescriptionRes(
                isMonero = true,
                isEvm = false,
                isBlockedByProvider = false,
                sourceChangeable = false,
            )
        )
    }

    @Test
    fun syncErrorDescriptionRes_tronBlocked_returnsFixedText() {
        assertEquals(
            R.string.balance_sync_error_fixed_source,
            syncErrorDescriptionRes(
                isMonero = false,
                isEvm = false,
                isBlockedByProvider = true,
                sourceChangeable = false,
            )
        )
    }

    @Test
    fun syncErrorDescriptionRes_bitcoinChangeable_returnsChangeableText() {
        assertEquals(
            R.string.balance_sync_error_changeable_source,
            syncErrorDescriptionRes(
                isMonero = false,
                isEvm = false,
                isBlockedByProvider = false,
                sourceChangeable = true,
            )
        )
    }
}
