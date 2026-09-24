package cash.p.terminal.modules.market.search

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class MarketSearchServiceTest {

    private val marketKit = mockk<MarketKitWrapper>()

    private fun fullCoin(uid: String, coinGeckoId: String?, vararg tokenTypes: TokenType): FullCoin {
        val coin = Coin(uid = uid, name = uid, code = uid.uppercase(), coinGeckoId = coinGeckoId)
        val blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
        return FullCoin(coin, tokenTypes.map { Token(coin, blockchain, it, 8) })
    }

    @Test
    fun setQuery_resultsContainSyntheticCoin_filtersItOut() = runTest {
        val original = fullCoin("bitcoin", "bitcoin")
        val synthetic = fullCoin("thorchain-secured-bitcoin", "bitcoin", TokenType.ThorchainAsset("btc-btc"))
        coEvery { marketKit.fullCoins("bitcoin") } returns listOf(original, synthetic)

        val service = MarketSearchService(marketKit)
        service.setQuery("bitcoin")

        assertEquals(listOf(original), service.stateFlow.value.results)
    }
}
