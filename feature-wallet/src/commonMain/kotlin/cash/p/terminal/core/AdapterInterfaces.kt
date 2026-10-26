package cash.p.terminal.core

import cash.p.terminal.core.adapters.BitcoinFeeInfo
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.providers.FeeRates
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.OfflineTransactionOutpoint
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.core.managers.MoneroSubaddressInfo
import cash.p.terminal.data.repository.EvmTransactionRepository
import io.horizontalsystems.bitcoincore.core.IPluginData
import io.horizontalsystems.bitcoincore.storage.UnspentOutputInfo
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.NowBlock
import io.horizontalsystems.tronkit.transaction.Fee
import io.reactivex.Flowable
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.math.BigDecimal
import java.math.BigInteger
import io.horizontalsystems.solanakit.models.Address as SolanaAddress
import io.horizontalsystems.tronkit.models.Address as TronAddress

interface IMwebAddressValidator {
    fun isMwebAddress(address: String): Boolean
}

interface ISendZcashAdapter : IBalanceAdapter, OfflineTransactionAdapter<SignedOfflineZcashTransaction> {
    // Start syncing the adapter
    fun start()

    // Stop the adapter (releases the session without erasing data)
    fun stop()

    suspend fun validate(address: String): ZcashAdapter.ZCashAddressType
    /** The form of [address] the signing device will show, so the user can compare the two. */
    fun addressShownOnDevice(address: String): String
    /** Returns the txid of the broadcast transaction. */
    suspend fun send(amount: BigDecimal, address: String, memo: String): String
    suspend fun getOwnAddresses(): List<String>
}

interface ITransactionsAdapter {
    val explorerTitle: String
    val transactionsState: AdapterState
    val transactionsStateUpdatedFlowable: Flowable<Unit>

    val lastBlockInfo: LastBlockInfo?
    val lastBlockUpdatedFlowable: Flowable<Unit>
    suspend fun additionalTokenQueries(): List<TokenQuery> = emptyList()

    suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord>

    suspend fun getTransactionsAfter(
        fromTransactionId: String?
    ): List<TransactionRecord> = emptyList()

    fun getRawTransaction(transactionHash: String): String? = null

    fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?
    ): Flow<List<TransactionRecord>>

    // Content-unfiltered reload signal (e.g. a stuck transaction expiring and being deleted).
    // Unlike getTransactionRecordsFlow, this is not filtered by list content, so it also
    // triggers a reload for filters (Incoming/Outgoing) that would otherwise drop empty emissions.
    fun getTransactionsReloadSignalFlow(): Flow<Unit> = emptyFlow()

    fun getTransactionUrl(transactionHash: String): String

    fun getTransactionExplorerData(record: TransactionRecord): List<TransactionExplorerData> =
        listOf(TransactionExplorerData(explorerTitle, getTransactionUrl(record.transactionHash)))
}

data class TransactionExplorerData(val title: String, val url: String?)

class UnsupportedFilterException : Exception()

interface ISendBitcoinAdapter {
    val unspentOutputs: List<UnspentOutputInfo>
    val balanceData: BalanceData
    val blockchainType: BlockchainType
    fun availableBalance(
        feeRate: Int,
        address: String?,
        memo: String?,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): BigDecimal

    fun minimumSendAmount(address: String?): BigDecimal?
    fun bitcoinFeeInfo(
        amount: BigDecimal,
        feeRate: Int,
        address: String?,
        memo: String?,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): BitcoinFeeInfo?

    fun validate(address: String, pluginData: Map<Byte, IPluginData>?)

    suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String?,
        feeRate: Int,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        transactionSorting: TransactionDataSortMode?,
        rbfEnabled: Boolean,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): String

    // Used for swap sends: returns both the record uid (history matching) and the canonical tx hash (status polling).
    suspend fun sendForSwap(
        amount: BigDecimal,
        address: String,
        memo: String?,
        feeRate: Int,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        transactionSorting: TransactionDataSortMode?,
        rbfEnabled: Boolean,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): BitcoinSwapSendResult

    fun isTransactionInSendQueue(txHash: String): Boolean

    fun satoshiToBTC(value: Long): BigDecimal
}

interface OfflineSignRequest

interface OfflineTransactionAdapter<out T> {
    suspend fun signOffline(request: OfflineSignRequest): T

    suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata? = null,
    ): BroadcastRawTransactionResult
}

interface OfflineTransactionStatusAdapter {
    suspend fun transactionExists(txHash: String): Boolean
}

data class BroadcastRawTransactionResult(
    val txHash: String,
    val status: BroadcastRawTransactionStatus,
)

enum class BroadcastRawTransactionStatus {
    // OutcomeUnknown: the node may or may not have received the transaction.
    Submitted, Queued, AlreadyKnown, SeqnoConsumed, OutcomeUnknown
}

sealed interface OfflineBroadcastMetadata {
    data class Solana(
        val blockHash: String,
        val lastValidBlockHeight: Long,
    ) : OfflineBroadcastMetadata

    data class Ton(
        val validUntil: Long,
        val senderAddress: String,
        val seqno: Int,
    ) : OfflineBroadcastMetadata

    data class Tron(
        val expiration: Long,
    ) : OfflineBroadcastMetadata

    data class Stellar(
        val sourceAccountId: String,
        val sequenceNumber: Long,
        val validUntil: Long,
    ) : OfflineBroadcastMetadata

    data class Zcash(
        val txHash: String,
    ) : OfflineBroadcastMetadata
}

data class OfflineBitcoinSignRequest(
    val amount: BigDecimal,
    val address: String,
    val memo: String?,
    val feeRate: Int,
    val unspentOutputs: List<UnspentOutputInfo>?,
    val pluginData: Map<Byte, IPluginData>?,
    val transactionSorting: TransactionDataSortMode?,
    val rbfEnabled: Boolean,
    val changeToFirstInput: Boolean,
    val utxoFilters: UtxoFilters,
) : OfflineSignRequest

data class SignedOfflineBitcoinTransaction(
    val rawHex: String,
    val txHash: String,
    val inputOutpoints: List<OfflineTransactionOutpoint>,
)

data class OfflineEvmSignRequest(
    val transactionData: TransactionData,
    val gasPrice: GasPrice,
    val gasLimit: Long,
    val nonce: Long?,
) : OfflineSignRequest

data class SignedOfflineEvmTransaction(
    val rawHex: String,
    val txHash: String,
)

data class OfflineSolanaSignRequest(
    val amount: BigDecimal,
    val address: String,
) : OfflineSignRequest

data class SignedOfflineSolanaTransaction(
    val rawHex: String,
    val txHash: String,
    val fee: BigDecimal,
    val blockHash: String,
    val lastValidBlockHeight: Long,
)

data class OfflineTonSignRequest(
    val amount: BigDecimal,
    val address: FriendlyAddress,
    val memo: String?,
    val seqno: Int,
    val validUntil: Long, // unix seconds
    val fee: BigDecimal,
) : OfflineSignRequest

data class TonOfflineAnchor(
    val seqno: Int,
    val unixTimeSeconds: Long,
)

data class SignedOfflineTonTransaction(
    val rawHex: String,
    val txHash: String,
    val fee: BigDecimal,
    val validUntil: Long,
    val senderAddress: String,
    val seqno: Int,
)

data class OfflineTronSignRequest(
    val createdTransaction: CreatedTransaction,
) : OfflineSignRequest

data class SignedOfflineTronTransaction(
    val rawHex: String,
    val txHash: String,
    val expiration: Long,
)

data class OfflineStellarSignRequest(
    val amount: BigDecimal,
    val address: String,
    val memo: String?,
) : OfflineSignRequest

interface SignedOfflineMemoTransaction {
    val rawHex: String
    val txHash: String
    val fee: BigDecimal
}

data class SignedOfflineStellarTransaction(
    override val rawHex: String,
    override val txHash: String,
    override val fee: BigDecimal,
    val sourceAccountId: String,
    val sequenceNumber: Long,
    val validUntil: Long,
) : SignedOfflineMemoTransaction

data class OfflineThorchainSignRequest(
    val amount: BigDecimal,
    val address: String,
    val memo: String?,
) : OfflineSignRequest

data class SignedOfflineThorchainTransaction(
    override val rawHex: String,
    override val txHash: String,
    override val fee: BigDecimal,
) : SignedOfflineMemoTransaction

data class OfflineMoneroSignRequest(
    val amount: BigDecimal,
    val address: String,
    val memo: String?,
) : OfflineSignRequest

data class SignedOfflineMoneroTransaction(
    val rawHex: String,
    val txHash: String,
    val fee: BigDecimal,
)

data class OfflineZcashSignRequest(
    val amount: BigDecimal,
    val address: String,
    val memo: String,
) : OfflineSignRequest

data class SignedOfflineZcashTransaction(
    val rawHex: String,
    val txHash: String,
    val fee: BigDecimal,
)
data class BitcoinSwapSendResult(val uid: String, val canonicalHashReversedHex: String)

interface ISendEthereumAdapter : IBalanceAdapter {
    val evmTransactionRepository: EvmTransactionRepository

    fun getTransactionData(amount: BigDecimal, address: Address): TransactionData
}

interface ISendSolanaAdapter: IBalanceAdapter {
    suspend fun send(amount: BigDecimal, to: SolanaAddress): FullTransaction
    suspend fun send(rawTransaction: ByteArray): FullTransaction
    fun estimateFee(rawTransaction: ByteArray): BigDecimal
}

interface IMoneroHardwareWalletAdapter {
    val hardwareWallet: Boolean
    val spendReadiness: StateFlow<MoneroSpendReadiness>

    suspend fun refreshHardwareKeyImages()
    suspend fun fullWalletRecovery()
}

fun Throwable.isEligibleForMoneroFullWalletRecovery(): Boolean =
    this is HardwareWalletOperationException && error == HardwareWalletErrorCode.Protocol

interface ISendMoneroAdapter :
    IBalanceAdapter,
    OfflineTransactionAdapter<SignedOfflineMoneroTransaction>,
    IMoneroHardwareWalletAdapter {

    suspend fun send(amount: BigDecimal, address: String, memo: String?): String
    suspend fun estimateFee(amount: BigDecimal, address: String, memo: String?): BigDecimal
}

interface IMoneroReceiveAdapter : IReceiveAdapter, IMoneroHardwareWalletAdapter {
    suspend fun getSubaddresses(): List<MoneroSubaddressInfo>
    suspend fun createNewSubaddress(): String
    suspend fun displayAddressOnDevice(addressIndex: Int)
}

interface ISendTonAdapter : IBalanceAdapter {
    suspend fun send(amount: BigDecimal, address: FriendlyAddress, memo: String?)
    suspend fun sendWithPayload(amount: BigInteger, address: String, payload: String)
    suspend fun estimateFee(amount: BigDecimal, address: FriendlyAddress, memo: String?) : BigDecimal
    suspend fun fetchOfflineAnchor(): TonOfflineAnchor
}

interface ISendMemoAdapter : IBalanceAdapter {
    val sendFee: BigDecimal
    val sendFeeUpdatedFlow: Flow<Unit>
        get() = emptyFlow()
    fun validate(address: String)
    suspend fun getMinimumSendAmount(address: String) : BigDecimal?
    suspend fun send(amount: BigDecimal, address: String, memo: String?): String?
}

interface INativeBalanceProvider {
    val nativeBalanceData: BalanceData
    val nativeBalanceUpdatedFlow: Flow<Unit>
}

/** Balance as last read from the wallet store; null until it has been read, which is not zero. */
interface ILastKnownBalanceAdapter {
    val lastKnownBalanceData: BalanceData?
}

interface ISendTronAdapter : IBalanceAdapter {
    val trxBalanceData: BalanceData

    suspend fun estimateFee(amount: BigDecimal, to: TronAddress): List<Fee>
    suspend fun estimateFee(transaction: CreatedTransaction): List<Fee>
    suspend fun estimateFee(contract: Contract): List<Fee>
    suspend fun send(amount: BigDecimal, to: TronAddress, feeLimit: Long?): String
    suspend fun send(contract: Contract, feeLimit: Long?): String
    suspend fun send(createdTransaction: CreatedTransaction): String
    fun isOwnAddress(address: TronAddress): Boolean

    suspend fun getNowBlock(): NowBlock
    fun buildOfflineTransaction(
        amount: BigDecimal,
        to: TronAddress,
        feeLimit: Long?,
        block: NowBlock,
        timestamp: Long,
        expiration: Long,
    ): CreatedTransaction
}
interface IFeeRateProvider {
    val feeRateChangeable: Boolean get() = false
    suspend fun getFeeRates() : FeeRates
}


interface ICoinManager {
    fun getToken(query: TokenQuery): Token?
}
