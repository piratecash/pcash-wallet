package cash.p.terminal.modules.market.filters

import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.core.Translator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketFiltersViewModelTest {

    private var outperformedBtcFilter = false
    private val service = mockk<MarketFiltersService>(relaxed = true) {
        every { filterOutperformedBtcOn = any() } answers { outperformedBtcFilter = firstArg() }
        coEvery { fetchNumberOfItems() } answers { if (outperformedBtcFilter) MATCHING_ITEMS else 0 }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(Translator)
        every { Translator.getString(any<StringResource>(), *anyVararg()) } answers {
            firstArg<StringResource>().key
        }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
        Dispatchers.resetMain()
    }

    @Test
    fun updateOutperformedBtcOn_enabled_requestsCountWithNewFilterAndEnablesButton() = runTest(dispatcher) {
        val viewModel = MarketFiltersViewModel(service, dispatcherProvider)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.showSpinner)
        assertFalse(viewModel.uiState.buttonEnabled)

        viewModel.updateOutperformedBtcOn(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.buttonEnabled)
        assertTrue(viewModel.uiState.outperformedBtcOn)
        verify { service.filterOutperformedBtcOn = true }
    }

    @Test
    fun updateCoinList_top100_resizesCoinListAndClearsServiceCache() = runTest(dispatcher) {
        val viewModel = MarketFiltersViewModel(service, dispatcherProvider)

        viewModel.updateCoinList(FilterViewItemWrapper("Top 100", CoinList.Top100))
        advanceUntilIdle()

        verify { service.coinCount = CoinList.Top100.itemsCount }
        verify { service.clearCache() }
        assertEquals(CoinList.Top100, viewModel.uiState.coinListSet.item)
    }

    @Test
    fun reset_afterChangingFilters_restoresDefaults() = runTest(dispatcher) {
        val viewModel = MarketFiltersViewModel(service, dispatcherProvider)
        viewModel.updateOutperformedBtcOn(true)
        viewModel.updateSolidCexOn(true)
        viewModel.updateCoinList(FilterViewItemWrapper("Top 100", CoinList.Top100))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.buttonEnabled)

        viewModel.reset()
        advanceUntilIdle()

        val state = viewModel.uiState
        assertFalse(state.buttonEnabled)
        assertFalse(state.outperformedBtcOn)
        assertFalse(state.solidCexOn)
        assertEquals(CoinList.Top250, state.coinListSet.item)
        assertFalse(outperformedBtcFilter)
    }

    private companion object {
        const val MATCHING_ITEMS = 42
    }
}
