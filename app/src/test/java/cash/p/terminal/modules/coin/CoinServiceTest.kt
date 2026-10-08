package cash.p.terminal.modules.coin

import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class CoinServiceTest {
    private val storedUids = MutableStateFlow(emptySet<String>())
    private val fullCoin = mockk<FullCoin> { every { coin.uid } returns COIN_UID }
    private val favoritesManager = mockk<MarketFavoritesManager> {
        every { favoriteCoinUids } returns storedUids
        coEvery { add(any()) } answers { storedUids.value += firstArg<String>() }
        coEvery { remove(any()) } answers { storedUids.value -= firstArg<String>() }
    }
    private val service = CoinService(fullCoin, favoritesManager)

    @After
    fun tearDown() {
        service.clear()
    }

    @Test
    fun isFavorite_favoritesChangedElsewhere_reflectsStoredMembership() {
        val observer = service.isFavorite.test()
        observer.awaitCount(1)

        storedUids.value = setOf(COIN_UID)

        observer.awaitCount(2)
        assertEquals(listOf(false, true), observer.values())
    }

    @Test
    fun favoriteThenUnfavorite_completed_endsNotFavorite() {
        val observer = service.isFavorite.test()
        observer.awaitCount(1)

        service.favorite()
        observer.awaitCount(2)
        service.unfavorite()
        observer.awaitCount(3)

        assertEquals(listOf(false, true, false), observer.values())
    }

    private companion object {
        const val COIN_UID = "bitcoin"
    }
}
