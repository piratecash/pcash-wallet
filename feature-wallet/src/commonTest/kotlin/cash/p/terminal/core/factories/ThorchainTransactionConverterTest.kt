package cash.p.terminal.core.factories

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import com.google.gson.Gson
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.MidgardAction
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.sync.TransactionSyncer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class ThorchainTransactionConverterTest {

    private val thorchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val maya = Blockchain(BlockchainType.Mayachain, "Maya", null)
    private val rune = Token(Coin("thorchain", "THORChain", "RUNE"), thorchain, TokenType.Native, 8)
    private val tcy = Token(Coin("tcy", "TCY", "TCY"), thorchain, TokenType.ThorchainAsset("tcy"), 8)
    private val cacao = Token(Coin("cacao", "CACAO", "CACAO"), maya, TokenType.Native, 10)

    private val coinManager = mockk<ICoinManager> {
        every { getToken(any()) } returns null
        every { getToken(TokenQuery(BlockchainType.Thorchain, TokenType.ThorchainAsset("tcy"))) } returns tcy
    }

    @Test
    fun convert_sendFromUser_returnsOutgoingRecordToRecipient() {
        val record = thorConverter(SENDER).convert(sendAction()).single()

        val type = record.type as ThorchainTransactionRecord.Type.Outgoing
        assertEquals(TransactionRecordType.THORCHAIN_OUTGOING, record.transactionRecordType)
        assertEquals(TransactionValue.CoinValue(rune, BigDecimal("-9500.0912")), type.value.normalized())
        assertEquals(RECIPIENT, type.to)
        assertFalse(type.sentToSelf)
        assertEquals("hello", record.memo)
        assertEquals(27069723, record.blockHeight)
        assertEquals(TransactionStatus.Completed, record.status(null))
    }

    @Test
    fun convert_sendToUser_returnsIncomingRecordFromSender() {
        val record = thorConverter(RECIPIENT).convert(sendAction()).single()

        val type = record.type as ThorchainTransactionRecord.Type.Incoming
        assertEquals(TransactionRecordType.THORCHAIN_INCOMING, record.transactionRecordType)
        assertEquals(TransactionValue.CoinValue(rune, BigDecimal("9500.0912")), type.value.normalized())
        assertEquals(SENDER, type.from)
    }

    @Test
    fun convert_swapRuneToTcy_returnsSpendAndReceiveRecords() {
        val records = thorConverter(SENDER).convert(
            action(
                type = "swap",
                incoming = transfer(SENDER, "THOR.RUNE", "100000000"),
                outgoing = transfer(SENDER, "THOR.TCY", "2500000000", txId = ""),
            )
        )

        assertEquals(2, records.size)
        val spend = records[0].type as ThorchainTransactionRecord.Type.Outgoing
        val receive = records[1].type as ThorchainTransactionRecord.Type.Incoming
        assertEquals(TransactionValue.CoinValue(rune, BigDecimal("-1")), spend.value.normalized())
        assertNull(spend.to)
        assertFalse(spend.sentToSelf)
        assertEquals(TransactionValue.CoinValue(tcy, BigDecimal("25")), receive.value.normalized())
        assertEquals(tcy, records[1].token)
        assertTrue(records[0].uid != records[1].uid)
    }

    @Test
    fun convert_selfSend_returnsSingleOutgoingSentToSelf() {
        val records = thorConverter(SENDER).convert(
            action(
                incoming = transfer(SENDER, "THOR.RUNE", "100000000"),
                outgoing = transfer(SENDER, "THOR.RUNE", "100000000"),
            )
        )

        val type = records.single().type as ThorchainTransactionRecord.Type.Outgoing
        assertTrue(type.sentToSelf)
        assertTrue(records.single().sentToSelf)
    }

    @Test
    fun convert_refundOfSameAsset_isNotDuplicatedAsIncoming() {
        val records = thorConverter(SENDER).convert(
            action(
                type = "refund",
                incoming = transfer(SENDER, "THOR.RUNE", "100000000"),
                outgoing = transfer(SENDER, "THOR.RUNE", "98000000", txId = ""),
            )
        )

        assertEquals(TransactionRecordType.THORCHAIN_OUTGOING, records.single().transactionRecordType)
    }

    @Test
    fun convert_failedAction_marksRecordFailed() {
        val record = thorConverter(SENDER).convert(
            action(type = "failed", incoming = transfer(SENDER, "THOR.RUNE", "100000000"), outgoing = "")
        ).single()

        assertTrue(record.failed)
        assertEquals(TransactionStatus.Failed, record.status(null))
    }

    @Test
    fun convert_pendingAction_hasNoBlockAndIsPending() {
        val record = thorConverter(SENDER).convert(
            action(status = "pending", incoming = transfer(SENDER, "THOR.RUNE", "100000000"), outgoing = "")
        ).single()

        assertNull(record.blockHeight)
        assertEquals(TransactionStatus.Pending, record.status(null))
    }

    @Test
    fun convert_unknownDenom_returnsTokenValueWithTicker() {
        val record = thorConverter(RECIPIENT).convert(
            action(
                incoming = transfer(SENDER, "BTC/BTC", "150000000"),
                outgoing = transfer(RECIPIENT, "BTC/BTC", "150000000"),
            )
        ).single()

        val value = record.mainValue as TransactionValue.TokenValue
        assertEquals("BTC", value.tokenCode)
        assertEquals(8, value.tokenDecimals)
        assertEquals(0, BigDecimal("1.5").compareTo(value.value))
        assertEquals(rune, record.token)
    }

    @Test
    fun convert_bankDenomNotation_resolvesKnownToken() {
        val record = thorConverter(RECIPIENT).convert(
            action(incoming = transfer(SENDER, "TCY", "100000000"), outgoing = transfer(RECIPIENT, "TCY", "100000000"))
        ).single()

        assertEquals(TransactionValue.CoinValue(tcy, BigDecimal("1")), record.mainValue.normalized())
    }

    @Test
    fun convert_cacao_scalesByTenDecimals() {
        val converter =
            ThorchainTransactionConverter(coinManager, source(maya), MAYA_RECIPIENT, cacao, Network.MayaMainnet)

        val record = converter.convert(
            action(
                incoming = transfer(MAYA_SENDER, "MAYA.CACAO", "15000000000"),
                outgoing = transfer(MAYA_RECIPIENT, "MAYA.CACAO", "15000000000"),
            )
        ).single()

        assertEquals(TransactionValue.CoinValue(cacao, BigDecimal("1.5")), record.mainValue.normalized())
    }

    @Test
    fun convert_ownSendWithFee_outgoingRecordCarriesFeeInRune() {
        val record = thorConverter(SENDER).convert(sendAction().copy(fee = BigInteger("2000000"))).single()

        assertEquals(TransactionValue.CoinValue(rune, BigDecimal("0.02")), record.fee.normalized())
    }

    @Test
    fun convert_ownMayaSendWithFee_outgoingRecordCarriesFeeInCacao() {
        val converter =
            ThorchainTransactionConverter(coinManager, source(maya), MAYA_SENDER, cacao, Network.MayaMainnet)

        val record = converter.convert(
            action(
                incoming = transfer(MAYA_SENDER, "MAYA.CACAO", "15000000000"),
                outgoing = transfer(MAYA_RECIPIENT, "MAYA.CACAO", "15000000000"),
            ).copy(fee = BigInteger("2000000000"))
        ).single()

        assertEquals(TransactionValue.CoinValue(cacao, BigDecimal("0.2")), record.fee.normalized())
    }

    @Test
    fun convert_incomingSendWithFee_recordHasNoFee() {
        val record = thorConverter(RECIPIENT).convert(sendAction().copy(fee = BigInteger("2000000"))).single()

        assertNull(record.fee)
    }

    @Test
    fun convert_ownSendWithUnknownFee_recordHasNoFee() {
        val record = thorConverter(SENDER).convert(sendAction()).single()

        assertNull(record.fee)
    }

    @Test
    fun convert_swapWithFee_feeOnlyOnSpendRecord() {
        val records = thorConverter(SENDER).convert(
            action(
                type = "swap",
                incoming = transfer(SENDER, "THOR.RUNE", "100000000"),
                outgoing = transfer(SENDER, "THOR.TCY", "2500000000", txId = ""),
            ).copy(fee = BigInteger("2000000"))
        )

        assertEquals(TransactionValue.CoinValue(rune, BigDecimal("0.02")), records[0].fee.normalized())
        assertNull(records[1].fee)
    }

    @Test
    fun convert_runeInFullAndBankDenomNotation_producesSameDenomUid() {
        assertEquals(listOf("$HASH-rune", "$HASH-rune"), listOf("THOR.RUNE", "RUNE").map(::incomingUid))
    }

    @Test
    fun convert_tcyInFullAndBankDenomNotation_producesSameDenomUid() {
        assertEquals(listOf("$HASH-tcy", "$HASH-tcy"), listOf("THOR.TCY", "TCY").map(::incomingUid))
    }

    private fun incomingUid(asset: String) = thorConverter(RECIPIENT).convert(
        action(incoming = transfer(SENDER, asset, "100000000"), outgoing = transfer(RECIPIENT, asset, "100000000"))
    ).single().uid

    private fun thorConverter(userAddress: String) =
        ThorchainTransactionConverter(coinManager, source(thorchain), userAddress, rune, Network.Mainnet)

    private fun source(blockchain: Blockchain) = TransactionSource(blockchain, mockk<Account>(relaxed = true), null)

    private fun TransactionValue?.normalized(): TransactionValue? =
        (this as? TransactionValue.CoinValue)?.let { it.copy(value = it.value.stripTrailingZeros()) }

    // Real mainnet Midgard send action (kit TransactionSyncerTest fixture).
    private fun sendAction() = action(
        incoming = transfer(SENDER, "THOR.RUNE", "950009120000"),
        outgoing = transfer(RECIPIENT, "THOR.RUNE", "950009120000"),
        memo = "hello",
    )

    private fun action(
        incoming: String,
        outgoing: String,
        type: String = "send",
        status: String = "success",
        memo: String = "",
    ): Transaction {
        val json = """
            {
              "date": "1784454197789344321",
              "height": "27069723",
              "in": [$incoming],
              "metadata": {"$type": {"code": "0", "memo": "$memo", "reason": ""}},
              "out": [$outgoing],
              "pools": [],
              "status": "$status",
              "type": "$type"
            }
        """
        return requireNotNull(TransactionSyncer.fromMidgardAction(Gson().fromJson(json, MidgardAction::class.java)))
    }

    private fun transfer(address: String, asset: String, amount: String, txId: String = HASH) = """
        {"address": "$address", "coins": [{"amount": "$amount", "asset": "$asset"}], "txID": "$txId"}
    """

    private companion object {
        const val HASH = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        const val SENDER = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"
        const val RECIPIENT = "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"
        const val MAYA_SENDER = "maya1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwvvjl8"
        const val MAYA_RECIPIENT = "maya166n4w5039meulfa3p6ydg60ve6ueac7tlu37cq"
    }
}
