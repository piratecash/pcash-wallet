package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.isEvm
import cash.p.terminal.modules.multiswap.providers.ExpiringCache
import cash.p.terminal.modules.multiswap.providers.SwapAssetKind
import cash.p.terminal.modules.multiswap.providers.contractAddress
import cash.p.terminal.modules.multiswap.providers.swapAssetKind
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCurrency
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.withContext

/** Resolves to the single catalog row on our network: natives by coinId, contract tokens by contract only. */
class BackendSwapAssetResolver(
    private val backendSwapRepository: BackendSwapRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val currenciesCache = ExpiringCache<String, List<BackendSwapCurrency>>(CACHE_TTL_MS)

    suspend fun resolve(providerName: String, token: Token): BackendSwapAsset? {
        val kind = token.backendSwapAssetKind ?: return null
        val network = token.blockchainType.backendSwapNetwork ?: return null
        val row = when (kind) {
            SwapAssetKind.NATIVE -> {
                val coinId = token.coin.coinGeckoId ?: return null
                currencies(providerName)
                    .singleOrNull { it.blockchain == network && it.coinId == coinId }
                    ?.takeIf { it.contractAddress.isNullOrBlank() }
            }
            SwapAssetKind.CONTRACT -> currencies(providerName)
                .singleOrNull { it.blockchain == network && it.hasContract(token) }
        } ?: return null
        return BackendSwapAsset(coinId = row.coinId, blockchain = row.blockchain)
    }

    private fun BackendSwapCurrency.hasContract(token: Token): Boolean {
        val contract = contractAddress?.takeIf { it.isNotBlank() } ?: return false
        return contract.equals(token.backendContractAddress, ignoreCase = token.hasCaseInsensitiveContract)
    }

    private val Token.backendSwapAssetKind: SwapAssetKind?
        get() = if (type is TokenType.Asset) SwapAssetKind.CONTRACT else swapAssetKind

    private val Token.backendContractAddress: String
        get() = when (val tokenType = type) {
            is TokenType.Asset -> "${tokenType.code}-${tokenType.issuer}"
            else -> contractAddress()
        }

    // Changelly lowercases Stellar CODE-ISSUER; an issuer is uppercase-only, so only one issuer's codes could collide.
    private val Token.hasCaseInsensitiveContract: Boolean
        get() = blockchainType.isEvm || blockchainType == BlockchainType.Stellar

    private suspend fun currencies(providerName: String): List<BackendSwapCurrency> =
        currenciesCache.getOrLoad(providerName) {
            withContext(dispatcherProvider.io) { backendSwapRepository.getCurrencies(providerName) }
        }

    private companion object {
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}
