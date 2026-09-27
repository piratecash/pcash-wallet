package cash.p.terminal.wallet.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.pirate.domain.model.CoinsList
import cash.p.terminal.network.pirate.domain.repository.CoinsListRepository
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.DumpManager
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.storage.CoinsData
import cash.p.terminal.wallet.storage.TokenRecord
import cash.p.terminal.wallet.storage.countValueRows
import cash.p.terminal.wallet.storage.initialCoinsFile
import cash.p.terminal.wallet.storage.validateDumpSql
import cash.p.terminal.wallet.syncers.CoinsListMapper
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MIN_RECORD_COUNT_RATIO = 0.9

/**
 * Old-uid token that must still resolve to its renamed coin after the V1 -> V2 migration
 * (see Migration_116_117): the eip20 identity is unambiguous even though the display uid changed.
 */
private data class MigratedToken(
    val newCoinUid: String,
    val blockchainUid: String,
    val contract: String,
)

private val migratedTokens = listOf(
    MigratedToken("apecoin", "ethereum", "0x4d224452801aced8b2f0aebe155379bb5d594381"),
    MigratedToken("weth", "binance-smart-chain", "0x2170ed0880ac9a755fd29b2688956bd959f933f8"),
    MigratedToken("usd-coin-avalanche-bridged-usdc-e", "arbitrum-one", "0xff970a61a04b1ca14834a43f5de4533ebddb5cc8"),
    MigratedToken(
        "usd-coin-avalanche-bridged-usdc-e", "optimistic-ethereum", "0x7f5c764cbc14f9669b88837ca1490cca17c31607"
    ),
    MigratedToken("usd-coin-avalanche-bridged-usdc-e", "polygon-pos", "0x2791bca1f2de4661ed88a30c99a7a9449aa84174"),
    MigratedToken("usd-coin-avalanche-bridged-usdc-e", "avalanche", "0xa7d7079b0fead91f3e65f86e8915cb59c1a4c664"),
    MigratedToken("dash", "binance-smart-chain", "0xcbfb0d98151d03ef8bb71fa668f57df5e3fb4673"),
)

/**
 * Regenerates the checked-in initial coins list dump from the live V2 coins API.
 * Skipped by default (assumeTrue guard) so normal test runs never touch the network;
 * run explicitly via tools/update-coins-list.sh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InitialCoinsListGenerator {

    @Test
    fun updateInitialCoinsList() {
        assumeTrue(System.getProperty("updateCoinsList") == "true")

        val repository = fetchRepository()
        val mapper = CoinsListMapper()
        val virtualCoinMapper = VirtualCoinMapper()

        val list = runBlocking { repository.coinsList() }
        val ranks = runBlocking { repository.coinRanks() }
        val mapped = mapper.map(list, ranks, virtualCoinMapper)
        val dump = DumpManager.getInitialDump(mapped.blockchains, mapped.coins, mapped.tokens)

        assertFetchedDataIsSane(mapped)
        assertNoPipelineIntroducedPkCollisions(mapper, list, virtualCoinMapper)
        validateDumpSql(dump)

        initialCoinsFile().writeText(dump)
    }

    /**
     * Koin definitions are lazy, so pulling only [CoinsListRepository] out of [networkModule]
     * never touches its unrelated swap-provider/database dependencies.
     */
    private fun fetchRepository(): CoinsListRepository {
        val koin = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(networkModule)
        }.koin
        return koin.get()
    }

    private fun assertFetchedDataIsSane(mapped: CoinsData) {
        assertTrue("Fetched coins list is empty", mapped.coins.isNotEmpty())
        assertTrue("Fetched blockchains list is empty", mapped.blockchains.isNotEmpty())
        assertTrue("Fetched tokens list is empty", mapped.tokens.isNotEmpty())

        assertRecordCountsNotRegressed(mapped)
        assertUniqueKeys("coins.uid", mapped.coins.map { it.uid })
        assertUniqueKeys("blockchains.uid", mapped.blockchains.map { it.uid })

        val coinUids = mapped.coins.map { it.uid }.toSet()
        assertTrue(
            "Token references a coin missing from the coins list",
            mapped.tokens.all { it.coinUid in coinUids }
        )
        assertAnchorsPresent(mapped, coinUids)
        assertMigratedTokensResolveToNewCoin(mapped.tokens)
    }

    /**
     * Each of the 7 tokens Migration_116_117 re-keys by old uid must exist in the catalog
     * under its new coingecko_id coin, otherwise the migrated swap-record label would point
     * at a coin without that token.
     */
    private fun assertMigratedTokensResolveToNewCoin(tokens: List<TokenRecord>) {
        migratedTokens.forEach { migrated ->
            assertTokenAnchor(tokens, "migrated token '${migrated.newCoinUid}' on '${migrated.blockchainUid}'") {
                it.coinUid == migrated.newCoinUid &&
                        it.blockchainUid == migrated.blockchainUid &&
                        it.type == "eip20" &&
                        it.reference.equals(migrated.contract, ignoreCase = true)
            }
        }
    }

    private fun assertRecordCountsNotRegressed(mapped: CoinsData) {
        val currentDump = initialCoinsFile().readText()
        assertCountNotRegressed("Blockchain", mapped.blockchains.size, currentDump)
        assertCountNotRegressed("Coin", mapped.coins.size, currentDump)
        assertCountNotRegressed("Token", mapped.tokens.size, currentDump)
    }

    private fun assertCountNotRegressed(table: String, fetchedCount: Int, currentDump: String) {
        val currentCount = countValueRows(currentDump, table)
        assertTrue(
            "$table record count regressed too much: fetched $fetchedCount, current asset has $currentCount",
            fetchedCount >= currentCount * MIN_RECORD_COUNT_RATIO
        )
    }

    private fun assertUniqueKeys(label: String, keys: List<Any>) {
        assertTrue("Duplicate primary key detected for $label", keys.size == keys.toSet().size)
    }

    /**
     * The final keep-last dedup in map() mirrors the app database and silently collapses
     * duplicate PKs, so upstream dirt must not abort generation. Collisions that only
     * appear AFTER transform/injectVirtualTokens, however, signal a pipeline or
     * upstream-schema problem and must stop the run before the asset is written.
     */
    private fun assertNoPipelineIntroducedPkCollisions(
        mapper: CoinsListMapper,
        list: CoinsList,
        virtualCoinMapper: VirtualCoinMapper
    ) {
        val rawCounts = primaryKeyCounts(mapper.flattenTokens(list.coins), mapper)
        val blockchains = list.blockchains.map { mapper.blockchainRecord(it) }
        val pipelineCounts =
            primaryKeyCounts(mapper.tokenPipeline(list.coins, blockchains, virtualCoinMapper), mapper)
        // Per-key multiplicities, not collision-key sets: a key already duplicated upstream
        // must still trip the guard when the pipeline adds yet another row for it.
        val introduced = pipelineCounts.filter { (pk, count) ->
            count > 1 && count > (rawCounts[pk] ?: 0)
        }.keys
        assertTrue(
            "Token PK collisions introduced by transform/injectVirtualTokens: $introduced",
            introduced.isEmpty()
        )
    }

    private fun primaryKeyCounts(tokens: List<TokenRecord>, mapper: CoinsListMapper): Map<List<String>, Int> =
        tokens.groupingBy(mapper::primaryKey).eachCount()

    private fun assertAnchorsPresent(mapped: CoinsData, coinUids: Set<String>) {
        listOf("piratecash", "cosanta", "tether", "bitcoin").forEach { uid ->
            assertTrue("Expected anchor coin '$uid' is missing", uid in coinUids)
            assertTokenAnchor(mapped.tokens, "any '$uid'") { it.coinUid == uid }
        }
        assertNativeChainAnchors(mapped.tokens)
        assertStartupAnchors(mapped.tokens)
        assertDefaultWalletAnchors(mapped.tokens)
    }

    /**
     * Derived anchors must carry real decimals: CoinDao maps a null-decimal row to
     * TokenType.Unsupported, which would make the base chain unusable from the asset.
     */
    private fun assertNativeChainAnchors(tokens: List<TokenRecord>) {
        assertTokenAnchor(tokens, "mweb litecoin") {
            it.coinUid == "litecoin" && it.blockchainUid == "litecoin" &&
                    it.type == "mweb" && it.decimals == 8 && it.reference == ""
        }
        listOf("bitcoin", "litecoin").forEach { chain ->
            listOf("Bip44", "Bip49", "Bip84", "Bip86").forEach { reference ->
                assertTokenAnchor(tokens, "$chain derived '$reference'") {
                    it.coinUid == chain && it.blockchainUid == chain &&
                            it.type == "derived" && it.decimals == 8 && it.reference == reference
                }
            }
        }
    }

    /**
     * Exact tokens App.needForceUpdateCoins() checks at startup; shipping an asset
     * without them would force a full network resync on every first launch.
     */
    private fun assertStartupAnchors(tokens: List<TokenRecord>) {
        assertTokenAnchor(tokens, "zcash Shielded") {
            it.coinUid == "zcash" && it.blockchainUid == "zcash" &&
                    it.type == "address_spec_type" && it.decimals == 8 && it.reference == "Shielded"
        }
        assertTokenAnchor(tokens, "tether on binance-smart-chain") {
            it.coinUid == "tether" && it.blockchainUid == "binance-smart-chain"
        }
    }

    /**
     * Every default wallet created on a fresh install must resolve from the asset with
     * the chain's fixed decimals: CoinDao maps null decimals to TokenType.Unsupported,
     * and a wrong value would scale balances and transfers incorrectly.
     */
    private fun assertDefaultWalletAnchors(tokens: List<TokenRecord>) {
        val expectedDecimals = mapOf(
            TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84)) to 8,
            TokenQuery(BlockchainType.Monero, TokenType.Native) to 12,
            TokenQuery.ZcashUnified to 8,
            TokenQuery(BlockchainType.Ethereum, TokenType.Native) to 18,
            TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native) to 18,
            TokenQuery.PirateCashBnb to 8,
            TokenQuery.CosantaBnb to 8
        )
        TokenQuery.defaultTokenQueries.forEach { query ->
            val decimals = requireNotNull(expectedDecimals[query]) {
                "No expected decimals for default token query '${query.id}' — update the map"
            }
            val values = query.tokenType.values
            assertTokenAnchor(tokens, "default wallet '${query.id}'") {
                it.blockchainUid == query.blockchainType.uid &&
                        it.type == values.type && it.reference == values.reference &&
                        it.decimals == decimals
            }
        }
    }

    private fun assertTokenAnchor(
        tokens: List<TokenRecord>,
        description: String,
        predicate: (TokenRecord) -> Boolean
    ) {
        assertTrue("Expected $description token is missing", tokens.any(predicate))
    }
}
