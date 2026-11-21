package cash.p.terminal.modules.transactions

import cash.p.terminal.entities.transactionrecords.TransactionRecord
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchPagingTest {

    private val paging = SearchPaging()
    private val visible: (TransactionRecord) -> Boolean = { false }

    @Test
    fun searchStateFor_stepTimedOutWithoutResult_pauses() {
        val state = paging.searchStateFor(emptyList(), batch(scannedCount = 1_234, timedOut = true), visible)

        assertEquals(SearchScanState.Paused(1_234), state)
        assertEquals(1_200, (state as SearchScanState.Paused).shownCount)
    }

    @Test
    fun searchStateFor_refreshAfterPause_staysPausedWithNewCount() {
        paging.searchStateFor(emptyList(), batch(scannedCount = 0, timedOut = true), visible)

        val state = paging.searchStateFor(emptyList(), batch(scannedCount = 600, refresh = true), visible)

        assertEquals(SearchScanState.Paused(600), state)
    }

    @Test
    fun searchStateFor_refreshAfterResults_doesNotResumeScanning() {
        paging.searchStateFor(listOf(mockk()), batch(scannedCount = 300), visible)

        val state = paging.searchStateFor(emptyList(), batch(scannedCount = 300, refresh = true), visible)

        assertEquals(SearchScanState.Finished, state)
    }

    @Test
    fun searchStateFor_refreshWithNewVisibleRowAfterPause_finishes() {
        paging.searchStateFor(emptyList(), batch(scannedCount = 5_000), visible)

        val state = paging.searchStateFor(listOf(mockk()), batch(scannedCount = 5_001, refresh = true), visible)

        assertEquals(SearchScanState.Finished, state)
    }

    private fun batch(scannedCount: Int, timedOut: Boolean = false, refresh: Boolean = false) = RecordsBatch(
        records = emptyList(),
        searchCompleted = true,
        searchScannedCount = scannedCount,
        searchSessionId = 1,
        searchTimedOut = timedOut,
        searchRefresh = refresh,
    )
}
