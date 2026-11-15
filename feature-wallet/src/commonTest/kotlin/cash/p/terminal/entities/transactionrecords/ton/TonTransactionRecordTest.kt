package cash.p.terminal.entities.transactionrecords.ton

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.transactions.TransactionStatus
import io.horizontalsystems.tonkit.models.Event
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TonTransactionRecordTest {

    private val nft = TransactionValue.NftValue("nft-address", BigDecimal.ONE, "Item", "NFT")
    private val coin = mockk<TransactionValue>(relaxed = true)

    @Test
    fun isNftOnly_singleNftAction_true() {
        assertTrue(record(nft).isNftOnly)
    }

    @Test
    fun isNftOnly_twoNftActions_true() {
        assertTrue(record(nft, nft).isNftOnly)
    }

    @Test
    fun isNftOnly_noActions_false() {
        assertFalse(record().isNftOnly)
    }

    @Test
    fun isNftOnly_coinAndNftActions_false() {
        assertFalse(record(coin, nft).isNftOnly)
    }

    @Test
    fun isNftOnly_singleCoinAction_false() {
        assertFalse(record(coin).isNftOnly)
    }

    private fun record(vararg values: TransactionValue) = TonTransactionRecord(
        source = mockk(relaxed = true),
        event = Event(
            id = "event",
            lt = 1L,
            timestamp = 1L,
            scam = false,
            inProgress = false,
            extra = 0L,
            actions = emptyList(),
        ),
        token = mockk(relaxed = true),
        actions = values.map { value ->
            TonTransactionRecord.Action(
                type = TonTransactionRecord.Action.Type.Receive(value, "sender", null, null),
                status = TransactionStatus.Completed,
            )
        },
    )
}
