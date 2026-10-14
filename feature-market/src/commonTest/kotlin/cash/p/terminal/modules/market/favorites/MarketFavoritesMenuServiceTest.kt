package cash.p.terminal.modules.market.favorites

import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener
import io.horizontalsystems.core.MarketSettingsStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketFavoritesMenuServiceTest {

    private val settings = mockk<MarketSettingsStore>(relaxed = true)
    private val changeListener = mockk<MarketFavoritesChangeListener>(relaxed = true)
    private val service = MarketFavoritesMenuService(settings, changeListener)

    @Test
    fun listSorting_set_writesNameAndNotifiesOnce() {
        service.listSorting = WatchlistSorting.Gainers

        verify(exactly = 1) { settings.marketFavoritesSorting = "Gainers" }
        verify(exactly = 1) { changeListener.onFavoritesChanged() }
    }

    @Test
    fun timeDuration_set_writesNameAndNotifiesOnce() {
        service.timeDuration = TimeDuration.ThirtyDay

        verify(exactly = 1) { settings.marketFavoritesPeriod = "ThirtyDay" }
        verify(exactly = 1) { changeListener.onFavoritesChanged() }
    }

    @Test
    fun showSignals_set_writesValueAndNotifiesOnce() {
        service.showSignals = true

        verify(exactly = 1) { settings.marketFavoritesShowSignals = true }
        verify(exactly = 1) { changeListener.onFavoritesChanged() }
    }

    @Test
    fun listSorting_storedName_returnsMatchingSorting() {
        every { settings.marketFavoritesSorting } returns "LowestCap"

        assertEquals(WatchlistSorting.LowestCap, service.listSorting)
    }

    @Test
    fun listSorting_nullOrUnknownName_returnsManual() {
        every { settings.marketFavoritesSorting } returns null
        assertEquals(WatchlistSorting.Manual, service.listSorting)

        every { settings.marketFavoritesSorting } returns "Removed"
        assertEquals(WatchlistSorting.Manual, service.listSorting)
    }

    @Test
    fun timeDuration_storedName_returnsMatchingDuration() {
        every { settings.marketFavoritesPeriod } returns "SevenDay"

        assertEquals(TimeDuration.SevenDay, service.timeDuration)
    }

    @Test
    fun timeDuration_nullOrUnknownName_returnsOneDay() {
        every { settings.marketFavoritesPeriod } returns null
        assertEquals(TimeDuration.OneDay, service.timeDuration)

        every { settings.marketFavoritesPeriod } returns "Removed"
        assertEquals(TimeDuration.OneDay, service.timeDuration)
    }
}
