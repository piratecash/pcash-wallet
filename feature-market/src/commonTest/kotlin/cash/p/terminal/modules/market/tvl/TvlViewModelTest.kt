package cash.p.terminal.modules.market.tvl

import cash.p.terminal.modules.market.tvl.TvlModule.Chain
import cash.p.terminal.modules.market.tvl.TvlModule.SelectorDialogState
import cash.p.terminal.modules.market.tvl.TvlModule.TvlDiffType
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class TvlViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val usd = Currency("USD", "$", 2)
    private val emissions = BehaviorSubject.create<DataState<List<TvlModule.MarketTvlItem>>>()
    private val service = mockk<TvlService>(relaxed = true) {
        every { marketTvlItemsObservable } returns emissions
        every { chains } returns Chain.values().toList()
        every { chain } returns Chain.All
        every { sortDescending } returns true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(Translator)
        every { Translator.getString(any<StringResource>(), *anyVararg()) } returns "text"
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
        Dispatchers.resetMain()
    }

    @Test
    fun init_noEmissionYet_isLoadingWithoutData() {
        val viewModel = createViewModel()

        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
        assertNull(viewModel.uiState.tvlData)
        verify { service.start() }
    }

    @Test
    fun emission_serviceFilteredAndSorted_viewItemsKeepServiceOrderAndSelection() {
        every { service.chain } returns Chain.Ethereum
        every { service.sortDescending } returns false
        val viewModel = createViewModel()

        emissions.onNext(DataState.Success(listOf(item("Aave", "1"), item("Lido", "2"))))

        val tvlData = checkNotNull(viewModel.uiState.tvlData)
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(Chain.Ethereum, tvlData.chainSelect.selected)
        assertFalse(tvlData.sortDescending)
        assertEquals(listOf("Aave", "Lido"), tvlData.coinTvlViewItems.map { it.name })
        assertEquals(listOf("1", "2"), tvlData.coinTvlViewItems.map { it.rank })
    }

    @Test
    fun emission_error_showsErrorAndKeepsPreviousData() {
        val viewModel = createViewModel()
        emissions.onNext(DataState.Success(listOf(item("Aave", "1"))))

        emissions.onNext(DataState.Error(RuntimeException("boom")))

        assertTrue(viewModel.uiState.viewState is ViewState.Error)
        assertEquals(1, viewModel.uiState.tvlData?.coinTvlViewItems?.size)
    }

    @Test
    fun onToggleTvlDiffType_twice_returnsToPercent() {
        val viewModel = createViewModel()
        assertEquals(TvlDiffType.Percent, viewModel.uiState.tvlDiffType)

        viewModel.onToggleTvlDiffType()
        assertEquals(TvlDiffType.Currency, viewModel.uiState.tvlDiffType)

        viewModel.onToggleTvlDiffType()
        assertEquals(TvlDiffType.Percent, viewModel.uiState.tvlDiffType)
    }

    @Test
    fun onToggleSortType_descending_asksServiceForAscending() {
        val viewModel = createViewModel()

        viewModel.onToggleSortType()

        verify { service.sortDescending = false }
    }

    @Test
    fun chainSelector_openThenSelect_setsServiceChainAndClosesDialog() {
        val viewModel = createViewModel()

        viewModel.onClickChainSelector()
        assertTrue(viewModel.uiState.chainSelectorDialogState is SelectorDialogState.Opened)

        viewModel.onSelectChain(Chain.Solana)

        verify { service.chain = Chain.Solana }
        assertEquals(SelectorDialogState.Closed, viewModel.uiState.chainSelectorDialogState)
    }

    @Test
    fun chainSelector_dismissed_closesDialogWithoutChangingChain() {
        val viewModel = createViewModel()
        viewModel.onClickChainSelector()

        viewModel.onChainSelectorDialogDismiss()

        assertEquals(SelectorDialogState.Closed, viewModel.uiState.chainSelectorDialogState)
        verify(exactly = 0) { service.chain = any() }
    }

    private fun createViewModel() = TvlViewModel(service, TvlViewItemFactory())

    private fun item(name: String, rank: String) = TvlModule.MarketTvlItem(
        fullCoin = null,
        name = name,
        chains = listOf("Ethereum"),
        iconUrl = "",
        tvl = CurrencyValue(usd, BigDecimal.TEN),
        diff = null,
        diffPercent = null,
        rank = rank,
    )
}
