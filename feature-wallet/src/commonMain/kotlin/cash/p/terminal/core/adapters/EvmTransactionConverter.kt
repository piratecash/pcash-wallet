package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.SpamTransferDetector
import cash.p.terminal.core.tokenIconPlaceholder
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.erc20kit.decorations.ApproveEip20Decoration
import io.horizontalsystems.erc20kit.decorations.OutgoingEip20Decoration
import io.horizontalsystems.erc20kit.events.TokenInfo
import io.horizontalsystems.erc20kit.events.TransferEventInstance
import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.decorations.ContractCreationDecoration
import io.horizontalsystems.ethereumkit.decorations.IncomingDecoration
import io.horizontalsystems.ethereumkit.decorations.OutgoingDecoration
import io.horizontalsystems.ethereumkit.decorations.UnknownTransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncSourceStorage
import io.horizontalsystems.merkleiokit.MerkleTransactionAdapter
import io.horizontalsystems.oneinchkit.decorations.OneInchDecoration
import io.horizontalsystems.oneinchkit.decorations.OneInchSwapDecoration
import io.horizontalsystems.oneinchkit.decorations.OneInchUnknownDecoration
import io.horizontalsystems.oneinchkit.decorations.OneInchUnoswapDecoration
import io.horizontalsystems.uniswapkit.TokenFactory
import io.horizontalsystems.uniswapkit.decorations.SwapDecoration
import io.horizontalsystems.uniswapkit.v3.UniswapV3TransactionDecorator
import io.horizontalsystems.uniswapkit.v3.contract.UniswapV3ContractMethodFactories
import io.horizontalsystems.uniswapkit.v3.router.ExactInputMethod
import io.horizontalsystems.uniswapkit.v3.router.ExactInputSingleMethod
import io.horizontalsystems.uniswapkit.v3.router.ExactOutputMethod
import io.horizontalsystems.uniswapkit.v3.router.ExactOutputSingleMethod
import io.horizontalsystems.uniswapkit.v3.router.MulticallMethod
import java.math.BigDecimal
import java.math.BigInteger

private val V2_ROUTE_SELECTORS = setOf(0x472b43f3L, 0x42712a67L)

class EvmTransactionConverter(
    private val coinManager: ICoinManager,
    private val evmTransactionRepository: EvmTransactionRepository,
    private val source: TransactionSource,
    private val baseToken: Token,
    private val evmLabelManager: EvmLabelManager
) {

    private val wethAddress by lazy { tryOrNull { TokenFactory.getWethAddress(evmTransactionRepository.chain) } }

    fun transactionRecord(fullTransaction: FullTransaction): EvmTransactionRecord {
        val transaction = fullTransaction.transaction
        val isProtected = MerkleTransactionAdapter.isProtected(fullTransaction)
        val syncSource = TransactionSyncSourceStorage.syncSource(fullTransaction)?.displayName

        val transactionRecord = when (val decoration = fullTransaction.decoration) {
            is ContractCreationDecoration -> {
                EvmTransactionRecord(
                    transaction = transaction,
                    token = baseToken,
                    source = source,
                    protected = isProtected,
                    transactionRecordType = TransactionRecordType.EVM_CONTRACT_CREATION,
                    syncSource = syncSource
                )
            }

            is IncomingDecoration -> {
                val transactionValue = baseCoinValue(decoration.value, false)
                val isSpam = SpamTransferDetector.isSpam(
                    listOf(
                        TransferEvent(
                            decoration.from.eip55,
                            null,
                            transactionValue
                        )
                    )
                )

                EvmTransactionRecord(
                    transaction = transaction,
                    token = baseToken,
                    source = source,
                    protected = isProtected,
                    spam = isSpam,
                    from = decoration.from.eip55,
                    to = transaction.to?.eip55,
                    value = baseCoinValue(decoration.value, false),
                    transactionRecordType = TransactionRecordType.EVM_INCOMING,
                    syncSource = syncSource
                )
            }

            is OutgoingDecoration -> {
                EvmTransactionRecord(
                    transaction = transaction,
                    token = baseToken,
                    source = source,
                    protected = isProtected,
                    to = decoration.to.eip55,
                    value = baseCoinValue(decoration.value, true),
                    sentToSelf = decoration.sentToSelf,
                    transactionRecordType = TransactionRecordType.EVM_OUTGOING,
                    syncSource = syncSource
                )
            }

            is OutgoingEip20Decoration -> {
                EvmTransactionRecord(
                    transaction = transaction,
                    token = baseToken,
                    source = source,
                    protected = isProtected,
                    to = decoration.to.eip55,
                    value = getEip20Value(
                        decoration.contractAddress,
                        decoration.value,
                        true,
                        decoration.tokenInfo
                    ),
                    sentToSelf = decoration.sentToSelf,
                    transactionRecordType = TransactionRecordType.EVM_OUTGOING,
                    syncSource = syncSource
                )
            }

            is ApproveEip20Decoration -> {
                EvmTransactionRecord(
                    transaction = transaction,
                    token = baseToken,
                    source = source,
                    protected = isProtected,
                    spender = decoration.spender.eip55,
                    value = getEip20Value(decoration.contractAddress, decoration.value, false),
                    transactionRecordType = TransactionRecordType.EVM_APPROVE,
                    syncSource = syncSource
                )
            }

            is SwapDecoration -> swapRecord(transaction, decoration, isProtected, syncSource)

            is OneInchSwapDecoration -> {
                EvmTransactionRecord(
                    transaction = transaction, token = baseToken, source = source,
                    exchangeAddress = decoration.contractAddress.eip55,
                    amountIn = EvmTransactionRecord.Amount.Exact(
                        convertToTransactionValue(
                            decoration.tokenIn,
                            decoration.amountIn,
                            true
                        )
                    ),
                    amountOut = convertToAmount(decoration.tokenOut, decoration.amountOut, false),
                    recipient = decoration.recipient.recipientUnlessSelf(),
                    transactionRecordType = TransactionRecordType.EVM_SWAP,
                    protected = isProtected,
                    syncSource = syncSource
                )
            }

            is OneInchUnoswapDecoration -> {
                EvmTransactionRecord(
                    transaction = transaction, token = baseToken, source = source,
                    exchangeAddress = decoration.contractAddress.eip55,
                    amountIn = EvmTransactionRecord.Amount.Exact(
                        convertToTransactionValue(
                            decoration.tokenIn,
                            decoration.amountIn,
                            true
                        )
                    ),
                    amountOut = decoration.tokenOut?.let {
                        convertToAmount(
                            it,
                            decoration.amountOut,
                            false
                        )
                    },
                    recipient = null,
                    transactionRecordType = TransactionRecordType.EVM_SWAP,
                    protected = isProtected,
                    syncSource = syncSource
                )
            }

            is OneInchUnknownDecoration -> {
                EvmTransactionRecord(
                    transaction = transaction, token = baseToken, source = source,
                    exchangeAddress = decoration.contractAddress.eip55,
                    valueIn = decoration.tokenAmountIn?.let {
                        convertToTransactionValue(
                            it.token,
                            it.value,
                            true
                        )
                    },
                    valueOut = decoration.tokenAmountOut?.let {
                        convertToTransactionValue(
                            it.token,
                            it.value,
                            true
                        )
                    },
                    transactionRecordType = TransactionRecordType.EVM_UNKNOWN_SWAP,
                    protected = isProtected,
                    syncSource = syncSource
                )
            }

            is UnknownTransactionDecoration -> {
                val address = evmTransactionRepository.receiveAddress

                val incomingInternalTransactions =
                    decoration.internalTransactions.filter { it.to == address }
                val outgoingInternalTransactions = decoration.internalTransactions.filter {
                    it.from == address && it.to != address && it.value > BigInteger.ZERO
                }

                val eip20Transfers =
                    decoration.eventInstances.mapNotNull { it as? TransferEventInstance }
                val incomingEip20Transfers =
                    eip20Transfers.filter { it.to == address && it.from != address }
                val outgoingEip20Transfers = eip20Transfers.filter { it.from == address }

                val incomingEvents = getForeignValueEvents(transaction) +
                        getInternalEvents(incomingInternalTransactions, negative = false) +
                        getIncomingEip20Events(incomingEip20Transfers)
                var outgoingEvents = getInternalEvents(outgoingInternalTransactions, negative = true) +
                        getOutgoingEip20Events(outgoingEip20Transfers)
                val hasUserTransfers = incomingEvents.isNotEmpty() || outgoingEvents.isNotEmpty()

                val contractAddress = transaction.to
                val value = transaction.value

                val transactionRecordType = when {
                    transaction.from == address && contractAddress != null && value != null -> {
                        TransactionRecordType.EVM_CONTRACT_CALL
                    }

                    transaction.from != address && (transaction.to != address || hasUserTransfers) -> {
                        TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL
                    }

                    // Tx known only from token events (Erc20TransactionSyncer sets
                    // Transaction.to = null and copies the Transfer event's `from`,
                    // which may incorrectly equal the user for phantom/batch transfers).
                    // Without a real contract address we can't show it as a user-initiated
                    // call; treat it as an external contract call so events still render.
                    transaction.to == null -> {
                        TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL
                    }

                    else -> null
                }

                if (transactionRecordType != null) {
                    val isExternal = transactionRecordType == TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL
                    val isSpam: Boolean
                    val contractAddressEip55: String?
                    val method: String?
                    val liquidityOperation: LiquidityOperation?
                    if (isExternal) {
                        isSpam = SpamTransferDetector.isSpam(incomingEvents + outgoingEvents)
                        contractAddressEip55 = null
                        method = null
                        liquidityOperation = null
                    } else {
                        outgoingEvents = getTransactionValueEvents(transaction) + outgoingEvents
                        contractAddressEip55 = contractAddress?.eip55
                        method = transaction.input?.let { evmLabelManager.methodLabel(it) }
                        liquidityOperation = transaction.input
                            ?.let { EvmLiquidityClassifier.operation(it, method) }
                            ?.let { LiquidityOperation(it, failed = transaction.isFailed) }
                        // Unknown-method contract calls that only emit zero/dust Transfer
                        // events are the address-poisoning pattern — mark as spam so
                        // Hide Suspicious filters them out. Recognised methods stay visible.
                        isSpam = method == null && liquidityOperation == null &&
                                SpamTransferDetector.isSpam(incomingEvents + outgoingEvents)
                    }
                    val callSwapRecord = if (isExternal || liquidityOperation != null) {
                        null
                    } else {
                        swapRecordFromCall(transaction, decoration, incomingEvents, outgoingEvents, isProtected, syncSource)
                    }
                    callSwapRecord ?: EvmTransactionRecord(
                        transaction = transaction,
                        token = baseToken,
                        source = source,
                        contractAddress = contractAddressEip55,
                        method = method,
                        spam = isSpam,
                        incomingEvents = incomingEvents,
                        outgoingEvents = outgoingEvents,
                        transactionRecordType = transactionRecordType,
                        foreignTransaction = transaction.isForeign(),
                        initiator = transaction.initiatorIfForeign(),
                        protected = isProtected,
                        syncSource = syncSource,
                        liquidityOperation = liquidityOperation
                    )
                } else {
                    null
                }
            }

            else -> null
        }

        val record = transactionRecord ?: evmRecord(
            transaction, TransactionRecordType.EVM, isProtected, syncSource
        )
        if (transaction.accountDelegation != null && record.hasNoContent()) {
            return evmRecord(transaction, TransactionRecordType.EVM_ACCOUNT_DELEGATION, isProtected, syncSource)
        }
        return record
    }

    private fun swapRecord(
        transaction: Transaction,
        decoration: SwapDecoration,
        isProtected: Boolean,
        syncSource: String?,
        swapDecodedFromCall: Boolean = false,
    ) = EvmTransactionRecord(
        transaction = transaction, token = baseToken, source = source,
        exchangeAddress = decoration.contractAddress.eip55,
        amountIn = convertToAmount(decoration.tokenIn, decoration.amountIn, true),
        amountOut = convertToAmount(decoration.tokenOut, decoration.amountOut, false),
        recipient = decoration.recipient.recipientUnlessSelf(),
        transactionRecordType = TransactionRecordType.EVM_SWAP,
        protected = isProtected,
        syncSource = syncSource,
        swapDecodedFromCall = swapDecodedFromCall
    )

    private fun swapRecordFromCall(
        transaction: Transaction,
        decoration: UnknownTransactionDecoration,
        incomingEvents: List<TransferEvent>,
        outgoingEvents: List<TransferEvent>,
        isProtected: Boolean,
        syncSource: String?,
    ): EvmTransactionRecord? {
        val calls = transaction.input?.let(::flattenCalls).orEmpty()
        val methods = calls.mapNotNull { tryOrNull { UniswapV3ContractMethodFactories.createMethodFromInput(it) } }
        val exactLegs = methods.count { it.isExactSwap() }
        val routeLegs = calls.count { it.selector in V2_ROUTE_SELECTORS }
        if (exactLegs + routeLegs == 0) return null

        // The decorator takes the first leg's input but sums every output, so only a lone exact leg is safe.
        val swap = if (exactLegs == 1 && routeLegs == 0) decodedSwap(transaction, decoration, methods) else null
        return if (swap != null) {
            swapRecord(transaction, swap, isProtected, syncSource, swapDecodedFromCall = true)
        } else {
            unknownSwapRecord(transaction, incomingEvents, outgoingEvents, isProtected, syncSource)
        }
    }

    private fun ContractMethod.isExactSwap(): Boolean =
        this is ExactInputSingleMethod || this is ExactInputMethod ||
                this is ExactOutputSingleMethod || this is ExactOutputMethod

    private fun decodedSwap(
        transaction: Transaction,
        decoration: UnknownTransactionDecoration,
        methods: List<ContractMethod>,
    ): SwapDecoration? {
        val weth = wethAddress ?: return null
        val swap = tryOrNull {
            UniswapV3TransactionDecorator(weth).decoration(
                from = transaction.from,
                to = transaction.to,
                value = transaction.value,
                contractMethod = MulticallMethod(methods),
                internalTransactions = decoration.internalTransactions,
                eventInstances = decoration.eventInstances,
            ) as? SwapDecoration
        }
        // The kit sums an exact-output spend from the recipient's transfers, so another recipient reads as zero.
        return swap?.takeUnless { it.amountIn.isExactZero() || it.amountOut.isExactZero() }
    }

    private fun SwapDecoration.Amount.isExactZero(): Boolean =
        this is SwapDecoration.Amount.Exact && value.signum() == 0

    private fun unknownSwapRecord(
        transaction: Transaction,
        incomingEvents: List<TransferEvent>,
        outgoingEvents: List<TransferEvent>,
        isProtected: Boolean,
        syncSource: String?,
    ): EvmTransactionRecord? {
        // combined() throws on raw values of tokens that could not be resolved.
        val (incoming, outgoing) = tryOrNull { EvmTransactionRecord.combined(incomingEvents, outgoingEvents) }
            ?: return null
        val oneValuePerSide = if (transaction.isFailed) {
            incoming.isEmpty() && outgoing.size <= 1
        } else {
            incoming.size == 1 && outgoing.size == 1
        }
        if (!oneValuePerSide) return null

        return EvmTransactionRecord(
            transaction = transaction, token = baseToken, source = source,
            exchangeAddress = transaction.to?.eip55,
            valueIn = outgoing.firstOrNull(),
            valueOut = incoming.firstOrNull(),
            transactionRecordType = TransactionRecordType.EVM_UNKNOWN_SWAP,
            protected = isProtected,
            syncSource = syncSource,
            swapDecodedFromCall = true
        )
    }

    private fun evmRecord(
        transaction: Transaction,
        type: TransactionRecordType,
        isProtected: Boolean,
        syncSource: String?
    ) = EvmTransactionRecord(
        transaction = transaction,
        token = baseToken,
        source = source,
        foreignTransaction = transaction.isForeign(),
        initiator = transaction.initiatorIfForeign(),
        transactionRecordType = type,
        protected = isProtected,
        syncSource = syncSource
    )

    /** Records that show nothing the user can act on: only these may be replaced by the delegation record. */
    private fun EvmTransactionRecord.hasNoContent(): Boolean = when (transactionRecordType) {
        TransactionRecordType.EVM -> true
        TransactionRecordType.EVM_INCOMING,
        TransactionRecordType.EVM_OUTGOING -> (value as? TransactionValue.CoinValue)
            ?.takeIf { it.token.type == TokenType.Native }?.value?.signum() == 0
        TransactionRecordType.EVM_CONTRACT_CALL ->
            incomingEvents.isNullOrEmpty() && outgoingEvents.isNullOrEmpty()
        else -> false
    }

    private fun Transaction.isForeign(): Boolean = from != evmTransactionRepository.receiveAddress

    private fun Transaction.initiatorIfForeign(): String? = from?.takeIf { isForeign() }?.eip55

    /**
     * A swap output delivered to our own address is not a third-party recipient.
     * Kit decorators already normalize this, but rows synced before the fix still
     * hold a polluted sender, so the comparison is repeated here.
     */
    private fun Address?.recipientUnlessSelf(): String? =
        this?.takeIf { it != evmTransactionRepository.receiveAddress }?.eip55

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

    private fun getEip20Value(
        tokenAddress: Address,
        amount: BigInteger,
        negative: Boolean,
        tokenInfo: TokenInfo? = null
    ): TransactionValue {
        val query = TokenQuery.eip20(
            evmTransactionRepository.getBlockchainType(),
            tokenAddress.hex
        )
        val token = coinManager.getToken(query)

        return when {
            token != null -> {
                TransactionValue.CoinValue(token, convertAmount(amount, token.decimals, negative))
            }

            tokenInfo != null -> {
                TransactionValue.TokenValue(
                    tokenName = tokenInfo.tokenName,
                    tokenCode = tokenInfo.tokenSymbol,
                    tokenDecimals = tokenInfo.tokenDecimal,
                    value = convertAmount(amount, tokenInfo.tokenDecimal, negative),
                    coinIconPlaceholder = evmTransactionRepository.getBlockchainType().tokenIconPlaceholder
                )
            }

            else -> {
                TransactionValue.RawValue(value = amount)
            }
        }
    }

    private fun convertToTransactionValue(
        token: SwapDecoration.Token,
        amount: BigInteger,
        negative: Boolean
    ): TransactionValue {
        return when (token) {
            SwapDecoration.Token.EvmCoin -> {
                baseCoinValue(amount, negative)
            }

            is SwapDecoration.Token.Eip20Coin -> {
                getEip20Value(token.address, amount, negative, token.tokenInfo)
            }
        }
    }

    private fun convertToTransactionValue(
        token: OneInchDecoration.Token,
        amount: BigInteger,
        negative: Boolean
    ): TransactionValue {
        return when (token) {
            OneInchDecoration.Token.EvmCoin -> {
                baseCoinValue(amount, negative)
            }

            is OneInchDecoration.Token.Eip20Coin -> {
                getEip20Value(token.address, amount, negative, token.tokenInfo)
            }
        }
    }

    private fun baseCoinValue(value: BigInteger, negative: Boolean): TransactionValue {
        val amount = convertAmount(value, baseToken.decimals, negative)

        return TransactionValue.CoinValue(baseToken, amount)
    }

    private fun convertToAmount(
        token: SwapDecoration.Token,
        amount: SwapDecoration.Amount,
        negative: Boolean
    ): EvmTransactionRecord.Amount {
        return when (amount) {
            is SwapDecoration.Amount.Exact -> EvmTransactionRecord.Amount.Exact(
                convertToTransactionValue(token, amount.value, negative)
            )

            is SwapDecoration.Amount.Extremum -> EvmTransactionRecord.Amount.Extremum(
                convertToTransactionValue(token, amount.value, negative)
            )
        }
    }

    private fun convertToAmount(
        token: OneInchDecoration.Token,
        amount: OneInchDecoration.Amount,
        negative: Boolean
    ): EvmTransactionRecord.Amount {
        return when (amount) {
            is OneInchDecoration.Amount.Exact -> EvmTransactionRecord.Amount.Exact(
                convertToTransactionValue(token, amount.value, negative)
            )

            is OneInchDecoration.Amount.Extremum -> EvmTransactionRecord.Amount.Extremum(
                convertToTransactionValue(token, amount.value, negative)
            )
        }
    }

    private fun getInternalEvents(
        internalTransactions: List<InternalTransaction>,
        negative: Boolean
    ): List<TransferEvent> = internalTransactions.map {
        if (negative) {
            TransferEvent(it.to.eip55, null, baseCoinValue(it.value, true))
        } else {
            TransferEvent(it.from.eip55, it.to.eip55, baseCoinValue(it.value, false))
        }
    }

    private val Transaction.positiveValue: BigInteger?
        get() = value?.takeIf { it > BigInteger.ZERO }

    private fun getTransactionValueEvents(transaction: Transaction): List<TransferEvent> {
        val value = transaction.positiveValue ?: return listOf()

        return listOf(
            TransferEvent(transaction.to?.eip55, null, baseCoinValue(value, true))
        )
    }

    /** Native value a third party sent straight to the user's own (delegated) account. */
    private fun getForeignValueEvents(transaction: Transaction): List<TransferEvent> {
        val value = transaction.positiveValue ?: return listOf()
        if (!transaction.isForeign() || transaction.to != evmTransactionRepository.receiveAddress) return listOf()

        return listOf(
            TransferEvent(transaction.from?.eip55, transaction.to?.eip55, baseCoinValue(value, false))
        )
    }

    private fun getIncomingEip20Events(incomingTransfers: List<TransferEventInstance>): List<TransferEvent> {
        val events: MutableList<TransferEvent> = mutableListOf()

        for (transfer in incomingTransfers) {
            events.add(
                TransferEvent(
                    transfer.from.eip55,
                    transfer.to.eip55,
                    getEip20Value(
                        transfer.contractAddress,
                        transfer.value,
                        false,
                        transfer.tokenInfo
                    )
                )
            )
        }

        return events
    }

    private fun getOutgoingEip20Events(outgoingTransfers: List<TransferEventInstance>): List<TransferEvent> {
        val events: MutableList<TransferEvent> = mutableListOf()

        for (transfer in outgoingTransfers) {
            events.add(
                TransferEvent(
                    address = transfer.to.eip55,
                    addressForIncomingAddress = null,
                    value = getEip20Value(
                        transfer.contractAddress,
                        transfer.value,
                        true,
                        transfer.tokenInfo
                    )
                )
            )
        }

        return events
    }

}
