package cash.p.terminal.modules.walletconnect.stellar

import org.stellar.sdk.Asset
import org.stellar.sdk.AssetTypeCreditAlphaNum
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.MemoHash
import org.stellar.sdk.MemoId
import org.stellar.sdk.MemoReturnHash
import org.stellar.sdk.MemoText
import org.stellar.sdk.MuxedAccount
import org.stellar.sdk.Price
import org.stellar.sdk.Transaction
import org.stellar.sdk.operations.AccountMergeOperation
import org.stellar.sdk.operations.ChangeTrustOperation
import org.stellar.sdk.operations.CreateAccountOperation
import org.stellar.sdk.operations.ExtendFootprintTTLOperation
import org.stellar.sdk.operations.InvokeHostFunctionOperation
import org.stellar.sdk.operations.ManageBuyOfferOperation
import org.stellar.sdk.operations.ManageDataOperation
import org.stellar.sdk.operations.ManageSellOfferOperation
import org.stellar.sdk.operations.Operation
import org.stellar.sdk.operations.PathPaymentStrictReceiveOperation
import org.stellar.sdk.operations.PathPaymentStrictSendOperation
import org.stellar.sdk.operations.PaymentOperation
import org.stellar.sdk.operations.RestoreFootprintOperation
import org.stellar.sdk.operations.SetOptionsOperation
import java.math.BigDecimal
import java.math.MathContext

/** Decodes a WalletConnect Stellar transaction for the sign screen; unsupported operations become [Op.Unknown]. */
object WCStellarTxSummary {

    data class AssetInfo(val code: String, val issuer: String?)

    sealed class Op {
        abstract val sourceAccount: String?

        data class Payment(
            val destination: String,
            val amount: BigDecimal,
            val asset: AssetInfo,
            override val sourceAccount: String?,
        ) : Op()

        data class CreateAccount(
            val destination: String,
            val startingBalance: BigDecimal,
            override val sourceAccount: String?,
        ) : Op()

        /** Sends the account's ENTIRE balance to [destination] and deletes the account. */
        data class AccountMerge(val destination: String, override val sourceAccount: String?) : Op()

        data class PathPaymentStrictSend(
            val destination: String,
            val sendAmount: BigDecimal,
            val sendAsset: AssetInfo,
            val destMin: BigDecimal,
            val destAsset: AssetInfo,
            override val sourceAccount: String?,
        ) : Op()

        data class PathPaymentStrictReceive(
            val destination: String,
            val sendMax: BigDecimal,
            val sendAsset: AssetInfo,
            val destAmount: BigDecimal,
            val destAsset: AssetInfo,
            override val sourceAccount: String?,
        ) : Op()

        data class ChangeTrust(val asset: AssetInfo, val limit: BigDecimal, override val sourceAccount: String?) : Op()

        data class ManageOffer(
            val isBuy: Boolean,
            val selling: AssetInfo,
            val buying: AssetInfo,
            val amount: BigDecimal,
            val price: BigDecimal,
            override val sourceAccount: String?,
        ) : Op()

        /** A null field is left untouched by the operation. */
        data class SetOptions(
            val signer: String?,
            val signerWeight: Int?,
            val masterKeyWeight: Int?,
            val lowThreshold: Int?,
            val mediumThreshold: Int?,
            val highThreshold: Int?,
            val setFlags: Int?,
            val clearFlags: Int?,
            val homeDomain: String?,
            val inflationDestination: String?,
            override val sourceAccount: String?,
        ) : Op()

        data class Unknown(val typeName: String, override val sourceAccount: String?) : Op()

        /** Soroban: the contract's effect is not decoded, but this is not reported as unreadable. */
        data class Contract(val typeName: String, override val sourceAccount: String?) : Op()
    }

    data class Decoded(
        val sourceAccount: String,
        /** Null for MemoNone. */
        val memo: String?,
        val operations: List<Op>,
        /** SEP-10 login: sequence 0 can never be applied on-chain and ManageData moves no funds. */
        val isAuthChallenge: Boolean,
    )

    /** Ordered by severity: critical first, then caution. */
    sealed interface Warning {
        data object AccountControlChange : Warning
        data object AccountMerge : Warning
        data object ForeignSource : Warning
        data class UnreadableOperation(val typeNames: List<String>) : Warning
    }

    fun decode(transaction: Transaction): Decoded = Decoded(
        sourceAccount = transaction.sourceAccount,
        memo = memoText(transaction),
        // Explicit type: the SDK array element carries compile-only lombok.NonNull.
        operations = transaction.operations.map { operation: Operation -> decode(operation) },
        isAuthChallenge = transaction.sequenceNumber == 0L &&
            transaction.operations.all { operation: Operation -> operation is ManageDataOperation },
    )

    fun warnings(decoded: Decoded, walletAddress: String?): List<Warning> = buildList {
        if (decoded.isAuthChallenge) return@buildList
        if (decoded.operations.any { it is Op.SetOptions }) add(Warning.AccountControlChange)
        if (decoded.operations.any { it is Op.AccountMerge }) add(Warning.AccountMerge)
        // An M-address of the wallet's own G-account is still the wallet.
        if (walletAddress != null && MuxedAccount(decoded.sourceAccount).accountId != walletAddress) {
            add(Warning.ForeignSource)
        }
        val unknown = decoded.operations.filterIsInstance<Op.Unknown>()
        if (unknown.isNotEmpty()) add(Warning.UnreadableOperation(unknown.map { it.typeName }))
    }

    private fun decode(operation: Operation): Op {
        val source = operation.sourceAccount
        return when (operation) {
            is PaymentOperation -> Op.Payment(operation.destination, operation.amount, asset(operation.asset), source)
            is CreateAccountOperation -> Op.CreateAccount(operation.destination, operation.startingBalance, source)
            is AccountMergeOperation -> Op.AccountMerge(operation.destination, source)
            is PathPaymentStrictSendOperation -> Op.PathPaymentStrictSend(
                operation.destination, operation.sendAmount, asset(operation.sendAsset),
                operation.destMin, asset(operation.destAsset), source
            )
            is PathPaymentStrictReceiveOperation -> Op.PathPaymentStrictReceive(
                operation.destination, operation.sendMax, asset(operation.sendAsset),
                operation.destAmount, asset(operation.destAsset), source
            )
            // A liquidity-pool share trustline has no code/issuer to show, so it is reported as unreadable.
            is ChangeTrustOperation -> operation.asset.asset
                ?.let { Op.ChangeTrust(asset(it), operation.limit, source) }
                ?: Op.Unknown(typeName(operation), source)
            is ManageSellOfferOperation ->
                offer(false, operation.selling, operation.buying, operation.amount, operation.price, source)
            is ManageBuyOfferOperation ->
                offer(true, operation.selling, operation.buying, operation.amount, operation.price, source)
            is SetOptionsOperation -> setOptions(operation, source)
            is InvokeHostFunctionOperation, is ExtendFootprintTTLOperation, is RestoreFootprintOperation ->
                Op.Contract(typeName(operation), source)
            else -> Op.Unknown(typeName(operation), source)
        }
    }

    private fun offer(
        isBuy: Boolean,
        selling: Asset,
        buying: Asset,
        amount: BigDecimal,
        price: Price,
        source: String?,
    ) = Op.ManageOffer(isBuy, asset(selling), asset(buying), amount, price(price.numerator, price.denominator), source)

    private fun setOptions(operation: SetOptionsOperation, source: String?) = Op.SetOptions(
        signer = operation.signer?.encodedSignerKey,
        signerWeight = operation.signerWeight,
        masterKeyWeight = operation.masterKeyWeight,
        lowThreshold = operation.lowThreshold,
        mediumThreshold = operation.mediumThreshold,
        highThreshold = operation.highThreshold,
        setFlags = operation.setFlags,
        clearFlags = operation.clearFlags,
        homeDomain = operation.homeDomain,
        inflationDestination = operation.inflationDestination,
        sourceAccount = source,
    )

    private fun typeName(operation: Operation) = operation.javaClass.simpleName.removeSuffix("Operation")

    private fun asset(asset: Asset): AssetInfo = when (asset) {
        is AssetTypeNative -> AssetInfo("XLM", null)
        is AssetTypeCreditAlphaNum -> AssetInfo(asset.code, asset.issuer)
        else -> AssetInfo(asset.toString(), null)
    }

    // Significant digits, not a fixed scale: a tiny price like 1/25_000_000 must not render as 0.
    private fun price(numerator: Int, denominator: Int): BigDecimal =
        if (denominator == 0) BigDecimal.ZERO
        else BigDecimal(numerator).divide(BigDecimal(denominator), MathContext.DECIMAL64)

    private fun memoText(transaction: Transaction): String? = when (val memo = transaction.memo) {
        is MemoText -> memo.text
        is MemoId -> memo.id.toString()
        is MemoHash -> memo.hexValue
        is MemoReturnHash -> memo.hexValue
        else -> null
    }
}
