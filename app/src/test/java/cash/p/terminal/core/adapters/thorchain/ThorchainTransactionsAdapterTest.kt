package cash.p.terminal.core.adapters.thorchain

import cash.p.terminal.core.factories.ThorchainTransactionConverter
import cash.p.terminal.core.managers.ThorchainKitWrapper
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.Network
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ThorchainTransactionsAdapterTest {

    private val thorchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val rune = Token(Coin("thorchain", "THORChain", "RUNE"), thorchain, TokenType.Native, 8)
    private val tcy = Token(Coin("tcy", "TCY", "TCY"), thorchain, TokenType.ThorchainAsset("tcy"), 8)
    private val source = TransactionSource(thorchain, mockk<Account>(relaxed = true), null)

    private val swap = Transaction("HASH", 1, TIMESTAMP, "swap", "success", null, emptyList(), emptyList())
    private val spendRune = record("swap-rune", rune, outgoing = true)
    private val receiveTcy = record("swap-tcy", tcy, outgoing = false)

    private val kit = mockk<ThorchainKit>(relaxed = true) {
        every { network } returns Network.Mainnet
    }
    private val converter = mockk<ThorchainTransactionConverter> {
        every { convert(swap) } returns listOf(spendRune, receiveTcy)
    }
    private val adapter = ThorchainTransactionsAdapter(ThorchainKitWrapper(kit), converter)

    @Test
    fun getTransactions_pageEndsInsideSwap_resumesWithItsOtherRecord() = runTest {
        every { kit.getTransactions(TIMESTAMP + 1, any()) } returns listOf(swap)

        val page = adapter.getTransactions(spendRune, null, 10, FilterTransactionType.All, null)

        assertEquals(listOf(receiveTcy), page)
    }

    @Test
    fun getTransactions_tokenAndTypeFilters_keepOnlyMatchingRecords() = runTest {
        every { kit.getTransactions(null, any()) } returns listOf(swap)

        assertEquals(listOf(receiveTcy), adapter.getTransactions(null, tcy, 10, FilterTransactionType.All, null))
        assertEquals(listOf(spendRune), adapter.getTransactions(null, null, 10, FilterTransactionType.Outgoing, null))
    }

    private fun record(uid: String, token: Token, outgoing: Boolean): ThorchainTransactionRecord {
        val value = TransactionValue.CoinValue(token, BigDecimal.ONE)
        return ThorchainTransactionRecord(
            uid = uid,
            transaction = swap,
            spam = false,
            source = source,
            token = token,
            type = if (outgoing) {
                ThorchainTransactionRecord.Type.Outgoing(value, to = null, sentToSelf = false)
            } else {
                ThorchainTransactionRecord.Type.Incoming(value, from = null)
            },
            fee = null,
        )
    }

    private companion object {
        const val TIMESTAMP = 1_700_000_000L
    }
}
