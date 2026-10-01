package cash.p.terminal.modules.transactions

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TransactionRecordSearchMatcherTest {

    private val matcher = TransactionRecordSearchMatcher()
    private val thorchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val rune = Token(Coin("thorchain", "THORChain", "RUNE"), thorchain, TokenType.Native, 8)

    private fun thorchainRecord(type: ThorchainTransactionRecord.Type) = ThorchainTransactionRecord(
        uid = "uid",
        transaction = mockk<Transaction>(relaxed = true) {
            every { hash } returns "HASH"
            every { memo } returns MEMO
        },
        spam = false,
        source = TransactionSource(thorchain, mockk(relaxed = true), null),
        token = rune,
        type = type,
        fee = null,
    )

    @Test
    fun matches_thorchainOutgoing_findsMemoAndRecipient() {
        val record = thorchainRecord(
            ThorchainTransactionRecord.Type.Outgoing(
                TransactionValue.CoinValue(rune, BigDecimal.ONE.negate()),
                to = RECIPIENT,
                sentToSelf = false,
            )
        )

        assertTrue(matcher.matches(record, "=:eth.eth"))
        assertTrue(matcher.matches(record, RECIPIENT.uppercase()))
        assertFalse(matcher.matches(record, "unrelated"))
    }

    @Test
    fun matches_thorchainIncoming_findsSender() {
        val record = thorchainRecord(
            ThorchainTransactionRecord.Type.Incoming(TransactionValue.CoinValue(rune, BigDecimal.ONE), from = SENDER)
        )

        assertTrue(matcher.matches(record, SENDER))
    }

    private companion object {
        const val MEMO = "=:ETH.ETH:0xabc"
        const val RECIPIENT = "thor1recipientaddress"
        const val SENDER = "thor1senderaddress"
    }
}
