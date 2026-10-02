package cash.p.terminal.wallet.syncers

import cash.p.terminal.network.pirate.domain.model.CoinsList
import cash.p.terminal.network.pirate.domain.model.RemoteBlockchain
import cash.p.terminal.network.pirate.domain.model.RemoteCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.storage.BlockchainRecord
import cash.p.terminal.wallet.storage.CoinRecord
import cash.p.terminal.wallet.storage.CoinsData
import cash.p.terminal.wallet.storage.TokenRecord
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Single source of truth for turning the V2 coins-list response into storage records.
 * Used both by the live [CoinSyncer] and by the initial-coins-list generator, so the
 * mapping pipeline (flatten -> transform -> filterValidTokens -> injectVirtualTokens)
 * exists in exactly one place.
 */
class CoinsListMapper {

    internal fun map(
        list: CoinsList,
        ranks: Map<String, Int>,
        virtualCoinMapper: VirtualCoinMapper
    ): CoinsData {
        val coins = list.coins.map { coinRecord(it, ranks[it.coingeckoId]) }
        val blockchains = list.blockchains.map(::blockchainRecord)
        val pipelineTokens = tokenPipeline(list.coins, blockchains, virtualCoinMapper)
        // The API occasionally returns duplicate rows for the same token primary key;
        // the database keeps the last one via INSERT OR REPLACE. Apply the same rule to
        // the final list — after order-sensitive transform has seen the raw rows — so
        // the result is exactly what the app's database would hold.
        val dedupedTokens = pipelineTokens.associateBy(::primaryKey).values.toList()

        return CoinsData(coins, blockchains, dedupedTokens)
    }

    /**
     * Token pipeline before the final keep-last dedup. Exposed separately so the dump
     * generator can detect primary-key collisions introduced by [transform] or
     * [injectVirtualTokens], as opposed to collisions already present upstream.
     */
    internal fun tokenPipeline(
        remoteCoins: List<RemoteCoin>,
        blockchains: List<BlockchainRecord>,
        virtualCoinMapper: VirtualCoinMapper
    ): List<TokenRecord> {
        val tokens = flattenTokens(remoteCoins)
        val transformedTokens = transform(tokens)
        val validTokens = filterValidTokens(transformedTokens, blockchains)
        return injectVirtualTokens(remoteCoins.map { it.coingeckoId }.toSet(), validTokens, virtualCoinMapper)
    }

    internal fun primaryKey(token: TokenRecord): List<String> =
        listOf(token.coinUid, token.blockchainUid, token.type, token.reference)

    internal fun coinRecord(remoteCoin: RemoteCoin, rank: Int?): CoinRecord =
        CoinRecord(
            uid = remoteCoin.coingeckoId,
            name = remoteCoin.name,
            code = remoteCoin.code.uppercase(),
            marketCapRank = rank,
            priority = remoteCoin.priority
        )

    internal fun blockchainRecord(remoteBlockchain: RemoteBlockchain): BlockchainRecord =
        BlockchainRecord(uid = remoteBlockchain.uid, name = remoteBlockchain.name, eip3091url = remoteBlockchain.url)

    internal fun flattenTokens(remoteCoins: List<RemoteCoin>): List<TokenRecord> =
        remoteCoins.flatMap { coin ->
            coin.tokens.map { token ->
                TokenRecord(
                    coinUid = coin.coingeckoId,
                    blockchainUid = token.blockchainUid,
                    type = token.type,
                    decimals = token.decimals,
                    reference = token.address ?: ""
                )
            }
        }

    internal fun injectVirtualTokens(
        coinUids: Set<String>,
        tokens: List<TokenRecord>,
        virtualCoinMapper: VirtualCoinMapper
    ): List<TokenRecord> {
        val tokensIndex = tokens.associateBy { it.coinUid to it.blockchainUid }

        val virtualTokens = virtualCoinMapper.allMappings.mapNotNull { mapping ->
            if (mapping.virtualCoinUid !in coinUids) return@mapNotNull null
            val realToken = tokensIndex[mapping.realCoinUid to mapping.blockchainType.uid]
                ?: return@mapNotNull null

            realToken.copy(coinUid = mapping.virtualCoinUid)
        }

        return tokens + virtualTokens
    }

    internal fun transform(tokens: List<TokenRecord>): List<TokenRecord> {
        val derivationReferences = TokenType.Derivation.values().map { it.name }
        val addressTypes = TokenType.AddressType.values().map { it.name }
        val addressSpecTypes = TokenType.AddressSpecType.values().map { it.name }

        var result = tokens
        result = transform(
            tokens = result,
            blockchainUid = BlockchainType.Bitcoin.uid,
            transformedType = "derived",
            references = derivationReferences
        )
        result = transform(
            tokens = result,
            blockchainUid = BlockchainType.Zcash.uid,
            transformedType = "address_spec_type",
            references = addressSpecTypes
        )
        result = transform(
            tokens = result,
            blockchainUid = BlockchainType.Litecoin.uid,
            transformedType = "derived",
            references = derivationReferences
        )
        result = transform(
            tokens = result,
            blockchainUid = BlockchainType.BitcoinCash.uid,
            transformedType = "address_type",
            references = addressTypes
        )

        return result
    }

    private fun transform(
        tokens: List<TokenRecord>,
        blockchainUid: String,
        transformedType: String,
        references: List<String>
    ): List<TokenRecord> {
        val tokensMutable = tokens.toMutableList()
        val indexOfFirst = tokensMutable.indexOfFirst {
            it.blockchainUid == blockchainUid
        }
        if (indexOfFirst != -1) {
            val token = tokensMutable.removeAt(indexOfFirst)
            val entities = references.map {
                token.copy(type = transformedType, reference = it)
            }
            tokensMutable.addAll(entities)
        }
        return tokensMutable
    }

    internal fun filterValidTokens(
        tokens: List<TokenRecord>,
        blockchains: List<BlockchainRecord>
    ): List<TokenRecord> {
        val blockchainUids = blockchains.map { it.uid }.toSet()
        return tokens.filter { it.blockchainUid in blockchainUids }
    }
}
