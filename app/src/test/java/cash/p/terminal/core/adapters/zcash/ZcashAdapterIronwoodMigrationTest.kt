package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.zcash.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * A migration moves the whole pool to the account's own internal receiver, so the chain reports
 * it as a spend of everything and a change output back. Its recorded id is what tells the two
 * apart, and it decides the amount the history shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterIronwoodMigrationTest : ZcashAdapterTestFixture() {

    @Test
    fun getTransactions_rememberedMigration_amountIsTotalReceived() = runTest(dispatcher) {
        migrationTxIds = setOf("$ACCOUNT_ID:$MIGRATION_TXID")
        startAdapter()

        val record = publishMigrationTransaction()

        assertTrue(record.isIronwoodMigration)
        assertEquals(MIGRATED.convertZatoshiToZec(), record.mainValue.decimalValue)
    }

    @Test
    fun getTransactions_migrationNotRemembered_amountIsTheNetSpend() = runTest(dispatcher) {
        startAdapter()

        val record = publishMigrationTransaction()

        assertFalse(record.isIronwoodMigration)
        assertEquals(FEE.convertZatoshiToZec().negate(), record.mainValue.decimalValue)
    }

    @Test
    fun getTransactions_incomingTransaction_amountIsPositive() = runTest(dispatcher) {
        startAdapter()

        val record = publishTransaction(value = MIGRATED, totalReceived = MIGRATED)

        assertEquals(MIGRATED.convertZatoshiToZec(), record.mainValue.decimalValue)
    }

    @Test
    fun getTransactions_migrationRememberedByAPreviousProcess_amountIsTotalReceived() =
        runTest(dispatcher) {
            ZcashIronwoodMigrationRegistry(localStorage)
                .remember(ACCOUNT_ID, listOf(MIGRATION_TXID))
            startAdapter()

            val record = publishMigrationTransaction()

            assertTrue(record.isIronwoodMigration)
            assertEquals(MIGRATED.convertZatoshiToZec(), record.mainValue.decimalValue)
        }

    @Test
    fun migrationStepAndRefresh_stepThrows_stillRefreshesReservedBalance() = runTest(dispatcher) {
        var refreshed = false

        assertFailsWith<IOException> {
            migrationStepAndRefresh(
                step = { throw IOException("network failed after reservation") },
                refresh = { refreshed = true },
            )
        }

        assertTrue(refreshed)
    }

    private suspend fun TestScope.publishMigrationTransaction() =
        publishTransaction(value = -FEE, totalReceived = MIGRATED)

    private suspend fun TestScope.publishTransaction(
        value: Long,
        totalReceived: Long,
    ): BitcoinTransactionRecord {
        emitSessionTransactions(
            listOf(
                Transaction(
                    id = 1,
                    txid = MIGRATION_TXID,
                    height = 2_100_000,
                    time = 1_700_000_000L,
                    value = value,
                    memo = null,
                    fee = FEE,
                    totalReceived = totalReceived,
                    isChange = false,
                    recipient = null,
                )
            )
        )
        advanceUntilIdle()

        return adapter.getTransactions(
            from = null,
            token = null,
            limit = 10,
            transactionType = FilterTransactionType.All,
            address = null,
        ).single() as BitcoinTransactionRecord
    }

    private fun TestScope.startAdapter() {
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()
    }

    private companion object {
        const val MIGRATION_TXID =
            "b7c4a1f0d3e2b5a698877665544332211ffeeddccbbaa99887766554433221100"
        const val MIGRATED = 1_000_000_000L
        const val FEE = 15_000L
    }
}
