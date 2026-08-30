package cash.p.terminal.modules.balance.token

import cash.p.terminal.modules.transactions.TransactionViewItem
import io.mockk.mockk
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
                syncing = false,
                transactions = cachedTransactions,
            )
        )
    }
}
