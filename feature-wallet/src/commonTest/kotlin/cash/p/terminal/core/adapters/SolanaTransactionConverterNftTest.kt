package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture.USER_ADDRESS
import cash.p.terminal.entities.TransactionValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SolanaTransactionConverterNftTest {

    private val coinManager: ICoinManager = mockk(relaxed = true) {
        every { getToken(any()) } returns null
    }

    @Test
    fun transactionRecord_unknownIncomingNftMint_mainValueIsNftValueWithMintAddress() {
        val fullTransaction = SolanaTransactionConverterTestFixture.tokenTransferTransaction(
            hash = "hash",
            from = "SENDER_ADDRESS",
            to = USER_ADDRESS,
            mint = "NFT_MINT",
            incoming = true,
            amount = BigDecimal.ONE,
            decimals = 0,
            isNft = true,
            name = "Mad Lad",
        )

        val record = SolanaTransactionConverterTestFixture.createConverter(coinManager)
            .transactionRecord(fullTransaction)

        val value = record.mainValue as TransactionValue.NftValue
        assertEquals("NFT_MINT", value.mintAddress)
        assertEquals("Mad Lad", value.tokenName)
        assertEquals(0, BigDecimal.ONE.compareTo(value.value))
    }
}
