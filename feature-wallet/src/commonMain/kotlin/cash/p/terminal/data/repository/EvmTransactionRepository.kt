package cash.p.terminal.data.repository

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.KitPlatformContexts
import cash.p.terminal.modules.evmfee.estimateNativeTransferFee
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.api.models.AccountState
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.EthereumKit.HistoricalSyncState
import io.horizontalsystems.ethereumkit.core.EthereumKit.SyncState
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransactionBroadcastResult
import io.horizontalsystems.ethereumkit.models.SignedRawTransaction
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.await
import java.math.BigInteger

class EvmTransactionRepository(
    private val evmBlockchainManager: EvmBlockchainManager,
    private val platformContexts: KitPlatformContexts,
) {
    private var kitWrapper: EvmKitWrapper? = null
    private var _blockchainType: BlockchainType? = null

    val evmKitWrapper: EvmKitWrapper
        get() = requireNotNull(kitWrapper)

    private val evmKit: EthereumKit
        get() = evmKitWrapper.evmKit

    val lastBlockHeight: Long?
        get() = evmKit.lastBlockHeight

    val lastBlockHeightFlowable: Flowable<Long>
        get() = evmKit.lastBlockHeightFlowable

    val transactionsSyncState: SyncState
        get() = evmKit.transactionsSyncState

    val transactionsSyncStateFlowable: Flowable<SyncState>
        get() = evmKit.transactionsSyncStateFlowable

    val receiveAddress: Address
        get() = evmKit.receiveAddress

    val chain: Chain
        get() = evmKit.chain

    val syncState: SyncState
        get() = evmKit.syncState

    val syncStateFlowable: Flowable<SyncState>
        get() = evmKit.syncStateFlowable

    val historicalSyncState: StateFlow<HistoricalSyncState>
        get() = evmKit.historicalSyncState

    val forwardSyncState: StateFlow<EthereumKit.ForwardSyncState>
        get() = evmKit.forwardSyncState

    val accountState: AccountState?
        get() = evmKit.accountState

    val accountStateFlowable: Flowable<AccountState>
        get() = evmKit.accountStateFlowable

    fun debugInfo(): String = evmKit.debugInfo()

    fun statusInfo(): Map<String, Any> = evmKit.statusInfo()

    suspend fun setup(
        account: Account,
        blockchainType: BlockchainType
    ): EvmKitWrapper {
        _blockchainType = blockchainType
        return evmBlockchainManager.getEvmKitManager(blockchainType)
            .getEvmKitWrapper(account, blockchainType)
            .also { kitWrapper = it }
    }

    suspend fun release() {
        val wrapper = kitWrapper ?: return
        evmBlockchainManager.getEvmKitManager(wrapper.blockchainType).unlink(wrapper)
    }

    suspend fun buildErc20Kit(contractAddress: String): Erc20Kit = Erc20Kit.getInstance(
        platformContexts.ethereum(evmKit.walletId),
        evmKit,
        Address(contractAddress),
        evmKitWrapper.databaseKey
    )

    fun getBlockchainType(): BlockchainType = requireNotNull(_blockchainType)

    suspend fun getTagTokenContractAddresses(): List<String> = evmKit.getTagTokenContractAddresses()

    suspend fun signedRawTransaction(
        transactionData: TransactionData,
        gasPrice: GasPrice,
        gasLimit: Long,
        nonce: Long?,
    ): SignedRawTransaction =
        evmKitWrapper.signedRawTransaction(transactionData, gasPrice, gasLimit, nonce)

    suspend fun broadcastRawTransaction(rawTransactionHex: String): RawTransactionBroadcastResult =
        evmKitWrapper.broadcastRawTransaction(rawTransactionHex)

    suspend fun getFullTransactionsAsync(
        tags: List<List<String>>,
        fromHash: ByteArray? = null,
        limit: Int? = null
    ): List<FullTransaction> = evmKit.getFullTransactionsAsync(tags, fromHash, limit).await()

    fun getFullTransactionsFlowable(tags: List<List<String>>): Flow<List<FullTransaction>> =
        evmKit.getFullTransactionsFlowable(tags).asFlow()

    /** Fee in wei reserved by a full-balance native transfer. */
    suspend fun estimateNativeTransferFee(): BigInteger =
        estimateNativeTransferFee(evmKit, getBlockchainType())
}
