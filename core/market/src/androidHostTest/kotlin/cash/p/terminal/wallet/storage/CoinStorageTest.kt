package cash.p.terminal.wallet.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinStorageTest {

    private lateinit var database: MarketDatabase
    private lateinit var storage: CoinStorage

    private val blockchain = BlockchainRecord(
        uid = BlockchainType.Ethereum.uid,
        name = "Ethereum",
        eip3091url = "https://etherscan.io",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = CoinStorage(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceAll_thenFullCoin_mapsToDomainWithTokensAndBlockchain() {
        val coin = CoinRecord(uid = "bitcoin", name = "Bitcoin", code = "BTC", marketCapRank = 1, priority = 0)
        val token = TokenRecord(
            coinUid = coin.uid,
            blockchainUid = blockchain.uid,
            type = "eip20",
            decimals = 18,
            reference = "0xabc",
        )

        storage.replaceAll(CoinsData(coins = listOf(coin), blockchains = listOf(blockchain), tokens = listOf(token)))

        val fullCoin = checkNotNull(storage.fullCoin(coin.uid))
        assertEquals("Bitcoin", fullCoin.coin.name)
        assertEquals(1, fullCoin.tokens.size)
        assertEquals(BlockchainType.Ethereum, fullCoin.tokens.single().blockchainType)
        assertNull(fullCoin.coin.image)
    }

    @Test
    fun applyRanks_mixedKnownAndUnknownIds_setsKnownAndClearsAbsent() {
        val bitcoin = CoinRecord(uid = "bitcoin", name = "Bitcoin", code = "BTC", marketCapRank = 5)
        val litecoin = CoinRecord(uid = "litecoin", name = "Litecoin", code = "LTC", marketCapRank = 7)
        storage.replaceAll(
            CoinsData(coins = listOf(bitcoin, litecoin), blockchains = emptyList(), tokens = emptyList())
        )

        // "unknown-coin" has no matching row: the UPDATE affects 0 rows, which is expected.
        storage.applyRanks(mapOf("bitcoin" to 1, "unknown-coin" to 2))

        assertEquals(1, storage.coin("bitcoin")?.marketCapRank)
        assertNull(storage.coin("litecoin")?.marketCapRank)
    }

    @Test
    fun ranks_mixedRankedAndUnranked_returnsOnlyRanked() {
        val bitcoin = CoinRecord(uid = "bitcoin", name = "Bitcoin", code = "BTC", marketCapRank = 5)
        val litecoin = CoinRecord(uid = "litecoin", name = "Litecoin", code = "LTC", marketCapRank = null)
        storage.replaceAll(
            CoinsData(coins = listOf(bitcoin, litecoin), blockchains = emptyList(), tokens = emptyList())
        )

        assertEquals(mapOf("bitcoin" to 5), storage.ranks())
    }

    @Test
    fun getToken_sameContractOnTwoCoins_prefersBetterRank() {
        val reference = "0xabc"
        val betterRankedCoin = CoinRecord(uid = "canonical-coin", name = "Canonical", code = "CAN", marketCapRank = 1)
        val worseRankedCoin = CoinRecord(uid = "duplicate-coin", name = "Duplicate", code = "DUP", marketCapRank = 100)
        val tokenOnBetterCoin = TokenRecord(
            coinUid = betterRankedCoin.uid,
            blockchainUid = blockchain.uid,
            type = "eip20",
            decimals = 18,
            reference = reference,
        )
        val tokenOnWorseCoin = tokenOnBetterCoin.copy(coinUid = worseRankedCoin.uid)

        storage.replaceAll(
            CoinsData(
                coins = listOf(worseRankedCoin, betterRankedCoin),
                blockchains = listOf(blockchain),
                tokens = listOf(tokenOnWorseCoin, tokenOnBetterCoin),
            )
        )

        val token = storage.getToken(
            TokenQuery(blockchainType = BlockchainType.Ethereum, tokenType = TokenType.Eip20(reference))
        )

        assertEquals(betterRankedCoin.uid, checkNotNull(token).coin.uid)
    }
}
