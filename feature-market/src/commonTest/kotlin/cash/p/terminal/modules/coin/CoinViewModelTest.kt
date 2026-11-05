package cash.p.terminal.modules.coin

import cash.p.terminal.resources.Hud_Added_To_Watchlist
import cash.p.terminal.resources.Hud_Removed_from_Watchlist
import cash.p.terminal.resources.Res
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.MarketSettingsStore
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinViewModelTest {

    private val favorite = BehaviorSubject.createDefault(false)
    private val service = mockk<CoinService>(relaxed = true) {
        every { isFavorite } returns favorite
    }
    private val settings = mockk<MarketSettingsStore> {
        every { marketsTabEnabled } returns true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { service.fullCoin } returns fullCoinWith(TokenQuery(BlockchainType.Bitcoin, TokenType.Native))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fullCoinWith(tokenQuery: TokenQuery) = mockk<FullCoin> {
        every { tokens } returns listOf(mockk<Token> { every { this@mockk.tokenQuery } returns tokenQuery })
    }

    private fun viewModel() = CoinViewModel(service, listOf<Clearable>(service), settings)

    @Test
    fun tabs_coinWithAnalytics_includeDetails() {
        assertEquals(
            listOf(CoinModule.Tab.Overview, CoinModule.Tab.Details, CoinModule.Tab.Market),
            viewModel().tabs.toList()
        )
    }

    @Test
    fun tabs_analyticsDisabledToken_hideDetails() {
        every { service.fullCoin } returns fullCoinWith(TokenQuery(BlockchainType.Cosanta, TokenType.Native))

        assertEquals(listOf(CoinModule.Tab.Overview, CoinModule.Tab.Market), viewModel().tabs.toList())
    }

    @Test
    fun isFavorite_serviceEmits_followsService() {
        val viewModel = viewModel()
        assertFalse(viewModel.isFavorite)

        favorite.onNext(true)

        assertTrue(viewModel.isFavorite)
    }

    @Test
    fun onFavoriteClick_always_favoritesAndShowsAddedMessage() {
        val viewModel = viewModel()

        viewModel.onFavoriteClick()

        verify { service.favorite() }
        assertEquals(Res.string.Hud_Added_To_Watchlist, viewModel.successMessage)
    }

    @Test
    fun onUnfavoriteClick_always_unfavoritesAndShowsRemovedMessage() {
        val viewModel = viewModel()

        viewModel.onUnfavoriteClick()

        verify { service.unfavorite() }
        assertEquals(Res.string.Hud_Removed_from_Watchlist, viewModel.successMessage)
    }

    @Test
    fun onSuccessMessageShown_messagePending_clearsMessage() {
        val viewModel = viewModel()
        viewModel.onFavoriteClick()

        viewModel.onSuccessMessageShown()

        assertNull(viewModel.successMessage)
    }

    @Test
    fun isWatchlistEnabled_marketsTabSetting_isReadFromSettings() {
        every { settings.marketsTabEnabled } returns false

        assertFalse(viewModel().isWatchlistEnabled)
    }
}
