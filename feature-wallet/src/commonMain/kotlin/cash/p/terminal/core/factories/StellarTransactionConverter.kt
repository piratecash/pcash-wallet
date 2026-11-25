package cash.p.terminal.core.factories

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.SpamTransferDetector
import cash.p.terminal.core.tokenIconPlaceholder
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.protocolType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.room.ContractMovement
import io.horizontalsystems.stellarkit.room.Operation
import io.horizontalsystems.stellarkit.room.StellarAsset
import java.math.BigDecimal
import kotlin.let

class StellarTransactionConverter(
    private val source: TransactionSource,
    private val selfAddress: String,
    private val coinManager: ICoinManager,
    private val baseToken: Token,
) {
    fun convert(operation: Operation): StellarTransactionRecord {
        var type: StellarTransactionRecord.Type = StellarTransactionRecord.Type.Unsupported(operation.type)

        operation.payment?.let { payment ->
            val outgoing = payment.from == selfAddress
            val incoming = payment.to == selfAddress

            var amount = payment.amount
            if (outgoing) {
                amount = amount.negate()
            }

            val transactionValue = transactionValue(payment.asset, amount)

            when {
                outgoing -> {
                    type = StellarTransactionRecord.Type.Send(
                        value = transactionValue,
                        to = payment.to,
                        sentToSelf = incoming,
                        comment = operation.memo,
                        accountCreated = false,
                    )
                }
                incoming -> {
                    type = StellarTransactionRecord.Type.Receive(
                        value = transactionValue,
                        from = payment.from,
                        to = payment.to,
                        comment = operation.memo,
                        accountCreated = false,
                    )
                }
            }
        }

        operation.accountCreated?.let { accountCreated ->
            val outgoing = accountCreated.funder == selfAddress
            val incoming = accountCreated.account == selfAddress

            var amount = accountCreated.startingBalance
            if (outgoing) {
                amount = amount.negate()
            }

            val transactionValue = TransactionValue.CoinValue(baseToken, amount)

            when {
                outgoing -> {
                    type = StellarTransactionRecord.Type.Send(
                        value = transactionValue,
                        to = accountCreated.account,
                        sentToSelf = incoming,
                        comment = operation.memo,
                        accountCreated = true
                    )
                }
                incoming -> {
                    type = StellarTransactionRecord.Type.Receive(
                        value = transactionValue,
                        from = accountCreated.funder,
                        to = accountCreated.account,
                        comment = operation.memo,
                        accountCreated = true
                    )
                }
            }
        }

        pathPaymentType(operation)?.let { type = it }
        contractCallType(operation)?.let { type = it }

        operation.changeTrust?.let { changeTrust ->
            type = StellarTransactionRecord.Type.ChangeTrust(
                changeTrust.trustee,
                transactionValue(changeTrust.asset, changeTrust.limit)
            )
        }

        val eventsForPhishingCheck = StellarTransactionRecord.eventsForPhishingCheck(type)
        val spam = SpamTransferDetector.isSpam(eventsForPhishingCheck)

        return StellarTransactionRecord(baseToken, source, operation, type, spam)
    }

    private fun pathPaymentType(operation: Operation): StellarTransactionRecord.Type? {
        val pathPayment = operation.pathPayment ?: return null
        val sold = transactionValue(pathPayment.sourceAsset, pathPayment.sourceAmount.negate())
        val received = transactionValue(pathPayment.asset, pathPayment.amount)
        val outgoing = pathPayment.from == selfAddress
        val incoming = pathPayment.to == selfAddress

        return when {
            outgoing && incoming -> StellarTransactionRecord.Type.Swap(valueIn = sold, valueOut = received)
            outgoing -> send(sold, pathPayment.to, operation.memo)
            incoming -> receive(received, pathPayment.from, operation.memo)
            else -> null
        }
    }

    // Several counterparties keep the amount but name no single address.
    private fun contractCallType(operation: Operation): StellarTransactionRecord.Type? =
        when (val movement = operation.contractMovement(selfAddress)) {
            is ContractMovement.Swap -> StellarTransactionRecord.Type.Swap(
                valueIn = transactionValue(movement.sold),
                valueOut = transactionValue(movement.received),
            )
            is ContractMovement.Outgoing -> send(
                transactionValue(movement.movement),
                movement.counterparties.singleOrNull()?.address,
                operation.memo
            )
            is ContractMovement.Incoming -> receive(
                transactionValue(movement.movement),
                movement.counterparties.singleOrNull()?.address,
                operation.memo
            ).copy(
                senderTransfers = movement.counterparties.map {
                    TransferEvent(it.address, null, transactionValue(movement.movement.asset, it.amount))
                }
            )
            is ContractMovement.Unrepresentable,
            ContractMovement.None -> null
        }

    private fun send(value: TransactionValue, to: String?, memo: String?) = StellarTransactionRecord.Type.Send(
        value = value,
        to = to,
        sentToSelf = false,
        comment = memo,
        accountCreated = false,
    )

    private fun receive(value: TransactionValue, from: String?, memo: String?) = StellarTransactionRecord.Type.Receive(
        value = value,
        from = from,
        to = selfAddress,
        comment = memo,
        accountCreated = false,
    )

    private fun transactionValue(movement: ContractMovement.Movement) =
        transactionValue(movement.asset, movement.amount)

    private fun transactionValue(asset: StellarAsset, amount: BigDecimal): TransactionValue {
        getToken(asset)?.let { return TransactionValue.CoinValue(it, amount) }

        val code = when (asset) {
            StellarAsset.Native -> baseToken.coin.code
            is StellarAsset.Asset -> asset.code
        }
        return TransactionValue.TokenValue(
            tokenName = code,
            tokenCode = code,
            tokenDecimals = 7,
            value = amount,
            coinIconPlaceholder = BlockchainType.Stellar.tokenIconPlaceholder,
            badge = tokenQuery(asset).protocolType?.uppercase(),
        )
    }

    private fun getToken(asset: StellarAsset): Token? = coinManager.getToken(tokenQuery(asset))

    private fun tokenQuery(asset: StellarAsset): TokenQuery {
        val tokenType = when (asset) {
            StellarAsset.Native -> TokenType.Native
            is StellarAsset.Asset -> TokenType.Asset(asset.code, asset.issuer)
        }

        return TokenQuery(BlockchainType.Stellar, tokenType)
    }
}
