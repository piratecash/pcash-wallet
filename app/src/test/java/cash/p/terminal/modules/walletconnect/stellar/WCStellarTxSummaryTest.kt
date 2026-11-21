package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.AssetInfo
import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.Op
import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.stellar.sdk.Account
import org.stellar.sdk.Asset
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.ChangeTrustAsset
import org.stellar.sdk.KeyPair
import org.stellar.sdk.LiquidityPool
import org.stellar.sdk.Memo
import org.stellar.sdk.MuxedAccount
import org.stellar.sdk.Network
import org.stellar.sdk.Price
import org.stellar.sdk.SignerKey
import org.stellar.sdk.Transaction
import org.stellar.sdk.TransactionBuilder
import org.stellar.sdk.operations.AccountMergeOperation
import org.stellar.sdk.operations.ChangeTrustOperation
import org.stellar.sdk.operations.ExtendFootprintTTLOperation
import org.stellar.sdk.operations.ManageBuyOfferOperation
import org.stellar.sdk.operations.ManageDataOperation
import org.stellar.sdk.operations.Operation
import org.stellar.sdk.operations.PaymentOperation
import org.stellar.sdk.operations.SetOptionsOperation
import java.math.BigDecimal
import java.math.BigInteger

class WCStellarTxSummaryTest {

    private val source = KeyPair.random().accountId
    private val destination = KeyPair.random().accountId
    private val issuer = KeyPair.random().accountId

    @Test
    fun decode_nativePayment_showsDestinationAmountAndMemo() {
        val tx = transaction(nativePayment(BigDecimal("12.5")), memo = Memo.text("order-42"))

        val decoded = WCStellarTxSummary.decode(tx)

        assertEquals(source, decoded.sourceAccount)
        assertEquals("order-42", decoded.memo)
        assertEquals(
            listOf(Op.Payment(destination, BigDecimal("12.5000000"), AssetInfo("XLM", null), null)),
            decoded.operations
        )
    }

    @Test
    fun decode_creditAssetPayment_carriesIssuer() {
        val tx = transaction(
            PaymentOperation.builder()
                .destination(destination)
                .asset(Asset.createNonNativeAsset("USDC", issuer))
                .amount(BigDecimal("100"))
                .build()
        )

        val op = WCStellarTxSummary.decode(tx).operations.single() as Op.Payment

        assertEquals(AssetInfo("USDC", issuer), op.asset)
    }

    @Test
    fun decode_accountMerge_carriesDestination() {
        val tx = transaction(accountMerge())

        assertEquals(Op.AccountMerge(destination, null), WCStellarTxSummary.decode(tx).operations.single())
    }

    @Test
    fun decode_setOptions_carriesEveryFieldItChanges() {
        val newSigner = KeyPair.random().accountId
        val tx = transaction(
            SetOptionsOperation.builder()
                .signer(SignerKey.fromEd25519PublicKey(newSigner))
                .signerWeight(1)
                .masterKeyWeight(0)
                .lowThreshold(1)
                .mediumThreshold(1)
                .highThreshold(1)
                .homeDomain("example.com")
                .build()
        )

        assertEquals(
            Op.SetOptions(
                signer = newSigner,
                signerWeight = 1,
                masterKeyWeight = 0,
                lowThreshold = 1,
                mediumThreshold = 1,
                highThreshold = 1,
                setFlags = null,
                clearFlags = null,
                homeDomain = "example.com",
                inflationDestination = null,
                sourceAccount = null,
            ),
            WCStellarTxSummary.decode(tx).operations.single()
        )
    }

    @Test
    fun decode_unsupportedOperation_isUnknown() {
        val tx = transaction(manageData())

        assertEquals(Op.Unknown("ManageData", null), WCStellarTxSummary.decode(tx).operations.single())
    }

    @Test
    fun decode_poolShareTrustline_isUnknown() {
        val pool = LiquidityPool(AssetTypeNative(), Asset.createNonNativeAsset("USDC", issuer))
        val tx = transaction(
            ChangeTrustOperation.builder().asset(ChangeTrustAsset(pool)).limit(BigDecimal("1000")).build()
        )

        assertEquals(Op.Unknown("ChangeTrust", null), WCStellarTxSummary.decode(tx).operations.single())
    }

    @Test
    fun decode_tinyOfferPrice_isNotRoundedToZero() {
        val tx = transaction(
            ManageBuyOfferOperation.builder()
                .selling(AssetTypeNative())
                .buying(Asset.createNonNativeAsset("TOKEN", issuer))
                .amount(BigDecimal("1000"))
                .price(Price(1, 25_000_000))
                .build()
        )

        val op = WCStellarTxSummary.decode(tx).operations.single() as Op.ManageOffer

        assertEquals(0, BigDecimal("0.00000004").compareTo(op.price))
    }

    @Test
    fun decode_extendFootprintTtl_isContract() {
        val tx = transaction(extendFootprintTtl())

        assertEquals(Op.Contract("ExtendFootprintTTL", null), WCStellarTxSummary.decode(tx).operations.single())
    }

    @Test
    fun decode_noMemo_memoIsNull() {
        assertNull(WCStellarTxSummary.decode(transaction(nativePayment())).memo)
    }

    @Test
    fun warnings_setOptionsAndUnknown_returnsBothInSeverityOrder() {
        val setOptions = SetOptionsOperation.builder().masterKeyWeight(0).build()
        val decoded = WCStellarTxSummary.decode(transaction(manageData(), setOptions))

        assertEquals(
            listOf(Warning.AccountControlChange, Warning.UnreadableOperation(listOf("ManageData"))),
            WCStellarTxSummary.warnings(decoded, source)
        )
    }

    @Test
    fun warnings_accountMerge_returnsAccountMerge() {
        val decoded = WCStellarTxSummary.decode(transaction(accountMerge()))

        assertEquals(listOf(Warning.AccountMerge), WCStellarTxSummary.warnings(decoded, source))
    }

    @Test
    fun warnings_foreignTransactionSource_returnsForeignSource() {
        val decoded = WCStellarTxSummary.decode(transaction(nativePayment()))

        assertEquals(listOf(Warning.ForeignSource), WCStellarTxSummary.warnings(decoded, KeyPair.random().accountId))
    }

    @Test
    fun warnings_muxedSourceOfWallet_skipsForeignSource() {
        val muxed = MuxedAccount(source, BigInteger.valueOf(7)).address
        val tx = TransactionBuilder(Account(muxed, 1L), Network.PUBLIC).setBaseFee(100).setTimeout(0)
            .addOperation(nativePayment()).build()

        assertTrue(WCStellarTxSummary.warnings(WCStellarTxSummary.decode(tx), source).isEmpty())
    }

    @Test
    fun warnings_authChallengeFromForeignSource_returnsEmpty() {
        val decoded = WCStellarTxSummary.decode(authTransaction(manageData(), manageData()))

        assertTrue(WCStellarTxSummary.warnings(decoded, KeyPair.random().accountId).isEmpty())
    }

    @Test
    fun warnings_manageDataWithRealSequence_keepsWarnings() {
        val decoded = WCStellarTxSummary.decode(transaction(manageData(), manageData()))

        assertEquals(
            listOf(Warning.ForeignSource, Warning.UnreadableOperation(listOf("ManageData", "ManageData"))),
            WCStellarTxSummary.warnings(decoded, KeyPair.random().accountId)
        )
    }

    @Test
    fun warnings_zeroSequenceWithPayment_keepsWarnings() {
        val decoded = WCStellarTxSummary.decode(authTransaction(manageData(), nativePayment()))

        assertEquals(
            listOf(Warning.ForeignSource, Warning.UnreadableOperation(listOf("ManageData"))),
            WCStellarTxSummary.warnings(decoded, KeyPair.random().accountId)
        )
    }

    @Test
    fun warnings_contractOperation_returnsEmpty() {
        val decoded = WCStellarTxSummary.decode(transaction(extendFootprintTtl()))

        assertTrue(WCStellarTxSummary.warnings(decoded, source).isEmpty())
    }

    @Test
    fun warnings_plainPaymentFromWallet_returnsEmpty() {
        val decoded = WCStellarTxSummary.decode(transaction(nativePayment()))

        assertTrue(WCStellarTxSummary.warnings(decoded, source).isEmpty())
    }

    @Test
    fun warnings_unknownWalletAddress_skipsForeignSource() {
        val decoded = WCStellarTxSummary.decode(transaction(nativePayment()))

        assertTrue(WCStellarTxSummary.warnings(decoded, null).isEmpty())
    }

    // Sequence -1 makes the built transaction carry sequence 0, as a SEP-10 challenge does.
    private fun authTransaction(vararg operations: Operation) = transaction(*operations, sequence = -1L)

    private fun transaction(vararg operations: Operation, memo: Memo? = null, sequence: Long = 1L): Transaction {
        val builder = TransactionBuilder(Account(source, sequence), Network.PUBLIC)
            .setBaseFee(100)
            .setTimeout(0)
        operations.forEach { builder.addOperation(it) }
        memo?.let { builder.addMemo(it) }
        return builder.build()
    }

    private fun extendFootprintTtl() = ExtendFootprintTTLOperation.builder().extendTo(100L).build()

    private fun nativePayment(amount: BigDecimal = BigDecimal.ONE) =
        PaymentOperation.builder().destination(destination).asset(AssetTypeNative()).amount(amount).build()

    private fun accountMerge() = AccountMergeOperation.builder().destination(destination).build()

    private fun manageData() = ManageDataOperation.builder().name("k").value("v".toByteArray()).build()
}
