package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.R
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionResult
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Balance
import cash.p.zcash.MigrationEvent
import cash.p.zcash.MigrationPhase
import cash.p.zcash.MigrationStatus
import cash.p.zcash.MigrationStep
import cash.p.zcash.Pool
import cash.p.zcash.PoolBalance
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import cash.p.zcash.deriveSpendingKey
import io.mockk.coEvery
import io.mockk.mockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // region Whether a migration is offered at all

    @Test
    fun ironwoodMigrationRequiredBalance_migrationPhaseComplete_returnsNull() = runTest(dispatcher) {
        stubMigrationStatus(MigrationPhase.COMPLETE, standardNotes = 0)

        startMigratableAdapter()

        assertNull(adapter.ironwoodMigrationRequiredBalance)
    }

    @Test
    fun ironwoodMigrationRequiredBalance_migrationPhaseMigrating_returnsOrchardBalance() =
        runTest(dispatcher) {
            stubMigrationStatus(MigrationPhase.MIGRATING, standardNotes = 1)

            startMigratableAdapter()

            assertEquals(
                ORCHARD_AVAILABLE.convertZatoshiToZec(),
                adapter.ironwoodMigrationRequiredBalance,
            )
        }

    /** The banner reads the adapter when the balance is published, so the verdict must be there. */
    @Test
    fun ironwoodMigrationRequiredBalance_feasibleSessionState_resolvedBeforeTheBalanceIsPublished() =
        runTest(dispatcher) {
            // Holding the native status call open is what makes the ordering observable: the
            // balance must still be unpublished while the verdict is being resolved.
            val statusInFlight = CompletableDeferred<Unit>()
            coEvery { zcashWallet.migrationStatus(any()) } coAnswers {
                statusInFlight.await()
                migrationStatus(MigrationPhase.MIGRATING, standardNotes = 1)
            }
            adapter = createAdapter(AddressSpecType.Unified)
            adapter.start()
            advanceUntilIdle()
            var balancePublished = false
            val observer = appScope.launch {
                adapter.balanceUpdatedFlow.collect { balancePublished = true }
            }
            advanceUntilIdle()

            emitMigratableState()
            advanceUntilIdle()
            assertFalse(balancePublished)

            statusInFlight.complete(Unit)
            advanceUntilIdle()
            observer.cancel()

            assertTrue(balancePublished)
            assertEquals(
                ORCHARD_AVAILABLE.convertZatoshiToZec(),
                adapter.ironwoodMigrationRequiredBalance,
            )
        }

    @Test
    fun ironwoodMigrationRequiredBalance_migrationStatusFails_returnsNull() = runTest(dispatcher) {
        coEvery { zcashWallet.migrationStatus(any()) } throws ZcashException("native failure")

        startMigratableAdapter()

        assertNull(adapter.ironwoodMigrationRequiredBalance)
    }

    @Test
    fun ironwoodMigrationRequiredBalance_sessionUnavailable_returnsNull() = runTest(dispatcher) {
        stubMigrationStatus(MigrationPhase.MIGRATING, standardNotes = 1)
        startMigratableAdapter()

        coEvery {
            session.withOperation(any<suspend (ZcashWallet) -> Any?>())
        } returns ZcashSessionResult.Unavailable
        emitSessionBalance(orchardBalance(ORCHARD_AVAILABLE * 2))
        advanceUntilIdle()

        assertNull(adapter.ironwoodMigrationRequiredBalance)
    }

    // endregion

    // region A migration that would plan nothing

    @Test
    fun remainingSteps_oneOrchardAndOneIronwoodStandardNote_countsTheRemainingStep() =
        runTest(dispatcher) {
            stubMigrationStatus(MigrationPhase.MIGRATING, standardNotes = 1, migratedNotes = 1)
            startMigratableAdapter()

            val proposal = adapter.proposeIronwoodMigration()

            assertEquals(MINERS_FEE.convertZatoshiToZec(), proposal.fee)
        }

    @Test
    fun proposeIronwoodMigration_phaseComplete_throwsLocalizedException() = runTest(dispatcher) {
        stubMigrationStatus(MigrationPhase.COMPLETE, standardNotes = 0)
        startMigratableAdapter()

        val failure = assertFailsWith<LocalizedException> { adapter.proposeIronwoodMigration() }

        assertEquals(R.string.zcash_migration_error_nothing_to_migrate, failure.errorTextRes)
    }

    @Test
    fun executeIronwoodMigration_firstStepReportsNothingToDo_throwsNotBroadcastLocalizedException() =
        runTest(dispatcher) {
            stubMigrationStatus(MigrationPhase.MIGRATING, standardNotes = 1)
            coEvery { zcashWallet.migrationStep(any(), any()) } returns MigrationStep(
                event = MigrationEvent.NOTHING_TO_DO,
                fee = 0L,
                txid = null,
                status = migrationStatus(MigrationPhase.MIGRATING, standardNotes = 1),
            )
            stubSpendingKeyDerivation()
            startMigratableAdapter()

            val failure = assertFailsWith<NotBroadcastException> {
                adapter.executeIronwoodMigration()
            }

            assertNothingToMigrate(failure)
        }

    @Test
    fun executeIronwoodMigration_phaseAlreadyComplete_throwsNotBroadcastLocalizedException() =
        runTest(dispatcher) {
            stubMigrationStatus(MigrationPhase.COMPLETE, standardNotes = 0)
            stubSpendingKeyDerivation()
            startMigratableAdapter()

            val failure = assertFailsWith<NotBroadcastException> {
                adapter.executeIronwoodMigration()
            }

            assertNothingToMigrate(failure)
        }

    // endregion

    private fun assertNothingToMigrate(failure: NotBroadcastException) {
        assertEquals(
            R.string.zcash_migration_error_nothing_to_migrate,
            (failure.cause as LocalizedException).errorTextRes,
        )
    }

    private fun stubSpendingKeyDerivation() {
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns ByteArray(32)
    }

    private fun stubMigrationStatus(
        phase: MigrationPhase,
        standardNotes: Int,
        migratedNotes: Int = 0,
    ) {
        coEvery { zcashWallet.migrationStatus(any()) } returns
            migrationStatus(phase, standardNotes, migratedNotes)
    }

    private fun migrationStatus(
        phase: MigrationPhase,
        standardNotes: Int,
        migratedNotes: Int = 0,
    ) = MigrationStatus(
        phase = phase,
        standardNotes = standardNotes,
        nonStandardNotes = 0,
        migratedNotes = migratedNotes,
    )

    private fun TestScope.startMigratableAdapter() {
        adapter = createAdapter(AddressSpecType.Unified)
        adapter.start()
        advanceUntilIdle()
        emitMigratableState()
        advanceUntilIdle()
    }

    private fun emitMigratableState() = emitSessionState(
        syncState = SyncState.Synced,
        balance = orchardBalance(ORCHARD_AVAILABLE),
        transactions = emptyList(),
        latestHeight = IRONWOOD_ACTIVATION_HEIGHT,
    )

    private fun orchardBalance(available: Long) =
        PoolBalance(mapOf(Pool.ORCHARD to Balance(available = available)))

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
        const val ORCHARD_AVAILABLE = 500_000L
        const val MINERS_FEE = 10_000L
        const val IRONWOOD_ACTIVATION_HEIGHT = 3_428_143
    }
}
