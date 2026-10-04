package cash.p.terminal.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.KeyStoreCleaner
import cash.p.terminal.core.managers.MarketFavoritesDataMigration
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.MarketFavoritesDao
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.widgets.MarketWatchlistResetCleaner
import cash.p.terminal.widgets.MarketWidgetWorker
import cash.p.terminal.widgets.MarketWidget
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResetUseCaseTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context = mockk<Context>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val contacts = mockk<ContactsRepository>(relaxed = true)
    private val glance = mockk<GlanceAppWidgetManager>(relaxed = true)
    private val icons = mockk<AppIconService>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>(relaxed = true)
    private val keyStoreCleaner = mockk<KeyStoreCleaner>(relaxed = true)
    private val marketFavorites = mockk<MarketFavoritesManager>(relaxed = true)
    private val favoritesListener = mockk<MarketFavoritesChangeListener>(relaxed = true)
    private val legacyFavoritesDao = mockk<MarketFavoritesDao>(relaxed = true)
    private val legacyPreferences = mockk<SharedPreferences>(relaxed = true)
    private val favoritesScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        favoritesScopes.forEach { it.cancel() }
        unmockkAll()
    }

    @Test
    fun invoke_favoritesPresent_leavesFavoritesAndManualOrderEmpty() = runTest {
        prepareFilePurge()
        val file = File(temporaryFolder.newFolder("favorites"), "favorites.preferences_pb")
        val favorites = favoritesManager(file)
        favorites.add("bitcoin")

        reset(favorites)()

        assertEquals(emptyList(), favorites.getAll())
        assertEquals(emptyList(), favorites.manualSortingOrder.first())
        releaseFavoriteStores()
        assertEquals(emptyList(), favoritesManager(file).getAll())
    }

    @Test
    fun invoke_cleanupComplete_clearsFavoritesAfterDatabaseAndPreferencesPurge() = runTest {
        prepareFilePurge()

        reset()()

        coVerifyOrder {
            keyStoreCleaner.cleanExplicitReset()
            database.clearAllTables()
            marketFavorites.clear()
        }
    }

    @Test
    fun invoke_favoritesClearFails_stillPurgesSensitiveFilesAndFinishes() = runTest {
        prepareFilePurge()
        coEvery { marketFavorites.clear() } throws IOException("favorites")

        reset()()

        coVerify { contacts.clear() }
        coVerify { preflight.finishExplicitReset() }
    }

    // The reset returns to the same running process, so a favorite added right after it must not be
    // re-migrated away from the legacy stores the reset has just emptied.
    @Test
    fun invoke_favoriteAddedAfterReset_survivesNewInstanceWithLegacyMigration() = runTest {
        prepareFilePurge()
        every { legacyFavoritesDao.getAll() } returns emptyList()
        every { legacyPreferences.getString(any(), any()) } returns null
        val file = File(temporaryFolder.newFolder("favorites"), "favorites.preferences_pb")
        val favorites = favoritesManager(file, listOf(legacyMigration()))
        favorites.getAll()

        reset(favorites)()
        favorites.add("bitcoin")

        releaseFavoriteStores()
        val restarted = favoritesManager(file, listOf(legacyMigration()))
        assertEquals(listOf("bitcoin"), restarted.getAll())
        assertEquals(listOf("bitcoin"), restarted.manualSortingOrder.first())
    }

    // DataStore frees its file only once the owning scope completes; reopening before that throws.
    private suspend fun releaseFavoriteStores() {
        favoritesScopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        favoritesScopes.clear()
    }

    private fun legacyMigration() =
        MarketFavoritesDataMigration(legacyFavoritesDao, legacyPreferences)

    private fun favoritesManager(
        file: File,
        migrations: List<DataMigration<Preferences>> = emptyList(),
    ): MarketFavoritesManager {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also(favoritesScopes::add)
        return MarketFavoritesManager(
            dataStore = PreferenceDataStoreFactory.create(
                migrations = migrations,
                scope = scope,
                produceFile = { file },
            ),
            listener = favoritesListener,
        )
    }

    @Test
    fun invoke_intentOrBeamCleanupFails_hasNoGlobalDestructiveEffects() = runTest {
        mockkObject(WCDelegate)
        coEvery { preflight.prepareExplicitReset() } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { reset()() }

        verify {
            listOf(WCDelegate, context, localStorage, database, keyStoreCleaner, contacts, glance, icons) wasNot Called
        }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_keystoreBarrierBlocks_stopsBeforeDatabaseOrFilePurge() = runTest {
        prepareReset()
        coEvery { keyStoreCleaner.cleanExplicitReset() } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { reset()() }

        coVerifyOrder {
            preflight.prepareExplicitReset()
            WCDelegate.getActiveSessions()
            keyStoreCleaner.cleanExplicitReset()
        }
        verify { listOf(context, database, contacts, glance, icons) wasNot Called }
        verify(exactly = 0) { localStorage.mainShowedOnce = any() }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_keystoreResetCancelled_doesNotContinuePurge() = runTest {
        prepareReset()
        coEvery { keyStoreCleaner.cleanExplicitReset() } throws CancellationException()

        assertFailsWith<CancellationException> { reset()() }

        verify { listOf(context, database, contacts, glance, icons) wasNot Called }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_globalLinkageFailure_doesNotClearDatabaseOrIntent() = runTest {
        prepareReset()
        coEvery { keyStoreCleaner.cleanExplicitReset() } throws IOException("linkage")

        assertFailsWith<IOException> { reset()() }

        verify { database wasNot Called }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_appDatabaseFailure_doesNotRemoveResetIntent() = runTest {
        prepareReset()
        every { database.clearAllTables() } throws IOException("database")

        assertFailsWith<IOException> { reset()() }

        coVerify(exactly = 0) { preflight.finishExplicitReset() }
        verify { listOf(context, contacts, glance) wasNot Called }
    }

    @Test
    fun invoke_cleanupComplete_finishesMarkerOnlyAfterGlobalPurge() = runTest {
        prepareFilePurge()

        reset()()

        coVerifyOrder {
            preflight.prepareExplicitReset()
            keyStoreCleaner.cleanExplicitReset()
            database.clearAllTables()
            contacts.clear()
            preflight.finishExplicitReset()
        }
        coVerify(exactly = 1) { contacts.clear() }
        coVerify(exactly = 0) { preflight.ensureCanReset() }
        verify { context.deleteDatabase("logging_database") }
    }

    // The global clear already ran and the BEAM wrappers are shredded, so a retained marker only
    // defers a leftover file to startup cleanup; it must not surface as a failed reset.
    @Test
    fun invoke_markerRemovalFails_completesGlobalClearWithoutFailing() = runTest {
        prepareFilePurge()
        coEvery { preflight.finishExplicitReset() } throws AccountDeletionBlockedException()

        reset()()

        coVerifyOrder {
            keyStoreCleaner.cleanExplicitReset()
            database.clearAllTables()
            preflight.finishExplicitReset()
        }
        verify { context.deleteDatabase("logging_database") }
    }

    @Test
    fun invoke_markerRemovalCancelled_propagatesCancellation() = runTest {
        prepareFilePurge()
        coEvery { preflight.finishExplicitReset() } throws CancellationException()

        assertFailsWith<CancellationException> { reset()() }

        coVerify { keyStoreCleaner.cleanExplicitReset() }
    }

    private fun prepareReset() {
        mockkObject(WCDelegate)
        every { WCDelegate.getActiveSessions() } returns emptyList()
        every { WCDelegate.deleteAllPairings() } returns Unit
        every { localStorage.appIcon } returns null
    }

    private fun prepareFilePurge() {
        prepareReset()
        every { context.filesDir } returns temporaryFolder.root
        every { context.getDir(any(), any()) } returns temporaryFolder.newFolder("tor")
        mockkObject(MarketWidgetWorker.Companion)
        every { MarketWidgetWorker.cancel(context) } returns Unit
        coEvery { glance.getGlanceIds(MarketWidget::class.java) } returns emptyList()
    }

    private fun TestScope.reset(favorites: MarketFavoritesManager = marketFavorites) = ResetUseCase(
        context, localStorage, database, contacts,
        TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
        MarketWatchlistResetCleaner(context, glance, favorites),
        icons, preflight, keyStoreCleaner,
    )
}
