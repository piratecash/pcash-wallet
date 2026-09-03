package cash.p.terminal.modules.balance.token

import cash.p.terminal.modules.transactions.TransactionViewItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenNotSyncedSectionVisibilityTest {

    private val cachedTransactions = mapOf("TODAY" to listOf(mockk<TransactionViewItem>()))

    @Test
    fun shouldShowNotSyncedSection_syncFailed_returnsTrue() {
        assertTrue(
            shouldShowNotSyncedSection(
                failedIconVisible = true,
                loadFailed = false,
                syncing = false,
                transactions = null,
            )
        )
    }

    @Test
    fun shouldShowNotSyncedSection_syncingWithCachedTransactions_returnsTrue() {
        assertTrue(
            shouldShowNotSyncedSection(
                failedIconVisible = false,
                loadFailed = false,
                syncing = true,
                transactions = cachedTransactions,
            )
        )
    }

    @Test
    fun shouldShowNotSyncedSection_syncingWithoutTransactions_returnsFalse() {
        assertFalse(
            shouldShowNotSyncedSection(
                failedIconVisible = false,
                loadFailed = false,
                syncing = true,
                transactions = null,
            )
        )
    }

    @Test
    fun shouldShowNotSyncedSection_syncingWithEmptyTransactions_returnsFalse() {
        assertFalse(
            shouldShowNotSyncedSection(
                failedIconVisible = false,
                loadFailed = false,
                syncing = true,
                transactions = mapOf("TODAY" to emptyList()),
            )
        )
    }

    @Test
    fun shouldShowNotSyncedSection_syncedWithTransactions_returnsFalse() {
        assertFalse(
            shouldShowNotSyncedSection(
                failedIconVisible = false,
                loadFailed = false,
                syncing = false,
                transactions = cachedTransactions,
            )
        )
    }

    @Test
    fun shouldShowNotSyncedSection_loadFailedWithoutTransactions_returnsTrue() {
        assertTrue(
            shouldShowNotSyncedSection(
                failedIconVisible = false,
                loadFailed = true,
                syncing = false,
                transactions = null,
            )
        )
    }

    @Test
    fun shouldShowNotSyncedSection_loadFailedWithTransactions_returnsTrue() {
        assertTrue(
            shouldShowNotSyncedSection(
                failedIconVisible = false,
                loadFailed = true,
                syncing = false,
                transactions = cachedTransactions,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_loadFailedAndEmpty_returnsNone() {
        assertEquals(
            TransactionsPlaceholder.None,
            transactionsPlaceholder(
                searchScanning = false,
                searchEmptyResult = false,
                loadFailed = true,
                syncing = false,
                transactionsKnown = true,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_loadFailedWhileChainStillSyncing_returnsNone() {
        assertEquals(
            TransactionsPlaceholder.None,
            transactionsPlaceholder(
                searchScanning = false,
                searchEmptyResult = false,
                loadFailed = true,
                syncing = true,
                transactionsKnown = false,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_loadFailedWhileSearchScanning_returnsNone() {
        assertEquals(
            TransactionsPlaceholder.None,
            transactionsPlaceholder(
                searchScanning = true,
                searchEmptyResult = false,
                loadFailed = true,
                syncing = false,
                transactionsKnown = true,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_searchScanningWithoutFailure_returnsSearchInProgress() {
        assertEquals(
            TransactionsPlaceholder.SearchInProgress,
            transactionsPlaceholder(
                searchScanning = true,
                searchEmptyResult = false,
                loadFailed = false,
                syncing = false,
                transactionsKnown = true,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_searchEmptyResultWithoutFailure_returnsSearchEmpty() {
        assertEquals(
            TransactionsPlaceholder.SearchEmpty,
            transactionsPlaceholder(
                searchScanning = false,
                searchEmptyResult = true,
                loadFailed = false,
                syncing = false,
                transactionsKnown = true,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_syncingWithoutFailure_returnsWaitForSync() {
        assertEquals(
            TransactionsPlaceholder.WaitForSync,
            transactionsPlaceholder(
                searchScanning = false,
                searchEmptyResult = false,
                loadFailed = false,
                syncing = true,
                transactionsKnown = true,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_transactionsUnknownWithoutFailure_returnsWaitForSync() {
        assertEquals(
            TransactionsPlaceholder.WaitForSync,
            transactionsPlaceholder(
                searchScanning = false,
                searchEmptyResult = false,
                loadFailed = false,
                syncing = false,
                transactionsKnown = false,
            )
        )
    }

    @Test
    fun transactionsPlaceholder_loadedEmptyWalletWithoutFailure_returnsEmptyList() {
        assertEquals(
            TransactionsPlaceholder.EmptyList,
            transactionsPlaceholder(
                searchScanning = false,
                searchEmptyResult = false,
                loadFailed = false,
                syncing = false,
                transactionsKnown = true,
            )
        )
    }
}
