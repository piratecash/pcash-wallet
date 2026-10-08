package cash.p.terminal.core.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.managers.MarketFavoritesDataMigration.Companion.MANUAL_SORTING_ORDER_KEY
import cash.p.terminal.core.managers.MarketFavoritesDataMigration.Companion.MIGRATED
import cash.p.terminal.core.storage.FavoriteCoin
import cash.p.terminal.core.storage.MarketFavoritesDao
import cash.p.terminal.wallet.favorites.MarketFavoritesManager.Companion.FAVORITE_COIN_UIDS
import cash.p.terminal.wallet.favorites.MarketFavoritesManager.Companion.MANUAL_SORTING_ORDER
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MarketFavoritesDataMigrationTest {

    private val dao: MarketFavoritesDao = mockk(relaxed = true)
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("market_favorites_migration_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        every { dao.getAll() } returns emptyList()
    }

    @After
    fun tearDown() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun migrate_favoriteRowsAndOrderCsv_writesBothKeysAndFlag() = runTest {
        every { dao.getAll() } returns listOf(FavoriteCoin("bitcoin"), FavoriteCoin("dash"))
        preferences.edit(commit = true) { putString(MANUAL_SORTING_ORDER_KEY, "dash,bitcoin") }

        val migrated = migration().migrate(emptyPreferences())

        assertEquals(setOf("bitcoin", "dash"), migrated[FAVORITE_COIN_UIDS])
        assertEquals("dash,bitcoin", migrated[MANUAL_SORTING_ORDER])
        assertTrue(migrated[MIGRATED] == true)
    }

    @Test
    fun shouldMigrate_flagAbsent_returnsTrue() = runTest {
        assertTrue(migration().shouldMigrate(emptyPreferences()))
    }

    @Test
    fun shouldMigrate_afterMigration_returnsFalse() = runTest {
        val migration = migration()

        val migrated = migration.migrate(emptyPreferences())

        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun migrate_emptySources_writesEmptyFavoritesAndOrder() = runTest {
        val migrated = migration().migrate(emptyPreferences())

        assertEquals(emptySet(), migrated[FAVORITE_COIN_UIDS])
        assertEquals("", migrated[MANUAL_SORTING_ORDER])
    }

    @Test
    fun migrate_orderCsvWithUidMissingFromFavorites_preservesOrderAsStored() = runTest {
        every { dao.getAll() } returns listOf(FavoriteCoin("bitcoin"))
        preferences.edit(commit = true) { putString(MANUAL_SORTING_ORDER_KEY, "bitcoin,zcash") }

        val migrated = migration().migrate(emptyPreferences())

        assertEquals(setOf("bitcoin"), migrated[FAVORITE_COIN_UIDS])
        assertEquals("bitcoin,zcash", migrated[MANUAL_SORTING_ORDER])
    }

    @Test
    fun cleanUp_afterMigration_deletesFavoriteRowsAndOrderPreference() = runTest {
        preferences.edit(commit = true) { putString(MANUAL_SORTING_ORDER_KEY, "bitcoin") }

        migration().cleanUp()

        verify { dao.deleteAll() }
        assertNull(preferences.getString(MANUAL_SORTING_ORDER_KEY, null))
    }

    private fun migration() = MarketFavoritesDataMigration(dao, preferences)
}
