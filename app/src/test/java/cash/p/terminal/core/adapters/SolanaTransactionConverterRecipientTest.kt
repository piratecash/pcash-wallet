package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture.USER_ADDRESS
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * The kit fills Transaction.to for token transfers with an arbitrary account key (often the
 * sender), so incoming records must not trust it as the recipient address.
 */
class SolanaTransactionConverterRecipientTest {

    private val userAddress = USER_ADDRESS
    private val senderAddress = "SENDER_ADDRESS"
    private val foreignTo = "SENDER_OWNER"

    private val usdcToken = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = SolanaTransactionConverterTestFixture.solanaBlockchain,
        type = TokenType.Spl("USDC_MINT"),
        decimals = 6
    )

    private val coinManager: ICoinManager = mockk(relaxed = true)

    private fun createConverter(): SolanaTransactionConverter =
        SolanaTransactionConverterTestFixture.createConverter(coinManager)

    @Test
    fun transactionRecord_incomingSplTransferWithForeignTo_recipientIsWalletOwner() {
        every { coinManager.getToken(any()) } returns usdcToken
        val fullTransaction = SolanaTransactionConverterTestFixture.tokenTransferTransaction(
            hash = "incoming_spl_hash",
            from = senderAddress,
            to = foreignTo,
            mint = "USDC_MINT",
            incoming = true,
            amount = BigDecimal("49075306")
        )

        val record = createConverter().transactionRecord(fullTransaction)

        assertEquals(TransactionRecordType.SOLANA_INCOMING, record.transactionRecordType)
        assertEquals(listOf(userAddress), record.to)
        assertEquals(senderAddress, record.from)
    }

    @Test
    fun transactionRecord_outgoingSplTransfer_recipientUnchanged() {
        every { coinManager.getToken(any()) } returns usdcToken
        val recipientTokenAccount = "RECIPIENT_TOKEN_ACCOUNT"
        val fullTransaction = SolanaTransactionConverterTestFixture.tokenTransferTransaction(
            hash = "outgoing_spl_hash",
            from = userAddress,
            to = recipientTokenAccount,
            mint = "USDC_MINT",
            incoming = false,
            amount = BigDecimal("1000000")
        )

        val record = createConverter().transactionRecord(fullTransaction)

        assertEquals(TransactionRecordType.SOLANA_OUTGOING, record.transactionRecordType)
        assertEquals(listOf(recipientTokenAccount), record.to)
    }
}
