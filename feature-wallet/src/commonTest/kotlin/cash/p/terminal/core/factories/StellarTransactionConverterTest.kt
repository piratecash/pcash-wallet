package cash.p.terminal.core.factories

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.SpamTransferDetector
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.modules.transactions.getSenderAddresses
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.room.Operation
import io.horizontalsystems.stellarkit.room.StellarAsset
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class StellarTransactionConverterTest {

    private val stellar = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val xlm = Token(Coin("stellar", "Stellar", "XLM"), stellar, TokenType.Native, 7)
    private val usdc = Token(Coin("usd-coin", "USD Coin", "USDC"), stellar, TokenType.Asset("USDC", ISSUER), 7)

    private val coinManager = mockk<ICoinManager> {
        every { getToken(any()) } returns null
        every { getToken(TokenQuery(BlockchainType.Stellar, TokenType.Native)) } returns xlm
        every { getToken(TokenQuery(BlockchainType.Stellar, TokenType.Asset("USDC", ISSUER))) } returns usdc
    }

    private val converter = StellarTransactionConverter(
        source = TransactionSource(stellar, mockk(relaxed = true), null),
        selfAddress = SELF,
        coinManager = coinManager,
        baseToken = xlm,
    )

    @Test
    fun convert_pathPaymentToSelfFromOther_isReceiveOfDeliveredAsset() {
        val type = convert(pathPayment(from = OTHER, to = SELF)).type

        assertEquals(
            StellarTransactionRecord.Type.Receive(coin(usdc, "0.86"), from = OTHER, to = SELF, comment = MEMO, accountCreated = false),
            type
        )
    }

    @Test
    fun convert_pathPaymentFromSelf_isSendOfSpentAsset() {
        val type = convert(pathPayment(from = SELF, to = OTHER)).type

        assertEquals(
            StellarTransactionRecord.Type.Send(coin(xlm, "-5"), to = OTHER, sentToSelf = false, comment = MEMO, accountCreated = false),
            type
        )
    }

    @Test
    fun convert_pathPaymentToItself_isSwap() {
        val record = convert(pathPayment(from = SELF, to = SELF))

        assertEquals(StellarTransactionRecord.Type.Swap(valueIn = coin(xlm, "-5"), valueOut = coin(usdc, "0.86")), record.type)
        assertEquals(coin(usdc, "0.86"), record.mainValue)
    }

    @Test
    fun convert_contractSwap_isSwap() {
        val type = convert(contractCall(change(SELF, POOL, StellarAsset.Native, "5"), change(POOL, SELF, USDC, "0.86"))).type

        assertEquals(StellarTransactionRecord.Type.Swap(valueIn = coin(xlm, "-5"), valueOut = coin(usdc, "0.86")), type)
    }

    @Test
    fun convert_contractTransferToTwoRecipients_isSendWithoutAddress() {
        val type = convert(contractCall(change(SELF, POOL, StellarAsset.Native, "3"), change(SELF, OTHER, StellarAsset.Native, "7"))).type

        assertEquals(
            StellarTransactionRecord.Type.Send(coin(xlm, "-10"), to = null, sentToSelf = false, comment = MEMO, accountCreated = false),
            type
        )
    }

    @Test
    fun convert_contractTransferOfUnknownAssetFromTwoSenders_isSpamCheckedPerSender() {
        val aqua = StellarAsset.Asset("AQUA", "GAQUA")

        val record = convert(contractCall(change(POOL, SELF, aqua, "1"), change(OTHER, SELF, aqua, "2")))

        assertNull((record.type as StellarTransactionRecord.Type.Receive).from)
        assertTrue(record.spam)
        assertEquals(listOf(POOL, OTHER), record.getSenderAddresses())
    }

    @Test
    fun convert_contractTransferOfKnownAssetFromTwoSenders_isNotSpam() {
        val record = convert(contractCall(change(POOL, SELF, USDC, "1"), change(OTHER, SELF, USDC, "2")))

        assertEquals(coin(usdc, "3"), record.mainValue)
        assertFalse(record.spam)
    }

    @Test
    fun convert_contractTransferWithDustFromOneSender_flagsOnlyThatSender() {
        val record = convert(contractCall(change(POOL, SELF, USDC, "0.001"), change(OTHER, SELF, USDC, "1")))

        assertEquals(listOf(POOL), SpamTransferDetector.spamSenders(StellarTransactionRecord.eventsForPhishingCheck(record.type)))
    }

    @Test
    fun convert_contractMint_isReceiveFromIssuer() {
        val mint = Operation.ContractBalanceChange("mint", null, SELF, USDC, BigDecimal("0.3"))

        val type = convert(contractCall(mint)).type

        assertEquals(
            StellarTransactionRecord.Type.Receive(coin(usdc, "0.3"), from = ISSUER, to = SELF, comment = MEMO, accountCreated = false),
            type
        )
    }

    @Test
    fun convert_contractTwoAssetsSold_isUnsupported() {
        val type = convert(contractCall(change(SELF, POOL, StellarAsset.Native, "5"), change(SELF, POOL, USDC, "1"))).type

        assertTrue(type is StellarTransactionRecord.Type.Unsupported)
    }

    @Test
    fun convert_paymentOfUnknownAsset_showsAssetCodeAmountAndStellarBadge() {
        val payment = Operation.Payment(BigDecimal("12.5"), StellarAsset.Asset("AQUA", "GAQUA"), OTHER, SELF)

        val type = convert(operation(type = "payment", payment = payment)).type as StellarTransactionRecord.Type.Receive

        val value = type.value as TransactionValue.TokenValue
        assertEquals("AQUA", value.tokenCode)
        assertEquals(BigDecimal("12.5"), value.value)
        assertEquals("STELLAR", value.badge)
    }

    @Test
    fun convert_operationStoredWithoutPayload_staysUnsupported() {
        val type = convert(operation(type = "path_payment_strict_send")).type

        assertEquals("path_payment_strict_send", (type as StellarTransactionRecord.Type.Unsupported).type)
    }

    private fun convert(operation: Operation) = converter.convert(operation)

    private fun coin(token: Token, amount: String) = TransactionValue.CoinValue(token, BigDecimal(amount))

    private fun pathPayment(from: String, to: String) = operation(
        type = "path_payment_strict_send",
        pathPayment = Operation.PathPayment(from, to, USDC, BigDecimal("0.86"), StellarAsset.Native, BigDecimal("5")),
    )

    private fun contractCall(vararg changes: Operation.ContractBalanceChange) =
        operation(type = "invoke_host_function", changes = changes.toList())

    private fun change(from: String?, to: String?, asset: StellarAsset, amount: String) =
        Operation.ContractBalanceChange("transfer", from, to, asset, BigDecimal(amount))

    private fun operation(
        type: String,
        payment: Operation.Payment? = null,
        pathPayment: Operation.PathPayment? = null,
        changes: List<Operation.ContractBalanceChange>? = null,
    ) = Operation(
        id = 1L,
        timestamp = 1_700_000_000L,
        pagingToken = "1",
        sourceAccount = OTHER,
        transactionHash = "hash",
        transactionSuccessful = true,
        fee = null,
        memo = MEMO,
        type = type,
        payment = payment,
        accountCreated = null,
        changeTrust = null,
        pathPayment = pathPayment,
        contractBalanceChanges = changes,
    )

    private companion object {
        const val SELF = "GSELF"
        const val OTHER = "GOTHER"
        const val POOL = "CPOOL"
        const val ISSUER = "GISSUER"
        const val MEMO = "memo"
        val USDC = StellarAsset.Asset("USDC", ISSUER)
    }
}
