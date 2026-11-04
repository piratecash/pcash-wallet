package cash.p.terminal.shared.desktop.vault

import androidx.room.execSQL
import androidx.room.useWriterConnection
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesktopStorageMigrationTest {

    private val fixture = DesktopVaultFixture()
    private val vault = fixture.vault()

    @AfterTest
    fun tearDown() {
        fixture.tearDown()
    }

    @Test
    fun unlock_databaseAtVersion118_migratesAndClearsZeroAmountOutReal() = runTest {
        vault.create(password())
        val database = assertIs<DesktopVaultState.Unlocked>(vault.state.value).session.database
        database.swapProviderTransactionsDao().insert(zeroAmountSwap())
        database.useWriterConnection {
            it.execSQL("ALTER TABLE SwapProviderTransaction DROP COLUMN receivedAt")
            it.execSQL("PRAGMA user_version = 118")
        }
        vault.close()

        assertEquals(UnlockOutcome.Success, vault.unlock(password()))

        val reopened = assertIs<DesktopVaultState.Unlocked>(vault.state.value).session.database
        val row = reopened.swapProviderTransactionsDao().getByDate(SWAP_DATE)
        assertEquals(SWAP_DATE, row?.date)
        assertNull(row?.amountOutReal)
        assertNull(row?.receivedAt)
        vault.close()
    }

    private fun zeroAmountSwap() = SwapProviderTransaction(
        date = SWAP_DATE,
        outgoingRecordUid = null,
        transactionId = "tx-1",
        status = "finished",
        provider = SwapProvider.PCASH_BACKEND,
        coinUidIn = "ethereum",
        blockchainTypeIn = BlockchainType.Ethereum.uid,
        amountIn = BigDecimal.ONE,
        addressIn = "0xIn",
        coinUidOut = "tron",
        blockchainTypeOut = BlockchainType.Tron.uid,
        amountOut = BigDecimal("18159.295232"),
        addressOut = "TAddressOut",
        amountOutReal = BigDecimal.ZERO,
    )

    private companion object {
        const val SWAP_DATE = 1_000L
    }
}
