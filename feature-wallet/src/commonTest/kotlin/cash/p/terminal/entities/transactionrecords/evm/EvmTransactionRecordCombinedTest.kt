package cash.p.terminal.entities.transactionrecords.evm

import cash.p.terminal.entities.TransactionValue
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvmTransactionRecordCombinedTest {

    @Test
    fun combined_jettonValuesOfSameJetton_sumsThem() {
        val events = listOf(
            event(jetton("USDT", BigDecimal("-1.5"))),
            event(jetton("USDT", BigDecimal("-2.5"))),
        )

        val (incoming, outgoing) = EvmTransactionRecord.combined(emptyList(), events)

        assertTrue(incoming.isEmpty())
        assertEquals(BigDecimal("-4.0"), outgoing.single().decimalValue)
    }

    @Test
    fun combined_differentJettons_keepsBoth() {
        val events = listOf(
            event(jetton("USDT", BigDecimal("-1"))),
            event(jetton("STON", BigDecimal("3"))),
        )

        val (incoming, outgoing) = EvmTransactionRecord.combined(events.drop(1), events.take(1))

        assertEquals(listOf("STON"), incoming.map { it.coinCode })
        assertEquals(listOf("USDT"), outgoing.map { it.coinCode })
    }

    private fun jetton(symbol: String, value: BigDecimal) =
        TransactionValue.JettonValue(symbol, symbol, 6, value, null)

    private fun event(value: TransactionValue) = TransferEvent(null, null, value)
}
