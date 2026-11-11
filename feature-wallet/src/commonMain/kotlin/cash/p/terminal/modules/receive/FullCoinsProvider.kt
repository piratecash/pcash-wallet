package cash.p.terminal.modules.receive

import cash.p.terminal.core.isCustom
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.core.nativeTokenQueries
import cash.p.terminal.core.supported
import cash.p.terminal.core.supports
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.ethereumkit.core.AddressValidator
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

data class FullCoinsPage(val items: List<FullCoin>, val hasMore: Boolean)

class FullCoinsProvider(
    private val marketKit: MarketKitWrapper,
    val activeAccount: Account,
    private val dispatcherProvider: DispatcherProvider,
) {
    private var activeWallets = listOf<Wallet>()
    private var predefinedTokens = listOf<Token>()

    private var query: String? = null

    fun setActiveWallets(wallets: List<Wallet>) {
        activeWallets = wallets

        updatePredefinedTokens()
    }

    private fun updatePredefinedTokens() {
        val allowedBlockchainTypes =
            BlockchainType.supported.filter { it.supports(activeAccount.type) }
        val tokenQueries = allowedBlockchainTypes
            .map { it.nativeTokenQueries }
            .flatten()
        val supportedNativeTokens = marketKit.tokens(tokenQueries)
        val activeTokens = activeWallets.map { it.token }
        predefinedTokens = activeTokens + supportedNativeTokens
    }

    fun setQuery(q: String) {
        query = q
    }

    suspend fun getItems(): List<FullCoin> {
        val tmpQuery = query.orEmpty()

        val fullCoins = if (isContractAddress(tmpQuery)) {
            contractAddressFullCoins(tmpQuery)
        } else {
            customFullCoins(tmpQuery) + marketKit.fullCoins(tmpQuery, ALL_COINS_LIMIT)
        }
        return liftActivesKeepingSqlOrder(fullCoins, activeWallets)
    }

    suspend fun getPage(limit: Int): FullCoinsPage {
        val tmpQuery = query.orEmpty()

        return withContext(dispatcherProvider.io) {
            if (isContractAddress(tmpQuery)) {
                val items = liftActivesKeepingSqlOrder(contractAddressFullCoins(tmpQuery), activeWallets)
                return@withContext FullCoinsPage(items, hasMore = false)
            }
            val catalog = marketKit.fullCoins(tmpQuery, limit)
            // Active coins are read separately so those beyond the catalog window still stay on top.
            val lifted = liftActivesKeepingSqlOrder(catalog + activeFullCoins(tmpQuery), activeWallets)
            FullCoinsPage(
                items = (customFullCoins(tmpQuery) + lifted).distinctBy { it.coin.uid },
                hasMore = catalog.size == limit
            )
        }
    }

    private fun contractAddressFullCoins(query: String): List<FullCoin> {
        val customFullCoins = predefinedTokens
            .filter {
                val type = it.type
                it.isCustom && type is TokenType.Eip20 && type.address.contains(query, true)
            }
            .map { it.fullCoin }

        return customFullCoins + marketKit.tokens(query).map { it.fullCoin }
    }

    private fun customFullCoins(query: String): List<FullCoin> =
        predefinedTokens
            .filter { it.isCustom && it.coin.matches(query) }
            .map { it.fullCoin }

    private fun activeFullCoins(query: String): List<FullCoin> =
        marketKit.fullCoins(activeWallets.map { it.coin.uid }.distinct())
            .filter { it.coin.matches(query) }
            .sortedWith(
                compareBy<FullCoin, Int?>(nullsFirst()) { it.coin.priority }
                    .thenBy(nullsLast()) { it.coin.marketCapRank }
                    .thenBy { it.coin.name }
            )

    private fun Coin.matches(query: String) =
        query.isEmpty() || name.contains(query, true) || code.contains(query, true)

    fun liftActivesKeepingSqlOrder(
        fullCoins: List<FullCoin>,
        activeWallets: List<Wallet>
    ): List<FullCoin> {
        val activeUids = activeWallets.map { it.coin.uid }.toHashSet()

        val (actives, others) = fullCoins.partition { it.coin.uid in activeUids }

        return actives + others
    }

    private fun isContractAddress(filter: String) = try {
        AddressValidator.validate(filter)
        true
    } catch (e: AddressValidator.AddressValidationException) {
        false
    }

    private companion object {
        const val ALL_COINS_LIMIT = 100_000
    }
}
