package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.modules.sendevmtransaction.ValueType
import cash.p.terminal.modules.sendevmtransaction.ViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_OperationType
import cash.p.terminal.resources.wallet_connect_stellar_account_merge_title
import cash.p.terminal.resources.wallet_connect_stellar_set_options_title
import cash.p.terminal.resources.wallet_connect_stellar_source_account
import cash.p.terminal.resources.wallet_connect_stellar_unreadable_operation_title
import io.horizontalsystems.core.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.stellar.sdk.Account
import org.stellar.sdk.Asset
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.KeyPair
import org.stellar.sdk.Network
import org.stellar.sdk.Price
import org.stellar.sdk.TransactionBuilder
import org.stellar.sdk.operations.AccountMergeOperation
import org.stellar.sdk.operations.ExtendFootprintTTLOperation
import org.stellar.sdk.operations.ManageDataOperation
import org.stellar.sdk.operations.ManageSellOfferOperation
import org.stellar.sdk.operations.Operation
import org.stellar.sdk.operations.PaymentOperation
import org.stellar.sdk.operations.SetOptionsOperation
import java.math.BigDecimal

class WCStellarHelperTest {

    private val wallet = KeyPair.random().accountId
    private val destination = KeyPair.random().accountId
    private val issuer = KeyPair.random().accountId

    @Test
    fun getTransactionViewItems_setOptionsAndUnknown_showsCriticalThenCautionAlert() {
        val alerts = alerts(SetOptionsOperation.builder().masterKeyWeight(0).build(), manageData())

        assertEquals(
            listOf(
                Translator.getString(Res.string.wallet_connect_stellar_set_options_title) to true,
                Translator.getString(Res.string.wallet_connect_stellar_unreadable_operation_title) to false,
            ),
            alerts.map { it.title to it.critical }
        )
    }

    @Test
    fun getTransactionViewItems_accountMerge_showsCriticalAlert() {
        val alerts = alerts(AccountMergeOperation.builder().destination(destination).build())

        assertEquals(
            listOf(Translator.getString(Res.string.wallet_connect_stellar_account_merge_title) to true),
            alerts.map { it.title to it.critical }
        )
    }

    @Test
    fun getTransactionViewItems_nativePayment_showsRecipientAndAmountWithoutAlerts() {
        val items = viewItems(nativePayment())

        assertTrue(items.none { it is ViewItem.Alert })
        assertTrue(items.any { it is ViewItem.Address && it.value == destination })
        assertTrue(items.any { it is ViewItem.ValueMulti && it.primaryValue == "12.5" && it.secondaryValue == "XLM" })
    }

    @Test
    fun getTransactionViewItems_sellOfferForCreditAsset_showsBuyingAssetIssuer() {
        val items = viewItems(
            ManageSellOfferOperation.builder()
                .selling(AssetTypeNative())
                .buying(Asset.createNonNativeAsset("USDC", issuer))
                .amount(BigDecimal("10"))
                .price(Price(1, 2))
                .build()
        )

        assertTrue(items.any { it is ViewItem.Address && it.value == issuer })
    }

    @Test
    fun getTransactionViewItems_foreignSource_showsCriticalAlertAndSourceAddress() {
        val foreignSource = KeyPair.random().accountId
        val items = viewItems(nativePayment(), source = foreignSource)

        val alert = items.filterIsInstance<ViewItem.Alert>().single()
        assertEquals(Translator.getString(Res.string.wallet_connect_stellar_source_account), alert.title)
        assertTrue(alert.critical)
        assertTrue(items.any { it is ViewItem.Address && it.value == foreignSource })
    }

    @Test
    fun getTransactionViewItems_foreignTransactionSpendingFromWallet_showsWalletAsOperationSource() {
        val foreignSource = KeyPair.random().accountId
        val payment = PaymentOperation.builder().sourceAccount(wallet)
            .destination(destination).asset(AssetTypeNative()).amount(BigDecimal("12.5")).build()

        val items = viewItems(payment, source = foreignSource)

        assertTrue(items.any { it is ViewItem.Address && it.value == wallet })
    }

    @Test
    fun getTransactionViewItems_authChallenge_showsNoAlerts() {
        val challenge = ManageDataOperation.builder().sourceAccount(wallet).name("k").value("v".toByteArray()).build()

        val alerts = viewItems(challenge, source = KeyPair.random().accountId, sequence = -1L)
            .filterIsInstance<ViewItem.Alert>()

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun getTransactionViewItems_authChallenge_showsOperationTypeInRegularColour() {
        val items = viewItems(manageData(), source = KeyPair.random().accountId, sequence = -1L)

        assertEquals(ValueType.Regular, operationTypeRow(items).type)
    }

    @Test
    fun getTransactionViewItems_manageDataWithRealSequence_showsOperationTypeAsWarning() {
        assertEquals(ValueType.Warning, operationTypeRow(viewItems(manageData())).type)
    }

    @Test
    fun getTransactionViewItems_contractOperation_showsTypeWithoutAlerts() {
        val items = viewItems(ExtendFootprintTTLOperation.builder().extendTo(100L).build())

        assertTrue(items.none { it is ViewItem.Alert })
        assertTrue(
            items.any {
                it is ViewItem.Value && it.value == "ExtendFootprintTTL" && it.type == ValueType.Regular &&
                    it.title == Translator.getString(Res.string.Transactions_OperationType)
            }
        )
    }

    @Test
    fun getTransactionViewItems_anyTransaction_hasNoEmptyStellarRow() {
        val items = viewItems(nativePayment())

        assertFalse(items.any { it is ViewItem.Value && it.title == "Stellar" })
    }

    private fun operationTypeRow(items: List<ViewItem>) = items.filterIsInstance<ViewItem.Value>()
        .single { it.title == Translator.getString(Res.string.Transactions_OperationType) }

    private fun alerts(vararg operations: Operation) = viewItems(*operations).filterIsInstance<ViewItem.Alert>()

    private fun viewItems(vararg operations: Operation, source: String = wallet, sequence: Long = 1L): List<ViewItem> {
        val builder = TransactionBuilder(Account(source, sequence), Network.PUBLIC).setBaseFee(100).setTimeout(0)
        operations.forEach { builder.addOperation(it) }
        val transaction = builder.build()
        return WCStellarHelper.getTransactionViewItems(transaction, transaction.toEnvelopeXdrBase64(), "dApp", wallet)
            .flatMap { it.viewItems }
    }

    private fun nativePayment() = PaymentOperation.builder()
        .destination(destination).asset(AssetTypeNative()).amount(BigDecimal("12.5")).build()

    private fun manageData() = ManageDataOperation.builder().name("k").value("v".toByteArray()).build()
}
