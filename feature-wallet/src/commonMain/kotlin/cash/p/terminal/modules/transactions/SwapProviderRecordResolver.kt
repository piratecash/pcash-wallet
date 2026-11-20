package cash.p.terminal.modules.transactions

import cash.p.terminal.core.adapters.BitcoinReplacementChain
import cash.p.terminal.core.adapters.BitcoinReplacementRegistry
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.IncomingTransaction
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.wallet.Token
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

/** The provider swap a list row displays a record as; an incoming record is the swap's payout. */
data class ResolvedProviderSwap(val swap: SwapProviderTransaction, val isIncoming: Boolean)

class SwapProviderRecordResolver(
    private val storage: SwapProviderTransactionsStorage,
    private val swapTransactionMatcher: SwapTransactionMatcher,
) {
    private val replacementRegistry: BitcoinReplacementRegistry by inject(BitcoinReplacementRegistry::class.java)

    private sealed interface SwapLookup {
        class Incoming(val transaction: IncomingTransaction) : SwapLookup
        class Outgoing(val record: TransactionRecord, val token: Token) : SwapLookup
    }

    /** Read-only: search must never write swap links for rows the user has not seen. */
    fun find(record: TransactionRecord): ResolvedProviderSwap? =
        resolve(record, knownSwap = replacementChainSwap(record), link = false)

    /** The list row's lookup: [matchedSwap] wins, links found on the way are stored. */
    fun findAndLink(record: TransactionRecord, matchedSwap: SwapProviderTransaction?): ResolvedProviderSwap? =
        resolve(record, knownSwap = matchedSwap, link = true)

    /** The replacement chain [record] won, when the swap may still sit on another of its members. */
    fun replacementChainOf(record: BitcoinTransactionRecord): BitcoinReplacementChain? {
        if (record.replacementChainUids.isEmpty() || record.replacedByHash != null) return null
        if (record.transactionRecordType == TransactionRecordType.BITCOIN_INCOMING) return null
        return replacementRegistry.chainOf(
            record.source.account.id,
            record.token.blockchainType.uid,
            record.transactionHash
        )
    }

    private fun replacementChainSwap(record: TransactionRecord): SwapProviderTransaction? {
        if (record !is BitcoinTransactionRecord) return null
        replacementChainOf(record) ?: return null
        return record.replacementCandidateUids.firstNotNullOfOrNull(storage::getByOutgoingRecordUid)
    }

    private fun resolve(
        record: TransactionRecord,
        knownSwap: SwapProviderTransaction?,
        link: Boolean,
    ): ResolvedProviderSwap? {
        val lookup = lookupOf(record) ?: return null
        val swap = knownSwap ?: when (lookup) {
            is SwapLookup.Incoming -> findIncoming(lookup.transaction, link)
            is SwapLookup.Outgoing -> findOutgoing(lookup.record, lookup.token, link)
        } ?: return null
        return ResolvedProviderSwap(swap, isIncoming = lookup is SwapLookup.Incoming)
    }

    private fun lookupOf(record: TransactionRecord): SwapLookup? = when (record) {
        is EvmTransactionRecord -> evmLookup(record)
        is TronTransactionRecord -> tronLookup(record)
        is SolanaTransactionRecord -> solanaLookup(record)
        is PendingTransactionRecord -> outgoing(record, record.token)
        else -> directedLookup(record)
    }

    private fun evmLookup(record: EvmTransactionRecord): SwapLookup? = when (record.transactionRecordType) {
        TransactionRecordType.EVM_INCOMING -> incoming(record, record.token)
        TransactionRecordType.EVM_OUTGOING -> outgoing(record, record.mainCoinToken)
        TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL ->
            externalCallPayout(record, record.incomingEvents, record.outgoingEvents)

        else -> null
    }

    private fun tronLookup(record: TronTransactionRecord): SwapLookup? = when (record.transactionRecordType) {
        TransactionRecordType.TRON_INCOMING -> incoming(record, record.token)
        TransactionRecordType.TRON_OUTGOING -> outgoing(record, record.token)
        TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL ->
            externalCallPayout(record, record.incomingEvents, record.outgoingEvents)

        else -> null
    }

    private fun solanaLookup(record: SolanaTransactionRecord): SwapLookup? = when (record.transactionRecordType) {
        TransactionRecordType.SOLANA_INCOMING -> incoming(record, record.mainCoinToken)
        TransactionRecordType.SOLANA_OUTGOING -> outgoing(record, record.token)
        else -> null
    }

    private fun directedLookup(record: TransactionRecord): SwapLookup? = when (record) {
        is BitcoinTransactionRecord ->
            directed(record, record.token, record.transactionRecordType == TransactionRecordType.BITCOIN_INCOMING)

        // An NFT quantity of 1 would otherwise match a provider swap of 1 TON.
        is TonTransactionRecord -> directed(
            record,
            record.token.takeUnless { record.isNftOnly },
            record.actions.singleOrNull()?.type is TonTransactionRecord.Action.Type.Receive
        )

        is StellarTransactionRecord ->
            directed(record, record.token, record.type is StellarTransactionRecord.Type.Receive)

        is ThorchainTransactionRecord ->
            directed(record, record.token, record.type is ThorchainTransactionRecord.Type.Incoming)

        is MoneroTransactionRecord ->
            directed(record, record.token, record.transactionRecordType == TransactionRecordType.MONERO_INCOMING)

        else -> null
    }

    private fun directed(record: TransactionRecord, token: Token?, isIncoming: Boolean): SwapLookup? =
        if (isIncoming) incoming(record, token) else outgoing(record, token)

    private fun incoming(record: TransactionRecord, token: Token?): SwapLookup? =
        token?.let { incomingLookup(record, record.mainValue?.decimalValue, it, record.to) }

    private fun outgoing(record: TransactionRecord, token: Token?): SwapLookup? =
        token?.let { SwapLookup.Outgoing(record, it) }

    // A contract call that only paid us can be a provider swap's payout.
    private fun externalCallPayout(
        record: TransactionRecord,
        incomingEvents: List<TransferEvent>?,
        outgoingEvents: List<TransferEvent>?,
    ): SwapLookup? {
        val (incomingValues, outgoingValues) =
            EvmTransactionRecord.combined(incomingEvents.orEmpty(), outgoingEvents.orEmpty())
        if (outgoingValues.isNotEmpty()) return null
        val payout = incomingValues.firstOrNull() ?: return null
        return incomingLookup(
            record = record,
            amount = payout.decimalValue,
            token = (payout as? TransactionValue.CoinValue)?.token ?: record.token,
            addresses = incomingEvents?.firstOrNull()?.addressForIncomingAddress?.let(::listOf),
        )
    }

    private fun incomingLookup(record: TransactionRecord, amount: BigDecimal?, token: Token, addresses: List<String>?) =
        SwapLookup.Incoming(
            IncomingTransaction(
                uid = record.uid,
                amount = amount?.abs(),
                timestamp = record.timestamp * 1000,
                token = token,
                addresses = addresses,
            )
        )

    private fun findIncoming(transaction: IncomingTransaction, link: Boolean): SwapProviderTransaction? =
        if (link) {
            swapTransactionMatcher.findMatchingSwap(transaction)
        } else {
            swapTransactionMatcher.findMatch(transaction)?.swap
        }

    private fun findOutgoing(record: TransactionRecord, token: Token, link: Boolean): SwapProviderTransaction? {
        val outgoingRecordUid = record.outgoingRecordUidForSwapMatching()
        outgoingRecordUid?.let(storage::getByOutgoingRecordUid)?.let { return it }
        val swap = storage.getByTokenIn(
            token = (record.mainValue as? TransactionValue.CoinValue)?.token ?: token,
            amountIn = record.mainValue?.decimalValue?.abs(),
            timestamp = record.timestamp * 1000
        ) ?: return null
        if (link && outgoingRecordUid != null && swap.outgoingRecordUid != outgoingRecordUid) {
            storage.setOutgoingRecordUid(date = swap.date, outgoingRecordUid = outgoingRecordUid)
        }
        return swap
    }

    private fun TransactionRecord.outgoingRecordUidForSwapMatching(): String? = when (this) {
        is PendingTransactionRecord -> transactionHash.takeIf { it.isNotBlank() }
        else -> uid
    }
}

/** Every uid the chain's swap can be stored under, including this record's own. */
internal val BitcoinTransactionRecord.replacementCandidateUids: List<String>
    get() = replacementChainUids + uid

private val TransactionRecord.mainCoinToken: Token?
    get() = (mainValue as? TransactionValue.CoinValue)?.token
