package cash.p.terminal.modules.receive

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FullCoinsProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val account = mockk<Account>(relaxed = true) {
        every { type } returns AccountType.Mnemonic(listOf("word"), "")
    }
    private val provider = FullCoinsProvider(marketKit, account, dispatcherProvider)

    @Test
    fun getPage_activeCoinOutsideWindow_isListedFirst() = runTest {
        val btc = fullCoin("btc")
        val eth = fullCoin("eth")
        val bnb = fullCoin("bnb")
        activate(btc)
        coEvery { marketKit.fullCoins("", 200) } returns listOf(eth, bnb)

        val page = provider.getPage(200)

        assertEquals(listOf("btc", "eth", "bnb"), page.items.uids())
    }

    @Test
    fun getPage_activeCoinInsideWindow_appearsOnce() = runTest {
        val btc = fullCoin("btc")
        activate(btc)
        coEvery { marketKit.fullCoins("", 200) } returns listOf(fullCoin("eth"), btc, fullCoin("bnb"))

        val page = provider.getPage(200)

        assertEquals(listOf("btc", "eth", "bnb"), page.items.uids())
    }

    @Test
    fun getPage_textQuery_filtersActiveCoins() = runTest {
        activate(fullCoin("btc", name = "Bitcoin", code = "BTC"), fullCoin("etc", name = "Ethereum Classic", code = "ETC"))
        coEvery { marketKit.fullCoins("eth", 200) } returns listOf(fullCoin("eth", name = "Ethereum", code = "ETH"))
        provider.setQuery("eth")

        val page = provider.getPage(200)

        assertEquals(listOf("etc", "eth"), page.items.uids())
    }

    @Test
    fun getPage_textQueryActiveCoinsInsideWindow_keepCatalogRelevanceOrder() = runTest {
        val tether = fullCoin("tether", name = "Tether", code = "USDT", rank = 3)
        val usdCoin = fullCoin("usd-coin", name = "USD Coin", code = "USDC", rank = 6)
        activate(tether, usdCoin)
        coEvery { marketKit.fullCoins("usd", 200) } returns listOf(usdCoin, tether)
        provider.setQuery("usd")

        val page = provider.getPage(200)

        assertEquals(listOf("usd-coin", "tether"), page.items.uids())
    }

    @Test
    fun getPage_severalActiveCoins_orderedByPriorityThenRankThenName() = runTest {
        activate(
            fullCoin("prioritized", rank = 1, priority = 1),
            fullCoin("rank50", rank = 50),
            fullCoin("rank10", rank = 10),
            fullCoin("unrankedC", name = "C"),
            fullCoin("unrankedA", name = "A"),
        )
        coEvery { marketKit.fullCoins("", 200) } returns emptyList()

        val page = provider.getPage(200)

        assertEquals(
            listOf("rank10", "rank50", "unrankedA", "unrankedC", "prioritized"),
            page.items.uids()
        )
    }

    @Test
    fun getPage_catalogFillsWindow_hasMore() = runTest {
        coEvery { marketKit.fullCoins("", 2) } returns listOf(fullCoin("eth"), fullCoin("bnb"))

        assertTrue(provider.getPage(2).hasMore)
    }

    @Test
    fun getPage_catalogShorterThanWindow_hasNoMore() = runTest {
        coEvery { marketKit.fullCoins("", 2) } returns listOf(fullCoin("eth"))

        assertFalse(provider.getPage(2).hasMore)
    }

    @Test
    fun getPage_contractAddressQuery_returnsMatchingTokensWithoutMore() = runTest {
        val usdt = fullCoin("tether")
        coEvery { marketKit.tokens(CONTRACT_ADDRESS) } returns usdt.tokens
        provider.setQuery(CONTRACT_ADDRESS)

        val page = provider.getPage(200)

        assertEquals(FullCoinsPage(listOf(usdt), hasMore = false), page)
        coVerify(exactly = 0) { marketKit.fullCoins(any<String>(), any()) }
    }

    @Test
    fun getItems_activeCoin_isLiftedFromFullCatalog() = runTest {
        val btc = fullCoin("btc")
        activate(btc)
        coEvery { marketKit.fullCoins("", 100_000) } returns listOf(fullCoin("eth"), btc)

        val items = provider.getItems()

        assertEquals(listOf("btc", "eth"), items.uids())
    }

    private fun activate(vararg fullCoins: FullCoin) {
        val wallets = fullCoins.map { fullCoin ->
            mockk<Wallet> {
                every { coin } returns fullCoin.coin
                every { token } returns fullCoin.tokens.first()
            }
        }
        every { marketKit.fullCoins(any<List<String>>()) } answers {
            val uids = firstArg<List<String>>()
            fullCoins.filter { it.coin.uid in uids }.reversed()
        }
        provider.setActiveWallets(wallets)
    }

    private fun List<FullCoin>.uids() = map { it.coin.uid }

    private fun fullCoin(
        uid: String,
        name: String = uid,
        code: String = uid.uppercase(),
        rank: Int? = null,
        priority: Int? = null,
    ): FullCoin {
        val coin = Coin(uid = uid, name = name, code = code, marketCapRank = rank, priority = priority)
        val token = Token(
            coin = coin,
            blockchain = Blockchain(type = BlockchainType.Ethereum, name = "Ethereum", eip3091url = null),
            type = TokenType.Native,
            decimals = 18,
        )
        return FullCoin(coin, listOf(token))
    }

    private companion object {
        const val CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7"
    }
}
