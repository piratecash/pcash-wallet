package cash.p.terminal.modules.transactions

import cash.p.terminal.core.managers.localizedEvmMethodLabel
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.transactions_liquidity_search_key
import io.horizontalsystems.core.Translator
import java.util.Locale

/** Built once per page load: [providerFingerprint] changes with any provider swap the search can read. */
data class PreparedSearchQuery(
    val normalizedQuery: String,
    /** Null when neither matching nor the caller's acceptance reads swap storage. */
    val providerFingerprint: Int?,
    internal val searchesProviderSwaps: Boolean,
)

/** [providerSwap] is set when the record matched as that provider swap; otherwise it was not looked up. */
class TransactionSearchMatch(val providerSwap: ResolvedProviderSwap?)

class TransactionRecordSearchMatcher(
    private val swapProviderRecordResolver: SwapProviderRecordResolver,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
) {

    /** Whether [prepare] for the same arguments depends on swap storage; reads no storage itself. */
    fun readsProviderSwaps(query: String, readsProviderSwaps: Boolean): Boolean =
        readsProviderSwaps || query.normalizedSearchValue().isInProviderTitle()

    /** [readsProviderSwaps]: the caller's own acceptance reads swap storage, so the fingerprint must cover it. */
    fun prepare(query: String, readsProviderSwaps: Boolean = false): PreparedSearchQuery {
        val normalizedQuery = query.normalizedSearchValue()
        val inProviderTitle = normalizedQuery.isInProviderTitle()
        if (!inProviderTitle && !readsProviderSwaps) {
            return PreparedSearchQuery(normalizedQuery, null, searchesProviderSwaps = false)
        }

        val providerSwaps = swapProviderTransactionsStorage.getAllAcrossAccounts()
        val searchesProviderSwaps = inProviderTitle && providerSwaps.isNotEmpty()
        return PreparedSearchQuery(normalizedQuery, providerSwaps.hashCode(), searchesProviderSwaps)
    }

    fun matches(record: TransactionRecord, query: PreparedSearchQuery): Boolean = match(record, query) != null

    fun match(record: TransactionRecord, query: PreparedSearchQuery): TransactionSearchMatch? {
        val normalizedQuery = query.normalizedQuery
        if (normalizedQuery.isEmpty() || searchableFields(record).any { it.containsQuery(normalizedQuery) }) {
            return TransactionSearchMatch(providerSwap = null)
        }
        if (!query.searchesProviderSwaps) return null

        val providerSwap = swapProviderRecordResolver.find(record) ?: return null
        return TransactionSearchMatch(providerSwap)
            .takeIf { providerSwap.displayTitles().any { it.containsQuery(normalizedQuery) } }
    }

    // Search has no on-chain progress, so a payout may show either title of a finished swap.
    private fun ResolvedProviderSwap.displayTitles(): List<String> = listOfNotNull(
        swap.displayStatus(payoutConfirming = false).listTitle(),
        swap.displayStatus(payoutConfirming = true).listTitle().takeIf { isIncoming },
    )

    private fun searchableFields(record: TransactionRecord): List<String> = buildList {
        addCommonFields(record)
        addTypeSpecificFields(record)
    }.filterNotNull()

    private fun MutableList<String?>.addCommonFields(record: TransactionRecord) {
        add(record.transactionHash)
        add(record.from)
        record.to?.let(::addAll)
        add(record.memo)
        add(record.listTitle())
        add(record.token.coin.uid)
        add(record.token.coin.code)
        add(record.token.coin.name)
        addValue(record.mainValue)
        record.liquidityOperation?.let {
            add(Translator.getString(it.title))
            add(Translator.getString(Res.string.transactions_liquidity_search_key))
        }
    }

    private fun MutableList<String?>.addTypeSpecificFields(record: TransactionRecord) {
        when (record) {
            is BitcoinTransactionRecord -> addBitcoinFields(record)
            is EvmTransactionRecord -> addEvmFields(record)
            is TronTransactionRecord -> addTronFields(record)
            is SolanaTransactionRecord -> addSolanaFields(record)
            is TonTransactionRecord -> addTonFields(record)
            is StellarTransactionRecord -> addStellarFields(record)
            is MoneroTransactionRecord -> add(record.subaddressLabel)
            is BeamTransactionRecord -> {
                add(record.kernelId)
                add(record.failureReason)
            }
        }
    }

    private fun MutableList<String?>.addBitcoinFields(record: BitcoinTransactionRecord) {
        add(record.canonicalTransactionHash)
        record.changeAddresses?.let(::addAll)
    }

    private fun MutableList<String?>.addEvmFields(record: EvmTransactionRecord) {
        add(record.spender)
        add(record.contractAddress)
        addMethod(record.method)
        add(record.exchangeAddress)
        add(record.recipient)
        addEvents(record.incomingEvents)
        addEvents(record.outgoingEvents)
        addValue(record.value)
        addValue(record.valueIn)
        addValue(record.valueOut)
    }

    private fun MutableList<String?>.addTronFields(record: TronTransactionRecord) {
        add(record.spender)
        add(record.contractAddress)
        addMethod(record.method)
        addEvents(record.incomingEvents)
        addEvents(record.outgoingEvents)
        addValue(record.value)
    }

    private fun MutableList<String?>.addMethod(method: String?) {
        method ?: return
        add(method)
        add(localizedEvmMethodLabel(method))
    }

    private fun MutableList<String?>.addSolanaFields(record: SolanaTransactionRecord) {
        addSolanaTransfers(record.incomingSolanaTransfers)
        addSolanaTransfers(record.outgoingSolanaTransfers)
    }

    private fun MutableList<String?>.addTonFields(record: TonTransactionRecord) {
        record.actions.forEach { action ->
            when (val type = action.type) {
                is TonTransactionRecord.Action.Type.Send -> addTonSendFields(type)
                is TonTransactionRecord.Action.Type.Receive -> addTonReceiveFields(type)
                is TonTransactionRecord.Action.Type.Burn -> addValue(type.value)
                is TonTransactionRecord.Action.Type.Mint -> {
                    add(type.to)
                    addValue(type.value)
                }

                is TonTransactionRecord.Action.Type.Swap -> {
                    add(type.routerName)
                    add(type.routerAddress)
                    addValue(type.valueIn)
                    addValue(type.valueOut)
                }

                is TonTransactionRecord.Action.Type.ContractDeploy -> addAll(type.interfaces)
                is TonTransactionRecord.Action.Type.ContractCall -> {
                    add(type.address)
                    add(type.operation)
                    addValue(type.value)
                }

                is TonTransactionRecord.Action.Type.LiquidityDeposit -> {
                    add(type.protocol)
                    type.deposits.forEach {
                        add(it.vault)
                        addValue(it.value)
                    }
                }

                is TonTransactionRecord.Action.Type.Unsupported -> add(type.type)
            }
        }
    }

    private fun MutableList<String?>.addTonSendFields(type: TonTransactionRecord.Action.Type.Send) {
        add(type.to)
        add(type.comment)
        addValue(type.value)
    }

    private fun MutableList<String?>.addTonReceiveFields(type: TonTransactionRecord.Action.Type.Receive) {
        add(type.from)
        add(type.to)
        add(type.comment)
        addValue(type.value)
    }

    private fun MutableList<String?>.addStellarFields(record: StellarTransactionRecord) {
        add(record.operation.memo)
        when (val type = record.type) {
            is StellarTransactionRecord.Type.Send -> {
                add(type.to)
                add(type.comment)
                addValue(type.value)
            }

            is StellarTransactionRecord.Type.Receive -> {
                add(type.from)
                add(type.to)
                add(type.comment)
                addValue(type.value)
            }

            is StellarTransactionRecord.Type.ChangeTrust -> {
                add(type.trustee)
                addValue(type.value)
            }

            is StellarTransactionRecord.Type.Swap -> {
                addValue(type.valueIn)
                addValue(type.valueOut)
            }

            is StellarTransactionRecord.Type.Unsupported -> add(type.type)
        }
    }

    private fun MutableList<String?>.addValue(value: TransactionValue?) {
        value ?: return
        add(value.coinUid)
        add(value.coinCode)
        add(value.fullName)
        add(value.badge)
        if (value is TransactionValue.NftValue) add(value.mintAddress)
    }

    private fun MutableList<String?>.addEvents(events: List<TransferEvent>?) {
        events?.forEach { event ->
            add(event.address)
            add(event.addressForIncomingAddress)
            addValue(event.value)
        }
    }

    private fun MutableList<String?>.addSolanaTransfers(
        transfers: List<SolanaTransactionRecord.SolanaTransfer>?
    ) {
        transfers?.forEach { transfer ->
            add(transfer.address)
            add(transfer.addressForIncomingAddress)
            addValue(transfer.value)
        }
    }

    private fun String.normalizedSearchValue() = trim().lowercase(Locale.ROOT)

    private fun String.containsQuery(normalizedQuery: String) = normalizedSearchValue().contains(normalizedQuery)

    private fun String.isInProviderTitle() =
        isNotEmpty() && TransactionStatusEnum.entries.any { it.listTitle().containsQuery(this) }
}
