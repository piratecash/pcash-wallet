package io.horizontalsystems.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopSettingsStoreTest {

    private lateinit var dir: File
    private val file get() = File(dir, "app_settings.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("settings-store").toFile()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    private fun openStore(): DesktopSettingsStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return DesktopSettingsStore(file, scope)
    }

    /** DataStore releases its file in a completion handler; join() may return before that handler has run. */
    private fun closeStore(scope: CoroutineScope) {
        val job = scope.coroutineContext.job
        val released = CompletableDeferred<Unit>()
        job.invokeOnCompletion { released.complete(Unit) } // runs after DataStore's own handler
        job.cancel()
        runBlocking { released.await() }
    }

    @Test
    fun read_missingFile_returnsDefaults() {
        val store = openStore()

        assertDefaults(store)
    }

    @Test
    fun read_corruptFile_returnsDefaultsInsteadOfCrashing() {
        file.writeBytes(byteArrayOf(0x7F, 0x13, 0x00, 0x42, 0x55, 0x01, 0x02, 0x03))

        val store = openStore()

        assertDefaults(store)
    }

    @Test
    fun write_allKeys_surviveReopeningTheFile() {
        val first = openStore()
        first.marketSearchRecentCoinUids = listOf("bitcoin", "ethereum")
        first.marketFavoritesSorting = "HighestCap"
        first.marketFavoritesPeriod = "OneDay"
        first.marketFavoritesShowSignals = true
        first.chartIndicatorsEnabled = true
        first.marketsTabEnabled = false
        first.launchPage = "Watchlist"
        first.currentMarketTab = "Posts"
        first.baseCurrencyCode = "EUR"
        closeStore(scopes.removeAt(scopes.lastIndex))

        val second = openStore()

        assertEquals(listOf("bitcoin", "ethereum"), second.marketSearchRecentCoinUids)
        assertEquals("HighestCap", second.marketFavoritesSorting)
        assertEquals("OneDay", second.marketFavoritesPeriod)
        assertTrue(second.marketFavoritesShowSignals)
        assertTrue(second.chartIndicatorsEnabled)
        assertFalse(second.marketsTabEnabled)
        assertFalse(second.marketsTabEnabledFlow.value)
        assertEquals("Watchlist", second.launchPage)
        assertEquals("Posts", second.currentMarketTab)
        assertEquals("EUR", second.baseCurrencyCode)
    }

    @Test
    fun write_nullString_clearsStoredValue() {
        val store = openStore()
        store.launchPage = "Market"

        store.launchPage = null

        assertNull(store.launchPage)
    }

    @Test
    fun write_marketsTabEnabled_updatesFlow() {
        val store = openStore()

        store.marketsTabEnabled = false

        assertFalse(store.marketsTabEnabledFlow.value)
    }

    @Test
    fun setString_storedName_isReadBackAndNullClearsIt() {
        val store = openStore()

        store.setString("theme", "Dark")
        assertEquals("Dark", store.string("theme"))

        store.setString("theme", null)
        assertNull(store.string("theme"))
    }

    @Test
    fun stringFlow_afterSetString_updatesSynchronously() {
        val store = openStore()
        val flow = store.stringFlow("interval") { it ?: "default" }

        store.setString("interval", "midnight")

        assertEquals("midnight", flow.value)
    }

    @Test
    fun flagFlow_afterSetFlag_updatesSynchronouslyAndStartsAtDefault() {
        val store = openStore()
        val flow = store.flagFlow("buttons", true)
        assertTrue(flow.value)

        store.setFlag("buttons", false)

        assertFalse(flow.value)
    }

    @Test
    fun clearAll_cachedGenericFlows_returnToDefaults() {
        val store = openStore()
        val strings = store.stringFlow("interval") { it ?: "default" }
        val flags = store.flagFlow("buttons", true)
        store.setString("interval", "midnight")
        store.setFlag("buttons", false)

        store.clearAll()

        assertEquals("default", strings.value)
        assertTrue(flags.value)
        assertNull(store.string("interval"))
    }

    private fun assertDefaults(store: DesktopSettingsStore) {
        assertEquals(emptyList<String>(), store.marketSearchRecentCoinUids)
        assertNull(store.marketFavoritesSorting)
        assertNull(store.marketFavoritesPeriod)
        assertFalse(store.marketFavoritesShowSignals)
        assertFalse(store.chartIndicatorsEnabled)
        assertTrue(store.marketsTabEnabled)
        assertTrue(store.marketsTabEnabledFlow.value)
        assertNull(store.launchPage)
        assertNull(store.currentMarketTab)
        assertNull(store.baseCurrencyCode)
    }
}
