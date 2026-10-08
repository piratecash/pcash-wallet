package cash.p.terminal.wallet.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import cash.p.terminal.wallet.favorites.MarketFavoritesManager.Companion.FAVORITE_COIN_UIDS
import cash.p.terminal.wallet.favorites.MarketFavoritesManager.Companion.MANUAL_SORTING_ORDER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MarketFavoritesManager(
    private val dataStore: DataStore<Preferences>,
    private val listener: MarketFavoritesChangeListener,
) {
    val favoriteCoinUids: Flow<Set<String>> =
        dataStore.data.map { it.favoriteCoinUids }.distinctUntilChanged()

    val manualSortingOrder: Flow<List<String>> =
        dataStore.data.map { it.manualSortingOrder }.distinctUntilChanged()

    /**
     * Changes of the favorites set only — not of the manual order, which shares the store but must
     * not make subscribers reload the list. `drop(1)` skips the value DataStore replays to every
     * new collector, so this signals mutations, as the previous Rx subject did.
     */
    val dataUpdatedFlow: Flow<Unit> = favoriteCoinUids.drop(1).map { }

    suspend fun add(coinUid: String) = mutate {
        it[FAVORITE_COIN_UIDS] = it.favoriteCoinUids + coinUid
        it[MANUAL_SORTING_ORDER] = (it.manualSortingOrder + coinUid).encodeOrder()
    }

    suspend fun addAll(coinUids: List<String>) = mutate {
        it[FAVORITE_COIN_UIDS] = it.favoriteCoinUids + coinUids
    }

    suspend fun remove(coinUid: String) = mutate {
        it[FAVORITE_COIN_UIDS] = it.favoriteCoinUids - coinUid
        it[MANUAL_SORTING_ORDER] = (it.manualSortingOrder - coinUid).encodeOrder()
    }

    suspend fun setManualSortingOrder(coinUids: List<String>) = mutate {
        it[MANUAL_SORTING_ORDER] = coinUids.encodeOrder()
    }

    suspend fun getAll(): List<String> = dataStore.data.first().favoriteCoinUids.toList()

    /**
     * Secure reset: drops favorites and the manual order, but keeps the platform migration flag —
     * without it the next process start re-seeds the store from the purged legacy sources, wiping
     * what was added after the reset. Deliberately silent — the caller wipes the widgets itself.
     */
    suspend fun clear() {
        dataStore.edit {
            it.remove(FAVORITE_COIN_UIDS)
            it.remove(MANUAL_SORTING_ORDER)
        }
    }

    private suspend fun mutate(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
        listener.onFavoritesChanged()
    }

    companion object {
        val FAVORITE_COIN_UIDS: Preferences.Key<Set<String>> =
            stringSetPreferencesKey("favorite_coin_uids")
        val MANUAL_SORTING_ORDER: Preferences.Key<String> =
            stringPreferencesKey("manual_sorting_order")
    }
}

private const val ORDER_SEPARATOR = ","

private val Preferences.favoriteCoinUids: Set<String>
    get() = this[FAVORITE_COIN_UIDS].orEmpty()

private val Preferences.manualSortingOrder: List<String>
    get() = this[MANUAL_SORTING_ORDER]?.split(ORDER_SEPARATOR)?.filter(String::isNotEmpty).orEmpty()

private fun List<String>.encodeOrder() = joinToString(ORDER_SEPARATOR)
