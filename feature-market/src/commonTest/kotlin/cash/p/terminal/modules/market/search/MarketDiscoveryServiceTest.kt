package cash.p.terminal.modules.market.search

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.core.MarketSettingsStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketDiscoveryServiceTest {

    private var storedRecents: List<String> = emptyList()
    private val settingsStore = mockk<MarketSettingsStore> {
        every { marketSearchRecentCoinUids } answers { storedRecents }
        every { marketSearchRecentCoinUids = any() } answers { storedRecents = firstArg() }
    }
    private val marketKit = mockk<MarketKitWrapper>()
    private val service = MarketDiscoveryService(marketKit, settingsStore)

    @Test
    fun addCoinToRecent_newCoin_isStoredFirst() {
        storedRecents = listOf("bitcoin", "ethereum")

        service.addCoinToRecent(coin("tether"))

        assertEquals(listOf("tether", "bitcoin", "ethereum"), storedRecents)
    }

    @Test
    fun addCoinToRecent_alreadyRecentCoin_movesToFrontWithoutDuplicate() {
        storedRecents = listOf("bitcoin", "ethereum", "tether")

        service.addCoinToRecent(coin("tether"))

        assertEquals(listOf("tether", "bitcoin", "ethereum"), storedRecents)
    }

    @Test
    fun addCoinToRecent_fiveRecentsStored_dropsTheOldest() {
        storedRecents = listOf("c1", "c2", "c3", "c4", "c5")

        service.addCoinToRecent(coin("c6"))

        assertEquals(listOf("c6", "c1", "c2", "c3", "c4"), storedRecents)
    }

    @Test
    fun start_storedRecents_emitsRecentCoinsMostRecentFirst() = runTest {
        storedRecents = listOf("tether", "bitcoin")
        every { marketKit.fullCoins(storedRecents) } returns listOf(fullCoin("bitcoin"), fullCoin("tether"))
        coEvery { marketKit.fullCoins("") } returns listOf(fullCoin("ethereum"))

        service.start()

        val state = service.stateFlow.value
        assertEquals(listOf("tether", "bitcoin"), state.recent.map { it.coin.uid })
        assertEquals(listOf("ethereum"), state.popular.map { it.coin.uid })
    }

    private fun coin(uid: String) = Coin(uid = uid, name = uid, code = uid.uppercase())

    private fun fullCoin(uid: String) = FullCoin(coin(uid), emptyList())
}
