package cash.p.terminal.modules.settings.appcache

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheType
import cash.p.terminal.wallet.storage.CoinHistoricalPriceDao
import cash.p.terminal.wallet.storage.CoinPriceDao
import cash.p.terminal.wallet.storage.MarketDatabase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppCacheViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val cleaner = mockk<PlatformCacheCleaner>(relaxed = true)
    private val coinPriceDao = mockk<CoinPriceDao>(relaxed = true)
    private val historicalPriceDao = mockk<CoinHistoricalPriceDao>(relaxed = true)
    private val marketDatabase = mockk<MarketDatabase> {
        every { coinPriceDao() } returns coinPriceDao
        every { coinHistoricalPriceDao() } returns historicalPriceDao
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        AppCacheViewModel(cleaner, TestDispatcherProvider(dispatcher, TestScope(dispatcher)), marketDatabase)

    @Test
    fun clearCache_http_clearsOnlyHttp() {
        val viewModel = createViewModel()

        viewModel.clearCache(CacheType.HTTP)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { cleaner.clearHttp() }
        coVerify(exactly = 0) { cleaner.clearImages() }
        coVerify(exactly = 0) { cleaner.clearDatabase() }
        verify(exactly = 0) { coinPriceDao.deleteAll() }
    }

    @Test
    fun clearCache_marketPrices_clearsBothMarketDaosOnly() {
        val viewModel = createViewModel()

        viewModel.clearCache(CacheType.MARKET_PRICES)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { coinPriceDao.deleteAll() }
        verify(exactly = 1) { historicalPriceDao.deleteAll() }
        coVerify(exactly = 0) { cleaner.clearHttp() }
    }

    @Test
    fun clearCache_inProgress_marksItemThenResets() {
        val viewModel = createViewModel()

        viewModel.clearCache(CacheType.IMAGE)
        dispatcher.scheduler.runCurrent()
        assertEquals(CacheType.IMAGE, viewModel.uiState.value.clearingItemId)

        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.clearingItemId)
    }

    @Test
    fun clearAllCaches_clearsEverythingAndResetsFlag() {
        val viewModel = createViewModel()

        viewModel.clearAllCaches()
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isClearing)

        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { cleaner.clearHttp() }
        coVerify(exactly = 1) { cleaner.clearImages() }
        coVerify(exactly = 1) { cleaner.clearDatabase() }
        verify(exactly = 1) { coinPriceDao.deleteAll() }
        verify(exactly = 1) { historicalPriceDao.deleteAll() }
        assertFalse(viewModel.uiState.value.isClearing)
    }
}
