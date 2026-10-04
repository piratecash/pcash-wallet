package cash.p.terminal.wallet.providers

import cash.p.terminal.network.pirate.domain.enity.PriceChangeCoinInfo
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HsProviderTest {

    private val piratePlaceRepository = mockk<PiratePlaceRepository>()
    private lateinit var hsProvider: HsProvider

    @Before
    fun setUp() {
        startKoin {
            modules(module { single { piratePlaceRepository } })
        }
        hsProvider = HsProvider(baseUrl = "https://example.com", apiKey = "key")
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun priceInfo(uid: String) = PriceChangeCoinInfo(
        uid = uid,
        price = BigDecimal.ONE,
        priceChange1h = BigDecimal.ZERO,
        priceChange24h = BigDecimal.ZERO,
        priceChange7d = BigDecimal.ZERO,
        priceChange30d = BigDecimal.ZERO,
        priceChange1y = BigDecimal.ZERO,
        priceChangeMax = BigDecimal.ZERO,
        lastUpdated = 1L
    )

    @Test
    fun getCoinPrices_requestedUids_returnedKeyedByRequestedUid() = runTest {
        coEvery {
            piratePlaceRepository.getCoinsPriceChange(listOf("weth", "bitcoin"), "usd")
        } returns listOf(priceInfo("weth"), priceInfo("bitcoin"))

        val prices = hsProvider.getCoinPrices(listOf("weth", "bitcoin"), "usd")

        assertEquals(setOf("weth", "bitcoin"), prices.map { it.coinUid }.toSet())
        coVerify(exactly = 1) { piratePlaceRepository.getCoinsPriceChange(listOf("weth", "bitcoin"), "usd") }
    }

    @Test
    fun getCoinPrices_duplicateUid_requestedOnce() = runTest {
        coEvery {
            piratePlaceRepository.getCoinsPriceChange(listOf("bitcoin"), "usd")
        } returns listOf(priceInfo("bitcoin"))

        hsProvider.getCoinPrices(listOf("bitcoin", "bitcoin"), "usd")

        coVerify(exactly = 1) { piratePlaceRepository.getCoinsPriceChange(listOf("bitcoin"), "usd") }
    }

    @Test
    fun getCoinPrices_repositoryReturnsNull_emptyList() = runTest {
        coEvery { piratePlaceRepository.getCoinsPriceChange(any(), any()) } returns null

        val prices = hsProvider.getCoinPrices(listOf("bitcoin"), "usd")

        assertTrue(prices.isEmpty())
    }
}
