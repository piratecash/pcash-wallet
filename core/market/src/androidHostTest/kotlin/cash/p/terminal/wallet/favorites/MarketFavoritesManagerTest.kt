package cash.p.terminal.wallet.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class MarketFavoritesManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val listener = mockk<MarketFavoritesChangeListener>(relaxed = true)
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun add_newCoin_appendsToFavoritesAndManualOrder() = runBlocking {
        val manager = manager()

        manager.add("bitcoin")
        manager.add("dash")

        assertEquals(setOf("bitcoin", "dash"), manager.favoriteCoinUids.first())
        assertEquals(listOf("bitcoin", "dash"), manager.manualSortingOrder.first())
    }

    @Test
    fun remove_favoriteCoin_dropsFromFavoritesAndManualOrder() = runBlocking {
        val manager = manager()
        manager.add("bitcoin")
        manager.add("dash")

        manager.remove("bitcoin")

        assertEquals(listOf("dash"), manager.getAll())
        assertEquals(listOf("dash"), manager.manualSortingOrder.first())
    }

    @Test
    fun addAll_someAlreadyFavorite_unionsFavoritesAndLeavesManualOrderUntouched() = runBlocking {
        val manager = manager()
        manager.add("bitcoin")

        manager.addAll(listOf("bitcoin", "dash", "litecoin"))

        assertEquals(setOf("bitcoin", "dash", "litecoin"), manager.favoriteCoinUids.first())
        assertEquals(listOf("bitcoin"), manager.manualSortingOrder.first())
    }

    @Test
    fun setManualSortingOrder_reorderedUids_persistsNewOrderWithoutChangingFavorites() = runBlocking {
        val manager = manager()
        manager.add("bitcoin")
        manager.add("dash")

        manager.setManualSortingOrder(listOf("dash", "bitcoin"))

        assertEquals(listOf("dash", "bitcoin"), manager.manualSortingOrder.first())
        assertEquals(setOf("bitcoin", "dash"), manager.favoriteCoinUids.first())
    }

    @Test
    fun add_singleMutation_notifiesListenerOncePerMutation() = runBlocking {
        val manager = manager()

        manager.add("bitcoin")
        verify(exactly = 1) { listener.onFavoritesChanged() }

        manager.remove("bitcoin")
        verify(exactly = 2) { listener.onFavoritesChanged() }
    }

    @Test
    fun getAll_newInstanceOnSameFile_returnsPersistedFavoritesAndOrder() = runBlocking {
        val file = favoritesFile()
        val first = manager(file)
        first.add("bitcoin")
        first.add("dash")
        closeScopes()

        val second = manager(file)

        assertEquals(listOf("bitcoin", "dash"), second.getAll())
        assertEquals(listOf("bitcoin", "dash"), second.manualSortingOrder.first())
    }

    @Test
    fun clear_withFavorites_emptiesFavoritesAndManualOrder() = runBlocking {
        val file = favoritesFile()
        val manager = manager(file)
        manager.add("bitcoin")

        manager.clear()

        assertEquals(emptyList(), manager.getAll())
        assertEquals(emptyList(), manager.manualSortingOrder.first())
        closeScopes()
        assertEquals(emptyList(), manager(file).getAll())
    }

    // The manual order shares the store with the favorites set, but reordering the watchlist must
    // not look like a favorites change: subscribers reload the whole list on it.
    @Test
    fun dataUpdatedFlow_manualOrderChangedOnly_doesNotSignalUpdate() = runTest {
        val manager = MarketFavoritesManager(FakeDataStore(), listener)
        val updates = mutableListOf<Unit>()
        backgroundScope.launch { manager.dataUpdatedFlow.collect { updates.add(it) } }
        runCurrent()

        manager.add("bitcoin")
        runCurrent()
        assertEquals(1, updates.size)

        manager.setManualSortingOrder(listOf("zcash", "bitcoin"))
        runCurrent()
        assertEquals(1, updates.size)
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private fun favoritesFile() = File(temporaryFolder.root, "market_favorites.preferences_pb")

    private fun closeScopes() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    private fun manager(file: File = favoritesFile()): MarketFavoritesManager {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also(scopes::add)
        return MarketFavoritesManager(
            dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            listener = listener,
        )
    }
}
