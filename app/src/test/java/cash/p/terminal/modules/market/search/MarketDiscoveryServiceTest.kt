package cash.p.terminal.modules.market.search

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class MarketDiscoveryServiceTest {

    private val marketKit = mockk<MarketKitWrapper>()
    private val localStorage = mockk<ILocalStorage>(relaxed = true)

    private fun fullCoin(uid: String, coinGeckoId: String?, vararg tokenTypes: TokenType): FullCoin {
        val coin = Coin(uid = uid, name = uid, code = uid.uppercase(), coinGeckoId = coinGeckoId)
        val blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
        return FullCoin(coin, tokenTypes.map { Token(coin, blockchain, it, 8) })
    }

    @Test
    fun start_popularCoinsContainSyntheticCoin_filtersItOut() = runTest {
        val original = fullCoin("bitcoin", "bitcoin")
        val synthetic = fullCoin("thorchain-secured-bitcoin", "bitcoin", TokenType.ThorchainAsset("btc-btc"))
        every { localStorage.marketSearchRecentCoinUids } returns emptyList()
        every { marketKit.fullCoins(emptyList()) } returns emptyList()
        coEvery { marketKit.fullCoins("") } returns listOf(original, synthetic)

        val service = MarketDiscoveryService(marketKit, localStorage)
        service.start()

        assertEquals(listOf(original), service.stateFlow.value.popular)
    }

    @Test
    fun start_recentCoinsContainSyntheticCoin_filtersItOut() = runTest {
        val normalRecent = fullCoin("ethereum", "ethereum")
        val syntheticRecent = fullCoin("thorchain-secured-bitcoin", "bitcoin", TokenType.ThorchainAsset("btc-btc"))
        val recentUids = listOf(normalRecent.coin.uid, syntheticRecent.coin.uid)
        every { localStorage.marketSearchRecentCoinUids } returns recentUids
        every { marketKit.fullCoins(recentUids) } returns listOf(normalRecent, syntheticRecent)
        coEvery { marketKit.fullCoins("") } returns emptyList()

        val service = MarketDiscoveryService(marketKit, localStorage)
        service.start()

        assertEquals(listOf(normalRecent), service.stateFlow.value.recent)
    }
}
