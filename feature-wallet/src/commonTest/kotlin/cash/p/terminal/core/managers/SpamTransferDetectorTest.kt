package cash.p.terminal.core.managers

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SpamTransferDetectorTest {

    private val bnb = Token(
        coin = Coin(uid = "binancecoin", name = "BNB", code = "BNB"),
        blockchain = Blockchain(BlockchainType.BinanceSmartChain, "Binance Smart Chain", null),
        type = TokenType.Native,
        decimals = 18
    )

    private fun nativeEvent(address: String, amount: BigDecimal) =
        TransferEvent(address, null, TransactionValue.CoinValue(bnb, amount))

    @Test
    fun spamSenders_nativeSendAndEqualRefund_returnsEmpty() {
        val events = listOf(
            nativeEvent("recipient", BigDecimal.ONE.negate()),
            nativeEvent("refunder", BigDecimal.ONE)
        )

        assertTrue(SpamTransferDetector.spamSenders(events).isEmpty())
    }

    @Test
    fun spamSenders_nativePaymentWithZeroCallback_returnsEmpty() {
        val events = listOf(
            nativeEvent("contract", BigDecimal.ONE.negate()),
            nativeEvent("contract", BigDecimal.ZERO)
        )

        assertTrue(SpamTransferDetector.spamSenders(events).isEmpty())
    }

    @Test
    fun spamSenders_zeroIncomingNative_flagsSender() {
        val events = listOf(nativeEvent("poisoner", BigDecimal.ZERO))

        assertEquals(listOf("poisoner"), SpamTransferDetector.spamSenders(events))
    }
}
