package cash.p.terminal.modules.transactions

import cash.p.terminal.entities.transactionrecords.TransactionRecord

/** Decides how paging continues after a batch: regular pages may be skipped, search batches never are. */
internal class SearchPaging {
    enum class Action { Publish, LoadNext, Ignore }

    private var lastHiddenOnlyLoadKey: String? = null

    // The auto-scan window lives for one repository search session; a new visible result reopens it.
    private var sessionId: Long? = null
    private var windowStart = 0
    private var lastScannedCount = 0
    private var lastState: SearchScanState = SearchScanState.Scanning

    fun reset() {
        lastHiddenOnlyLoadKey = null
    }

    fun openNextWindow() {
        windowStart = lastScannedCount
        lastState = SearchScanState.Scanning
    }

    fun actionFor(newRecords: List<TransactionRecord>, isHidden: (TransactionRecord) -> Boolean): Action {
        val action = when {
            newRecords.isEmpty() || !newRecords.all(isHidden) -> Action.Publish
            else -> hiddenOnlyPageAction(newRecords)
        }
        if (action == Action.Publish) reset()
        return action
    }

    /** The state a search batch leaves once published; Scanning means read the next page. */
    fun searchStateFor(
        newRecords: List<TransactionRecord>,
        batch: RecordsBatch,
        isHidden: (TransactionRecord) -> Boolean,
    ): SearchScanState {
        if (batch.searchSessionId != sessionId) {
            sessionId = batch.searchSessionId
            windowStart = 0
            lastState = SearchScanState.Scanning
        }
        val scannedCount = batch.searchScannedCount
        lastScannedCount = scannedCount
        val addsVisibleRow = !newRecords.all(isHidden)
        if (addsVisibleRow) windowStart = scannedCount
        val state = when {
            addsVisibleRow || batch.searchExhausted -> SearchScanState.Finished
            // A wallet update must not resume a scan the screen already stopped, but may have read more.
            batch.searchRefresh ->
                if (lastState is SearchScanState.Paused) SearchScanState.Paused(scannedCount) else lastState
            batch.searchTimedOut || scannedCount - windowStart >= AUTO_SCAN_LIMIT ->
                SearchScanState.Paused(scannedCount)
            else -> SearchScanState.Scanning
        }
        lastState = state
        return state
    }

    /** Finished follows its rows, so a final "not found" never shows ahead of them; other states lead. */
    suspend fun publishInOrder(
        state: SearchScanState,
        applyState: (SearchScanState) -> Unit,
        publish: suspend () -> Unit,
    ) {
        if (state != SearchScanState.Finished) applyState(state)
        publish()
        if (state == SearchScanState.Finished) applyState(state)
    }

    // A repeated identical all-hidden page must not request the same next page again.
    private fun hiddenOnlyPageAction(newRecords: List<TransactionRecord>): Action {
        val hiddenOnlyLoadKey = newRecords.joinToString(separator = "|") { it.uid }
        if (lastHiddenOnlyLoadKey == hiddenOnlyLoadKey) return Action.Ignore
        lastHiddenOnlyLoadKey = hiddenOnlyLoadKey
        return Action.LoadNext
    }

    companion object {
        /** Records scanned without a new visible result before the search waits for the user. */
        const val AUTO_SCAN_LIMIT = 5_000
    }
}
