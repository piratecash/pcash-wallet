package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.modules.sendevmtransaction.SectionViewItem
import cash.p.terminal.modules.sendevmtransaction.ValueType
import cash.p.terminal.modules.sendevmtransaction.ViewItem
import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.AssetInfo
import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.Decoded
import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.Op
import cash.p.terminal.modules.walletconnect.stellar.WCStellarTxSummary.Warning
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Confirmation_To
import cash.p.terminal.resources.Swap_Price
import cash.p.terminal.resources.TransactionInfo_Memo
import cash.p.terminal.resources.Transactions_OperationType
import cash.p.terminal.resources.Transactions_OperationType_ChangeTrust
import cash.p.terminal.resources.Transactions_OperationType_CreateAccount
import cash.p.terminal.resources.WalletConnect_SignMessageRequest_dApp
import cash.p.terminal.resources.wallet_connect_stellar_account_merge
import cash.p.terminal.resources.wallet_connect_stellar_account_merge_title
import cash.p.terminal.resources.wallet_connect_stellar_amount
import cash.p.terminal.resources.wallet_connect_stellar_asset
import cash.p.terminal.resources.wallet_connect_stellar_asset_issuer
import cash.p.terminal.resources.wallet_connect_stellar_buying
import cash.p.terminal.resources.wallet_connect_stellar_clear_flags
import cash.p.terminal.resources.wallet_connect_stellar_foreign_source
import cash.p.terminal.resources.wallet_connect_stellar_high_threshold
import cash.p.terminal.resources.wallet_connect_stellar_home_domain
import cash.p.terminal.resources.wallet_connect_stellar_inflation_destination
import cash.p.terminal.resources.wallet_connect_stellar_low_threshold
import cash.p.terminal.resources.wallet_connect_stellar_master_key_weight
import cash.p.terminal.resources.wallet_connect_stellar_max_sent
import cash.p.terminal.resources.wallet_connect_stellar_medium_threshold
import cash.p.terminal.resources.wallet_connect_stellar_min_received
import cash.p.terminal.resources.wallet_connect_stellar_op_account_merge
import cash.p.terminal.resources.wallet_connect_stellar_op_buy_offer
import cash.p.terminal.resources.wallet_connect_stellar_op_path_payment
import cash.p.terminal.resources.wallet_connect_stellar_op_payment
import cash.p.terminal.resources.wallet_connect_stellar_op_sell_offer
import cash.p.terminal.resources.wallet_connect_stellar_op_set_options
import cash.p.terminal.resources.wallet_connect_stellar_received
import cash.p.terminal.resources.wallet_connect_stellar_selling
import cash.p.terminal.resources.wallet_connect_stellar_set_flags
import cash.p.terminal.resources.wallet_connect_stellar_set_options
import cash.p.terminal.resources.wallet_connect_stellar_set_options_title
import cash.p.terminal.resources.wallet_connect_stellar_signer
import cash.p.terminal.resources.wallet_connect_stellar_signer_weight
import cash.p.terminal.resources.wallet_connect_stellar_source_account
import cash.p.terminal.resources.wallet_connect_stellar_trust_limit
import cash.p.terminal.resources.wallet_connect_stellar_unreadable_operation
import cash.p.terminal.resources.wallet_connect_stellar_unreadable_operation_title
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import org.jetbrains.compose.resources.StringResource
import org.stellar.sdk.Transaction
import java.math.BigDecimal

object WCStellarHelper {

    private val XLM = AssetInfo("XLM", null)

    /** [walletAddress] is the active account; null skips the foreign-source warning. */
    fun getTransactionViewItems(
        transaction: Transaction,
        xdr: String,
        peerName: String,
        walletAddress: String?,
    ): List<SectionViewItem> {
        val decoded = WCStellarTxSummary.decode(transaction)
        val warnings = WCStellarTxSummary.warnings(decoded, walletAddress)

        return buildList {
            if (warnings.isNotEmpty()) add(SectionViewItem(warnings.map(::alert)))
            if (Warning.ForeignSource in warnings) {
                val source = address(Res.string.wallet_connect_stellar_source_account, decoded.sourceAccount)
                add(SectionViewItem(listOf(source)))
            }
            decoded.operations.forEach { add(SectionViewItem(operationRows(it, decoded))) }
            add(
                SectionViewItem(
                    buildList {
                        decoded.memo?.let { add(value(Res.string.TransactionInfo_Memo, it)) }
                        add(ViewItem.Input("Transaction XDR", xdr))
                    }
                )
            )
            add(SectionViewItem(listOf(value(Res.string.WalletConnect_SignMessageRequest_dApp, peerName))))
        }
    }

    private fun alert(warning: Warning): ViewItem.Alert = when (warning) {
        Warning.AccountControlChange -> alert(
            Res.string.wallet_connect_stellar_set_options_title,
            text(Res.string.wallet_connect_stellar_set_options),
            critical = true
        )
        Warning.AccountMerge -> alert(
            Res.string.wallet_connect_stellar_account_merge_title,
            text(Res.string.wallet_connect_stellar_account_merge),
            critical = true
        )
        Warning.ForeignSource -> alert(
            Res.string.wallet_connect_stellar_source_account,
            text(Res.string.wallet_connect_stellar_foreign_source),
            critical = true
        )
        is Warning.UnreadableOperation -> alert(
            Res.string.wallet_connect_stellar_unreadable_operation_title,
            Translator.getString(
                Res.string.wallet_connect_stellar_unreadable_operation,
                warning.typeNames.joinToString(", ")
            ),
            critical = false
        )
    }

    private fun alert(title: StringResource, text: String, critical: Boolean) =
        ViewItem.Alert(text(title), text, critical)

    private fun operationRows(op: Op, decoded: Decoded): List<ViewItem> = buildList {
        when (op) {
            is Op.Payment -> {
                operation(Res.string.wallet_connect_stellar_op_payment)
                amount(Res.string.wallet_connect_stellar_amount, op.amount, op.asset, ValueType.Outgoing)
                to(op.destination)
            }
            is Op.CreateAccount -> {
                operation(Res.string.Transactions_OperationType_CreateAccount)
                amount(Res.string.wallet_connect_stellar_amount, op.startingBalance, XLM, ValueType.Outgoing)
                to(op.destination)
            }
            is Op.AccountMerge -> {
                operation(Res.string.wallet_connect_stellar_op_account_merge)
                to(op.destination)
            }
            is Op.PathPaymentStrictSend ->
                addPathPayment(op.destination, op.sendAmount, op.sendAsset, op.destMin, op.destAsset)
            is Op.PathPaymentStrictReceive ->
                addPathPayment(
                    op.destination, op.sendMax, op.sendAsset, op.destAmount, op.destAsset, strictReceive = true
                )
            is Op.ChangeTrust -> addChangeTrust(op)
            is Op.ManageOffer -> addOffer(op)
            is Op.SetOptions -> addSetOptions(op)
            is Op.Unknown -> {
                val type = if (decoded.isAuthChallenge) ValueType.Regular else ValueType.Warning
                add(value(Res.string.Transactions_OperationType, op.typeName, type))
            }
            is Op.Contract -> add(value(Res.string.Transactions_OperationType, op.typeName))
        }

        // Shown against the transaction source, not the wallet: a foreign transaction may still spend from the wallet.
        op.sourceAccount?.takeIf { it != decoded.sourceAccount }?.let {
            add(address(Res.string.wallet_connect_stellar_source_account, it))
        }
    }

    /** Strict-send fixes the sent amount and bounds the received one; strict-receive is the reverse. */
    private fun MutableList<ViewItem>.addPathPayment(
        destination: String,
        sent: BigDecimal,
        sentAsset: AssetInfo,
        received: BigDecimal,
        receivedAsset: AssetInfo,
        strictReceive: Boolean = false,
    ) {
        operation(Res.string.wallet_connect_stellar_op_path_payment)
        if (strictReceive) {
            amount(Res.string.wallet_connect_stellar_max_sent, sent, sentAsset, ValueType.Outgoing)
            amount(Res.string.wallet_connect_stellar_received, received, receivedAsset, ValueType.Incoming)
        } else {
            amount(Res.string.wallet_connect_stellar_amount, sent, sentAsset, ValueType.Outgoing)
            amount(Res.string.wallet_connect_stellar_min_received, received, receivedAsset, ValueType.Incoming)
        }
        to(destination)
    }

    private fun MutableList<ViewItem>.addChangeTrust(op: Op.ChangeTrust) {
        operation(Res.string.Transactions_OperationType_ChangeTrust)
        add(value(Res.string.wallet_connect_stellar_asset, op.asset.code))
        issuer(op.asset)
        add(value(Res.string.wallet_connect_stellar_trust_limit, op.limit.plain()))
    }

    private fun MutableList<ViewItem>.addOffer(op: Op.ManageOffer) {
        if (op.isBuy) {
            operation(Res.string.wallet_connect_stellar_op_buy_offer)
            add(amountRow(Res.string.wallet_connect_stellar_amount, op.amount, op.buying, ValueType.Regular))
        } else {
            operation(Res.string.wallet_connect_stellar_op_sell_offer)
            add(amountRow(Res.string.wallet_connect_stellar_amount, op.amount, op.selling, ValueType.Regular))
        }
        add(value(Res.string.wallet_connect_stellar_selling, op.selling.code))
        issuer(op.selling)
        add(value(Res.string.wallet_connect_stellar_buying, op.buying.code))
        issuer(op.buying)
        add(value(Res.string.Swap_Price, op.price.plain()))
    }

    private fun MutableList<ViewItem>.addSetOptions(op: Op.SetOptions) {
        operation(Res.string.wallet_connect_stellar_op_set_options)
        op.signer?.let { add(address(Res.string.wallet_connect_stellar_signer, it)) }
        warning(Res.string.wallet_connect_stellar_signer_weight, op.signerWeight)
        warning(Res.string.wallet_connect_stellar_master_key_weight, op.masterKeyWeight)
        warning(Res.string.wallet_connect_stellar_low_threshold, op.lowThreshold)
        warning(Res.string.wallet_connect_stellar_medium_threshold, op.mediumThreshold)
        warning(Res.string.wallet_connect_stellar_high_threshold, op.highThreshold)
        op.setFlags?.let { add(value(Res.string.wallet_connect_stellar_set_flags, it.toString())) }
        op.clearFlags?.let { add(value(Res.string.wallet_connect_stellar_clear_flags, it.toString())) }
        op.homeDomain?.let { add(value(Res.string.wallet_connect_stellar_home_domain, it)) }
        op.inflationDestination?.let { add(address(Res.string.wallet_connect_stellar_inflation_destination, it)) }
    }

    private fun MutableList<ViewItem>.operation(name: StringResource) {
        add(value(Res.string.Transactions_OperationType, text(name)))
    }

    private fun MutableList<ViewItem>.to(destination: String) {
        add(address(Res.string.Send_Confirmation_To, destination))
    }

    private fun MutableList<ViewItem>.issuer(asset: AssetInfo) {
        asset.issuer?.let { add(address(Res.string.wallet_connect_stellar_asset_issuer, it)) }
    }

    private fun MutableList<ViewItem>.amount(
        title: StringResource,
        amount: BigDecimal,
        asset: AssetInfo,
        type: ValueType,
    ) {
        add(amountRow(title, amount, asset, type))
        issuer(asset)
    }

    // Two lines (amount, asset code): a single-line value would clip either of them on narrow screens.
    private fun amountRow(title: StringResource, amount: BigDecimal, asset: AssetInfo, type: ValueType) =
        ViewItem.ValueMulti(text(title), amount.plain(), asset.code, type)

    private fun MutableList<ViewItem>.warning(title: StringResource, number: Int?) {
        number?.let { add(value(title, it.toString(), ValueType.Warning)) }
    }

    private fun text(res: StringResource) = Translator.getString(res)

    private fun value(title: StringResource, value: String, type: ValueType = ValueType.Regular) =
        ViewItem.Value(text(title), value, type)

    private fun address(title: StringResource, value: String) =
        ViewItem.Address(text(title), value, showAdd = false, blockchainType = BlockchainType.Stellar)

    private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
}
