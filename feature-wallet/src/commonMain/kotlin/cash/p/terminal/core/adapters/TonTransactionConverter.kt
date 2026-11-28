package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.LiquidityOperation.Direction
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.tonconnect.TonConnectRequestMessage
import cash.p.terminal.modules.tonconnect.TonConnectRequestPreview
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.transaction.TransactionSource
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.AccountAddress
import io.horizontalsystems.tonkit.models.Action
import io.horizontalsystems.tonkit.models.EmulationRisk
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.LiquidityDeposit
import io.horizontalsystems.tonkit.models.NftTransfer
import io.horizontalsystems.tonkit.models.TransactionDetails
import java.math.BigDecimal
import java.math.BigInteger

class TonTransactionConverter(
    private val address: Address,
    private val coinManager: ICoinManager,
    private val source: TransactionSource,
    private val baseToken: Token,
    private val walletVersion: TonWalletVersion,
) {

    fun createTransactionRecord(
        event: Event,
        nftItems: Map<String, NftItemRecord> = emptyMap(),
    ): TonTransactionRecord {
        val actions = event.actions.map { action ->
            val status = when (action.status) {
                Action.Status.OK -> TransactionStatus.Completed
                Action.Status.FAILED -> TransactionStatus.Failed
            }

            TonTransactionRecord.Action(
                type = getActionType(action, nftItems),
                status = status
            )
        }

        return TonTransactionRecord(
            source = source,
            event = event,
            token = baseToken,
            actions = actions,
            liquidityOperation = liquidityOperation(event)
        )
    }

    /** `nftItems` is keyed by [NftItemRecord.address]; NFTs missing from it are shown without name and image. */
    fun requestPreview(
        details: TransactionDetails,
        messages: List<RawMessageEntity>,
        nftItems: Map<String, NftItemRecord>,
    ): TonConnectRequestPreview {
        val event = details.event.withoutRefunds(paidAmounts(messages))
        val recognized = event.actions.filter {
            it.status == Action.Status.OK &&
                    getActionType(it, nftItems) !is TonTransactionRecord.Action.Type.Unsupported
        }
        return TonConnectRequestPreview(
            record = createTransactionRecord(event, nftItems),
            requestMessages = messages.map {
                TonConnectRequestMessage(address = it.addressValue, value = tonValue(it.amount.toBigInteger(), true))
            },
            riskValues = riskJettons(details.risk, recognized) + riskNfts(details.risk, recognized, nftItems),
        )
    }

    private fun paidAmounts(messages: List<RawMessageEntity>): Map<Address, BigInteger> =
        messages.mapNotNull { message ->
            tryOrNull { Address.parse(message.addressValue) }?.let { it to message.amount.toBigInteger() }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.reduce(BigInteger::add) }

    /**
     * TON that a paid contract sends back is not income: drop it and net it against what was sent there,
     * so the visible lines and the fee add up to the real balance change. A sender's returns are capped by
     * what the request paid it, so a payout stays visible.
     */
    private fun Event.withoutRefunds(paid: Map<Address, BigInteger>): Event {
        val budget = paid.toMutableMap()
        val refunds = actions.withIndex().mapNotNull { (index, action) ->
            val transfer = action.tonTransfer?.takeIf {
                action.status == Action.Status.OK && it.recipient.address == address && it.sender.address != address
            } ?: return@mapNotNull null
            val left = budget[transfer.sender.address]?.takeIf { transfer.amount <= it } ?: return@mapNotNull null
            budget[transfer.sender.address] = left - transfer.amount
            index to transfer
        }
        if (refunds.isEmpty()) return this

        val refundIndexes = refunds.map { it.first }.toSet()
        var visible = actions.filterIndexed { index, _ -> index !in refundIndexes }
        var adjustedExtra = extra
        refunds.forEach { (_, refund) ->
            val (netted, rest) = visible.nettedAgainst(refund.sender.address, refund.amount)
            visible = netted
            adjustedExtra += rest.toLong()
        }
        return copy(actions = visible, extra = adjustedExtra)
    }

    /** Lowers the visible TON sent to [target] by up to [amount]; returns what could not be netted. */
    private fun List<Action>.nettedAgainst(target: Address, amount: BigInteger): Pair<List<Action>, BigInteger> {
        var left = amount
        val netted = map { action ->
            val sent = action.tonSentTo(target) ?: return@map action
            val cut = sent.min(left)
            left -= cut
            action.copy(
                tonTransfer = action.tonTransfer?.copy(amount = sent - cut),
                smartContractExec = action.smartContractExec?.copy(tonAttached = sent - cut),
            )
        }
        return netted to left
    }

    private fun Action.tonSentTo(target: Address): BigInteger? = when {
        status != Action.Status.OK -> null
        tonTransfer?.let { it.sender.address == address && it.recipient.address == target } == true ->
            tonTransfer?.amount

        smartContractExec?.contract?.address == target -> smartContractExec?.tonAttached
        else -> null
    }

    private fun riskJettons(risk: EmulationRisk, recognized: List<Action>): List<TransactionValue> {
        val shown = recognized.flatMap { it.jettonDebits() }.toMutableList()
        return risk.jettons
            .filterNot { shown.remove(it.jetton.address to it.amount) }
            .map { jettonValue(it.jetton, it.amount, true) }
    }

    private fun riskNfts(
        risk: EmulationRisk,
        recognized: List<Action>,
        nftItems: Map<String, NftItemRecord>,
    ): List<TransactionValue> {
        val shown = recognized.mapNotNull { action ->
            action.nftTransfer?.takeIf { it.sender?.address == address }?.nft
        }.toMutableList()
        return risk.nfts
            .filterNot { shown.remove(it) }
            .map { nftValue(it, BigDecimal.ONE.negate(), nftItems) }
    }

    /** Jetton amounts a recognized action takes from the wallet, keyed by master address so metadata cannot differ. */
    private fun Action.jettonDebits(): List<Pair<Address, BigInteger>> = buildList {
        jettonTransfer?.takeIf { it.sender?.address == address }?.let { add(it.jetton.address to it.amount) }
        jettonBurn?.takeIf { it.sender.address == address }?.let { add(it.jetton.address to it.amount) }
        jettonSwap?.let { swap -> swap.jettonMasterIn?.let { add(it.address to swap.amountIn) } }
        liquidityDeposit?.takeIf { it.from.address == address }?.tokens?.forEach { token ->
            token.jetton?.let { add(it.address to token.amount) }
        }
    }

    private fun liquidityOperation(event: Event): LiquidityOperation? {
        val deposit = event.actions.firstOrNull { it.isLiquidityDeposit() }
        if (deposit != null) {
            return LiquidityOperation(Direction.Add, failed = deposit.status == Action.Status.FAILED)
        }
        return LiquidityOperation(Direction.Remove, failed = false).takeIf { event.isLiquidityWithdrawal() }
    }

    private fun Action.isLiquidityDeposit(): Boolean =
        liquidityDeposit?.from?.address == address ||
                jettonTransfer?.let { it.sender?.address == address && it.comment in DEPOSIT_COMMENTS } == true ||
                smartContractExec?.operation == DEDUST_DEPOSIT_OPERATION

    private fun Event.isLiquidityWithdrawal(): Boolean {
        val ownBurns = actions.count { action ->
            action.status == Action.Status.OK &&
                    action.jettonBurn?.let { it.sender.address == address && it.amount.signum() > 0 } == true
        }
        val receivedJettons = actions.any { action ->
            action.status == Action.Status.OK &&
                    action.jettonTransfer?.let { it.recipient?.address == address && it.amount.signum() > 0 } == true
        }
        return ownBurns == 1 && receivedJettons
    }

    private fun convertAmount(amount: BigInteger, decimal: Int, negative: Boolean): BigDecimal {
        var significandAmount = amount.toBigDecimal().movePointLeft(decimal).stripTrailingZeros()

        if (significandAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
        }

        if (negative) {
            significandAmount = significandAmount.negate()
        }

        return significandAmount
    }

    private fun tonValue(value: BigInteger, negative: Boolean): TransactionValue {
        val amount = convertAmount(value, baseToken.decimals, negative)
        return TransactionValue.CoinValue(baseToken, amount)
    }

    private fun jettonValue(jetton: Jetton, value: BigInteger, negative: Boolean): TransactionValue {
        val query = TonAsset.Jetton(jetton.address.toUserFriendly(bounceable = true), walletVersion).tokenQuery

        val token = coinManager.getToken(query)

        return if (token != null) {
            TransactionValue.CoinValue(token, convertAmount(value, token.decimals, negative))
        } else {
            TransactionValue.JettonValue(
                jetton.name,
                jetton.symbol,
                jetton.decimals,
                convertAmount(value, jetton.decimals, negative),
                jetton.image
            )
        }
    }

    private fun liquidityDepositEntry(token: LiquidityDeposit.Token) =
        TonTransactionRecord.Action.Type.LiquidityDeposit.Deposit(
            value = token.jetton?.let { jettonValue(it, token.amount, true) } ?: tonValue(token.amount, true),
            vault = token.vault.toUserFriendly(bounceable = true)
        )

    private fun format(address: AccountAddress): String {
        return address.address.toUserFriendly(bounceable = !address.isWallet)
    }

    private fun getActionType(action: Action, nftItems: Map<String, NftItemRecord>): TonTransactionRecord.Action.Type {
        action.tonTransfer?.let { tonTransfer ->
            return when {
                tonTransfer.sender.address == address -> {
                    TonTransactionRecord.Action.Type.Send(
                        value = tonValue(tonTransfer.amount, true),
                        to = format(tonTransfer.recipient),
                        sentToSelf = tonTransfer.recipient.address == address,
                        comment = tonTransfer.comment
                    )
                }

                tonTransfer.recipient.address == address -> {
                    TonTransactionRecord.Action.Type.Receive(
                        value = tonValue(tonTransfer.amount, false),
                        from = format(tonTransfer.sender),
                        to = format(tonTransfer.recipient),
                        comment = tonTransfer.comment
                    )
                }

                else -> TonTransactionRecord.Action.Type.Unsupported("Ton Transfer")
            }
        }

        action.jettonTransfer?.let { jettonTransfer ->
            val recipient = jettonTransfer.recipient
            val sender = jettonTransfer.sender

            return when {
                jettonTransfer.sender?.address == address && recipient != null -> {
                    TonTransactionRecord.Action.Type.Send(
                        value = jettonValue(jettonTransfer.jetton, jettonTransfer.amount, true),
                        to = format(recipient),
                        sentToSelf = jettonTransfer.recipient?.address == address,
                        comment = jettonTransfer.comment
                    )
                }

                jettonTransfer.recipient?.address == address && sender != null -> {
                    TonTransactionRecord.Action.Type.Receive(
                        value = jettonValue(jettonTransfer.jetton, jettonTransfer.amount, false),
                        from = format(sender),
                        to = recipient?.let { format(it) }.orEmpty(),
                        comment = jettonTransfer.comment
                    )
                }

                else -> TonTransactionRecord.Action.Type.Unsupported("Jetton Transfer")
            }
        }

        action.jettonBurn?.let { jettonBurn ->
            return TonTransactionRecord.Action.Type.Burn(
                value = jettonValue(jettonBurn.jetton, jettonBurn.amount, true)
            )
        }

        action.jettonMint?.let { jettonMint ->
            return TonTransactionRecord.Action.Type.Mint(
                value = jettonValue(jettonMint.jetton, jettonMint.amount, false),
                to = format(jettonMint.recipient)
            )
        }

        action.contractDeploy?.let { contractDeploy ->
            return TonTransactionRecord.Action.Type.ContractDeploy(
                interfaces = contractDeploy.interfaces
            )
        }

        action.jettonSwap?.let { jettonSwap ->
            return TonTransactionRecord.Action.Type.Swap(
                routerName = jettonSwap.router.name,
                routerAddress = format(jettonSwap.router),
                valueIn = jettonSwap.jettonMasterIn?.let {
                    jettonValue(it, jettonSwap.amountIn, true)
                } ?: tonValue(jettonSwap.tonIn ?: BigInteger.ZERO, true),
                valueOut = jettonSwap.jettonMasterOut?.let {
                    jettonValue(it, jettonSwap.amountOut, false)
                } ?: tonValue(jettonSwap.tonOut ?: BigInteger.ZERO, false),
            )
        }

        action.smartContractExec?.let { smartContractExec ->
            return TonTransactionRecord.Action.Type.ContractCall(
                address = format(smartContractExec.contract),
                value = tonValue(smartContractExec.tonAttached, true),
                operation = smartContractExec.operation
            )
        }

        action.liquidityDeposit?.let { deposit ->
            if (deposit.from.address == address) {
                return TonTransactionRecord.Action.Type.LiquidityDeposit(
                    deposits = deposit.tokens.map { liquidityDepositEntry(it) },
                    protocol = deposit.protocol
                )
            }
        }

        return action.nftTransfer?.let { nftTransferType(it, nftItems) }
            ?: TonTransactionRecord.Action.Type.Unsupported(action.type.name)
    }

    private fun nftTransferType(
        nftTransfer: NftTransfer,
        nftItems: Map<String, NftItemRecord>,
    ): TonTransactionRecord.Action.Type? {
        val sender = nftTransfer.sender ?: return null
        val recipient = nftTransfer.recipient ?: return null
        return when (address) {
            sender.address -> TonTransactionRecord.Action.Type.Send(
                value = nftValue(nftTransfer.nft, BigDecimal.ONE.negate(), nftItems),
                to = format(recipient),
                sentToSelf = recipient.address == address,
                comment = nftTransfer.comment,
            )

            recipient.address -> TonTransactionRecord.Action.Type.Receive(
                value = nftValue(nftTransfer.nft, BigDecimal.ONE, nftItems),
                from = format(sender),
                to = format(recipient),
                comment = nftTransfer.comment,
            )

            else -> null
        }
    }

    private fun nftValue(
        nft: Address,
        value: BigDecimal,
        nftItems: Map<String, NftItemRecord>,
    ): TransactionValue.NftValue {
        val mintAddress = nft.toUserFriendly(bounceable = true)
        val item = nftItems[mintAddress]
        return TransactionValue.NftValue(
            mintAddress = mintAddress,
            value = value,
            tokenName = item?.name,
            tokenSymbol = "NFT",
            imageUrl = item?.imageUrl,
        )
    }

    private companion object {
        val DEPOSIT_COMMENTS = setOf("Call: StonfiProvideLiquidity", "Call: DedustDepositLiquidity")
        const val DEDUST_DEPOSIT_OPERATION = "DedustDepositLiquidity"
    }
}
