package cash.p.terminal.core.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import cash.p.terminal.core.storage.MarketFavoritesDao
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.favorites.MarketFavoritesMigration

/**
 * Seeds the favorites DataStore from the pre-KMP Android stores: the `FavoriteCoin` table and the
 * manual order CSV in the default SharedPreferences.
 */
class MarketFavoritesDataMigration(
    private val dao: MarketFavoritesDao,
    private val preferences: SharedPreferences,
) : MarketFavoritesMigration {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[MIGRATED] != true

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            set(MarketFavoritesManager.FAVORITE_COIN_UIDS, dao.getAll().map { it.coinUid }.toSet())
            set(
                MarketFavoritesManager.MANUAL_SORTING_ORDER,
                preferences.getString(MANUAL_SORTING_ORDER_KEY, null).orEmpty()
            )
            set(MIGRATED, true)
        }.toPreferences()

    override suspend fun cleanUp() {
        dao.deleteAll()
        preferences.edit { remove(MANUAL_SORTING_ORDER_KEY) }
    }

    companion object {
        internal const val MANUAL_SORTING_ORDER_KEY = "market_favorites_manual_sorting_order"
        internal val MIGRATED: Preferences.Key<Boolean> =
            booleanPreferencesKey("market_favorites_migrated")
    }
}
