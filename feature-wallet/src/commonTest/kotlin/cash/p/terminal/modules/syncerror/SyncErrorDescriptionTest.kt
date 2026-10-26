package cash.p.terminal.modules.syncerror

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.balance_sync_error_changeable_source
import cash.p.terminal.resources.balance_sync_error_fixed_source
import cash.p.terminal.resources.source_blocked_by_provider_error
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncErrorDescriptionTest {

    @Test
    fun syncErrorDescriptionRes_evmBlocked_returnsBlockedText() {
        assertEquals(
            Res.string.source_blocked_by_provider_error,
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
            Res.string.balance_sync_error_changeable_source,
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
            Res.string.source_blocked_by_provider_error,
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
            Res.string.balance_sync_error_fixed_source,
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
            Res.string.balance_sync_error_changeable_source,
            syncErrorDescriptionRes(
                isMonero = false,
                isEvm = false,
                isBlockedByProvider = false,
                sourceChangeable = true,
            )
        )
    }
}
