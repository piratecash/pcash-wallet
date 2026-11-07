package cash.p.terminal.core.managers

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class SpamTransferDetectorNftTest {

    @Test
    fun isSpam_incomingSolanaNftWithPositiveValue_notSpam() {
        val nft = TransactionValue.NftValue(
            mintAddress = "NFT_MINT",
            value = BigDecimal.ONE,
            tokenName = null,
            tokenSymbol = null
        )

        assertFalse(SpamTransferDetector.isSpam(listOf(incomingEvent(nft))))
    }

    @Test
    fun isSpam_incomingRawValueWithoutTokenQuery_spam() {
        val raw = TransactionValue.RawValue(value = BigInteger.ONE)

        assertTrue(SpamTransferDetector.isSpam(listOf(incomingEvent(raw))))
    }

    private fun incomingEvent(value: TransactionValue) =
        TransferEvent(address = "SENDER_ADDRESS", addressForIncomingAddress = "USER_ADDRESS", value = value)
}
