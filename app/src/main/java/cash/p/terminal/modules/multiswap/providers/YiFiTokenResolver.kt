package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.isEvm
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.network.yifi.data.repository.YiFiRepository
import cash.p.terminal.network.yifi.domain.entity.YiFiChain
import cash.p.terminal.network.yifi.domain.entity.YiFiToken
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class YiFiAsset(
    val ticker: String,
    val network: String,
)

/**
 * `/v1/swap` accepts only ticker + network, so a token resolves only when that pair is unambiguous
 * in the YiFi catalog. Failures are never cached, so an outage does not hide YiFi for the TTL.
 */
class YiFiTokenResolver(
    private val yiFiRepository: YiFiRepository,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val marketKit: MarketKitWrapper,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val chainsCache = ExpiringCache<Unit, List<YiFiChain>>(CACHE_TTL_MS)
    private val networkCache = ExpiringCache<BlockchainType, YiFiChain?>(CACHE_TTL_MS)
    private val searchCache = ExpiringCache<Pair<String, String>, List<YiFiToken>>(CACHE_TTL_MS)
    private val assetCache = ExpiringCache<String, YiFiAsset?>(CACHE_TTL_MS)

    suspend fun clear() {
        chainsCache.clear()
        networkCache.clear()
        searchCache.clear()
        assetCache.clear()
    }

    suspend fun resolveAsset(token: Token): YiFiAsset? {
        val kind = token.swapAssetKind ?: return null
        return withContext(dispatcherProvider.io) {
            assetCache.getOrLoad("${token.coin.uid}|${token.tokenQuery.id}") {
                when (kind) {
                    SwapAssetKind.NATIVE -> resolveNative(token)
                    SwapAssetKind.CONTRACT -> resolveContract(token.blockchainType, token.contractAddress())
                }
            }
        }
    }

    private suspend fun resolveNative(token: Token): YiFiAsset? {
        val chain = resolveNetwork(token.blockchainType, token.coin.code) ?: return null
        val ticker = chain.nativeToken.ifBlank { token.coin.code }
        val row = exactTickerRows(chain.id, ticker).singleOrNull() ?: return null
        return row.takeIf { it.isContractless }?.let { YiFiAsset(it.ticker, chain.id) }
    }

    private suspend fun resolveContract(blockchainType: BlockchainType, contract: String): YiFiAsset? {
        val nativeCode = marketKit.nativeToken(blockchainType)?.coin?.code
        val chain = resolveNetwork(blockchainType, nativeCode) ?: return null
        val row = searchTokens(chain.id, contract)
            .singleOrNull { it.contractAddress.equals(contract, ignoreCase = true) }
            ?: return null
        exactTickerRows(chain.id, row.ticker).singleOrNull() ?: return null
        return YiFiAsset(row.ticker, chain.id)
    }

    private suspend fun resolveNetwork(blockchainType: BlockchainType, nativeCode: String?): YiFiChain? =
        networkCache.getOrLoad(blockchainType) {
            if (blockchainType.isEvm) {
                val chainId = evmBlockchainManager.getChain(blockchainType).id.toLong()
                getChains().singleOrNull { it.chainId == chainId }
            } else {
                nativeCode?.let { findNonEvmNetwork(it) }
            }
        }

    // A ticker appears in the aliases of several chains (BTC on MERLIN, MEZO), but only its home
    // chain lists it as a contractless token.
    private suspend fun findNonEvmNetwork(nativeCode: String): YiFiChain? = coroutineScope {
        getChains()
            .filter { it.mentions(nativeCode) }
            .map { chain ->
                async { chain.takeIf { exactTickerRows(chain.id, nativeCode).any { it.isContractless } } }
            }
            .awaitAll()
            .filterNotNull()
            .singleOrNull()
    }

    private suspend fun exactTickerRows(network: String, ticker: String): List<YiFiToken> =
        searchTokens(network, ticker).filter { it.ticker.equals(ticker, ignoreCase = true) }

    private suspend fun getChains(): List<YiFiChain> =
        chainsCache.getOrLoad(Unit) { yiFiRepository.getChains() }

    private suspend fun searchTokens(network: String, query: String): List<YiFiToken> =
        searchCache.getOrLoad(network to query) { yiFiRepository.searchTokens(network, query) }

    private val YiFiToken.isContractless: Boolean
        get() = contractAddress.isNullOrBlank()

    private fun YiFiChain.mentions(code: String): Boolean =
        id.equals(code, ignoreCase = true) ||
            nativeToken.equals(code, ignoreCase = true) ||
            aliases.any { it.equals(code, ignoreCase = true) }

    private companion object {
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}
