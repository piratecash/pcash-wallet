package cash.p.terminal.core.factories

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.tokenIconPlaceholder
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.thorchainkit.models.CoinTransfer
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.Network
import java.math.BigDecimal
import java.math.BigInteger

class ThorchainTransactionConverter(
    private val coinManager: ICoinManager,
    private val source: TransactionSource,
    private val userAddress: String,
    private val baseToken: Token,
    private val network: Network,
) {
    // Midgard `in` transfers are spent by their address and `out` transfers are received by it. A swap
    // puts the user on both sides with different assets, so every user-side transfer is its own record.
    fun convert(transaction: Transaction): List<ThorchainTransactionRecord> {
        val userSpends = transaction.incoming.filter { it.address == userAddress }
        val userReceives = transaction.outgoing.filter { it.address == userAddress }
        val spentAssets = userSpends.mapTo(mutableSetOf()) { it.asset }
        val fee = fee(transaction)

        val outgoing = userSpends.map { transfer ->
            val type = ThorchainTransactionRecord.Type.Outgoing(
                value = transactionValue(transfer, negative = true),
                to = transaction.outgoing.firstOrNull { it.asset == transfer.asset }?.address,
                sentToSelf = userReceives.any { it.asset == transfer.asset },
            )
            record(transaction, transfer, type, spam = false, fee = fee)
        }
        // A same-asset receive in an action the user spent from is change, a self-send or a refund,
        // already shown by the outgoing record.
        val incoming = userReceives.filter { it.asset !in spentAssets }.map { transfer ->
            val type = ThorchainTransactionRecord.Type.Incoming(
                value = transactionValue(transfer, negative = false),
                from = transaction.incoming.firstOrNull { it.asset == transfer.asset }?.address,
            )
            record(
                transaction,
                transfer,
                type,
                SpamManager.isSpam(listOf(TransferEvent(type.from, null, type.value))),
                fee = null,
            )
        }

        return outgoing + incoming
    }

    private fun record(
        transaction: Transaction,
        transfer: CoinTransfer,
        type: ThorchainTransactionRecord.Type,
        spam: Boolean,
        fee: TransactionValue?,
    ) = ThorchainTransactionRecord(
        uid = ThorchainTransactionRecord.uid(transaction.hash, denom(transfer.asset) ?: transfer.asset.lowercase()),
        transaction = transaction,
        spam = spam,
        source = source,
        token = (type.value as? TransactionValue.CoinValue)?.token ?: baseToken,
        type = type,
        fee = fee,
    )

    private fun transactionValue(transfer: CoinTransfer, negative: Boolean): TransactionValue {
        val amount = transfer.amount.toCoinAmount().let { if (negative) it.negate() else it }
        val token = token(transfer.asset)
        if (token != null) return TransactionValue.CoinValue(token, amount)

        val ticker = tryOrNull { network.assetResolver.assetFor(transfer.asset).ticker }
            ?: transfer.asset.uppercase()
        return TransactionValue.TokenValue(
            tokenName = ticker,
            tokenCode = ticker,
            tokenDecimals = network.decimals,
            value = amount,
            coinIconPlaceholder = source.blockchain.type.tokenIconPlaceholder,
        )
    }

    private fun fee(transaction: Transaction): TransactionValue? =
        transaction.fee?.takeIf { it.signum() > 0 }?.let { TransactionValue.CoinValue(baseToken, it.toCoinAmount()) }

    private fun BigInteger.toCoinAmount(): BigDecimal = toBigDecimal().movePointLeft(network.decimals)

    private fun token(midgardAsset: String): Token? {
        val denom = denom(midgardAsset) ?: return null
        if (denom == network.nativeDenom) return baseToken
        return coinManager.getToken(TokenQuery(source.blockchain.type, TokenType.ThorchainAsset(denom)))
    }

    // Midgard mixes full notation ("THOR.RUNE") with bank denoms ("TCY"); the resolver reads both.
    private fun denom(midgardAsset: String): String? =
        tryOrNull { network.assetResolver.run { denomFor(assetFor(midgardAsset)) } }
}
