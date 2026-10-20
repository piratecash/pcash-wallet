package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.providers.EvmExplorerApiKeys
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.EvmSyncSource
import cash.p.terminal.entities.EvmSyncSourceRecord
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.ethereumkit.models.TransactionSource
import io.horizontalsystems.core.entities.BlockchainType
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URI

const val ROBINHOOD_RPC_URL = "https://rpc.mainnet.chain.robinhood.com"

class EvmSyncSourceManager(
    private val blockchainSettingsStorage: BlockchainSettingsStorage,
    private val evmSyncSourceStorage: EvmSyncSourceStorage,
    private val addressResolutionConfig: AddressResolutionConfig,
) {

    private val syncSourceSubject = PublishSubject.create<BlockchainType>()

    private val _syncSourcesUpdatedFlow =
        MutableSharedFlow<BlockchainType>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val syncSourcesUpdatedFlow = _syncSourcesUpdatedFlow.asSharedFlow()

    private fun defaultTransactionSource(blockchainType: BlockchainType): TransactionSource {
        return when (blockchainType) {
            BlockchainType.Ethereum -> TransactionSource.ethereum(EvmExplorerApiKeys.etherscan)
            BlockchainType.BinanceSmartChain -> TransactionSource.binance(EvmExplorerApiKeys.bscscan)
            BlockchainType.Avalanche -> TransactionSource.avalanche(EvmExplorerApiKeys.bscscan)
            BlockchainType.Optimism -> TransactionSource.optimism(EvmExplorerApiKeys.bscscan)
            BlockchainType.Base -> TransactionSource.base(EvmExplorerApiKeys.bscscan)
            BlockchainType.Polygon-> TransactionSource.polygon(EvmExplorerApiKeys.etherscan)
            BlockchainType.ArbitrumOne -> TransactionSource.arbitrumOne(EvmExplorerApiKeys.etherscan)
            BlockchainType.Gnosis -> TransactionSource.gnosis(EvmExplorerApiKeys.etherscan)
            BlockchainType.Fantom -> TransactionSource.fantom(EvmExplorerApiKeys.etherscan)
            BlockchainType.ZkSync -> TransactionSource.zkSync(EvmExplorerApiKeys.blockscout)
            BlockchainType.RobinhoodChain -> TransactionSource.robinhood(
                EvmExplorerApiKeys.blockscout,
                EvmExplorerApiKeys.etherscan
            )
            else -> throw IllegalArgumentException("Non-supported EVM blockchain")
        }
    }

    val syncSourceObservable: Observable<BlockchainType>
        get() = syncSourceSubject

    fun defaultSyncSources(blockchainType: BlockchainType): List<EvmSyncSource> =
        defaultRpcSources(blockchainType).map { (name, rpcSource) ->
            evmSyncSource(blockchainType, name, rpcSource, defaultTransactionSource(blockchainType))
        }

    private fun defaultRpcSources(blockchainType: BlockchainType): List<Pair<String, RpcSource>> =
        when (blockchainType) {
            BlockchainType.Ethereum -> listOf(
                "BlocksDecoded" to httpRpc(addressResolutionConfig.blocksDecodedEthereumRpc),
                "PublicNode" to httpRpc("https://ethereum-rpc.publicnode.com"),
            )

            BlockchainType.BinanceSmartChain -> listOf(
                "Binance" to RpcSource.binanceSmartChainHttp(),
                "BlockRazor" to httpRpc("https://unstoppable.bsc.blockrazor.xyz"),
                "48club" to httpRpc("https://unstoppable.rpc.48.club"),
                "PublicNode" to httpRpc("https://bsc-rpc.publicnode.com"),
            )

            BlockchainType.Polygon -> listOf(
                "Polygon RPC" to RpcSource.polygonRpcHttp(),
                "PublicNode" to httpRpc("https://polygon-bor-rpc.publicnode.com"),
            )

            BlockchainType.Avalanche -> listOf(
                "Avax Network" to RpcSource.avaxNetworkHttp(),
                "PublicNode" to httpRpc("https://avalanche-evm.publicnode.com"),
            )

            BlockchainType.Optimism -> listOf(
                "Optimism" to RpcSource.optimismRpcHttp(),
                "PublicNode" to httpRpc("https://optimism-rpc.publicnode.com"),
            )

            BlockchainType.Base -> listOf(
                "Base" to RpcSource.baseRpcHttp(),
                "dRPC" to httpRpc("https://base.drpc.org"),
                "PublicNode" to httpRpc("https://base-rpc.publicnode.com"),
            )

            BlockchainType.ZkSync -> listOf("ZKsync" to RpcSource.zkSyncRpcHttp())

            BlockchainType.RobinhoodChain -> listOf("Robinhood Chain" to httpRpc(ROBINHOOD_RPC_URL))

            BlockchainType.ArbitrumOne -> listOf(
                "Arbitrum" to RpcSource.arbitrumOneRpcHttp(),
                "PublicNode" to httpRpc("https://arbitrum-one-rpc.publicnode.com"),
            )

            BlockchainType.Gnosis -> listOf(
                "Gnosis Chain" to RpcSource.gnosisRpcHttp(),
                "dRPC" to httpRpc("https://gnosis.drpc.org"),
            )

            BlockchainType.Fantom -> listOf(
                "Fantom Chain" to RpcSource.fantomRpcHttp(),
                "Fantom Chain (Mirror)" to httpRpc("https://rpcapi.fantom.network/"),
                "dRPC" to httpRpc("https://fantom.drpc.org"),
            )

            else -> listOf()
        }

    private fun httpRpc(url: String) = RpcSource.Http(listOf(URI(url)), null)

    fun customSyncSources(blockchainType: BlockchainType): List<EvmSyncSource> {
        val records = evmSyncSourceStorage.evmSyncSources(blockchainType)
        return try {
            records.mapNotNull { record ->
                val uri = lenientUriParts(record.url)
                val rpcSource = when (uri.scheme) {
                    "http",
                    "https" -> RpcSource.Http(listOf(URI(record.url)), record.auth)

                    "ws",
                    "wss" -> RpcSource.WebSocket(URI(record.url), record.auth)

                    else -> return@mapNotNull null
                }
                EvmSyncSource(
                    id = blockchainType.uid + "|" + record.url,
                    name = uri.host ?: "",
                    rpcSource = rpcSource,
                    transactionSource = defaultTransactionSource(blockchainType)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun evmSyncSource(
        blockchainType: BlockchainType,
        name: String,
        rpcSource: RpcSource,
        transactionSource: TransactionSource
    ) =
        EvmSyncSource(
            id = "${blockchainType.uid}|${name}|${transactionSource.name}|${
                rpcSource.uris.joinToString(separator = ",") { it.toString() }
            }",
            name = name,
            rpcSource = rpcSource,
            transactionSource = transactionSource
        )

    fun allSyncSources(blockchainType: BlockchainType): List<EvmSyncSource> =
        defaultSyncSources(blockchainType) + customSyncSources(blockchainType)

    fun getSyncSource(blockchainType: BlockchainType): EvmSyncSource {
        val syncSources = allSyncSources(blockchainType)

        val syncSourceUrl = blockchainSettingsStorage.evmSyncSourceUrl(blockchainType)
        val syncSource = syncSources.firstOrNull { it.uri.toString() == syncSourceUrl }

        return syncSource ?: syncSources[0]
    }

    fun getHttpSyncSource(blockchainType: BlockchainType): EvmSyncSource? {
        val syncSources = allSyncSources(blockchainType)
        blockchainSettingsStorage.evmSyncSourceUrl(blockchainType)?.let { url ->
            syncSources.firstOrNull { it.uri.toString() == url && it.isHttp }?.let { syncSource ->
                return syncSource
            }
        }

        return syncSources.firstOrNull { it.isHttp }
    }

    fun save(syncSource: EvmSyncSource, blockchainType: BlockchainType) {
        blockchainSettingsStorage.save(syncSource.uri.toString(), blockchainType)
        syncSourceSubject.onNext(blockchainType)
    }

    fun saveSyncSource(blockchainType: BlockchainType, url: String, auth: String?) {
        val record = EvmSyncSourceRecord(
            blockchainTypeUid = blockchainType.uid,
            url = url,
            auth = auth
        )

        evmSyncSourceStorage.save(record)

        customSyncSources(blockchainType).firstOrNull { it.uri.toString() == url }?.let {
            save(it, blockchainType)
        }

        _syncSourcesUpdatedFlow.tryEmit(blockchainType)
    }

    fun delete(syncSource: EvmSyncSource, blockchainType: BlockchainType) {
        val isCurrent = getSyncSource(blockchainType) == syncSource

        evmSyncSourceStorage.delete(blockchainType.uid, syncSource.uri.toString())

        if (isCurrent) {
            syncSourceSubject.onNext(blockchainType)
        }

        _syncSourcesUpdatedFlow.tryEmit(blockchainType)
    }

}

val RpcSource.uris: List<URI>
    get() = when (this) {
        is RpcSource.WebSocket -> listOf(uri)
        is RpcSource.Http -> uris
    }
