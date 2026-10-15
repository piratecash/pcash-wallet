package cash.p.terminal.shared.desktop.vault

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.entities.SyncerState
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.shared.desktop.DesktopChartIndicatorStorage
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import com.google.gson.Gson
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DesktopLanguageManager
import io.horizontalsystems.core.DesktopSettingsStore
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopVaultWipeTest {

    private val fixture = DesktopVaultFixture()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, scope)
    private val publicCache = File(fixture.dataDirectory, "market-http-cache").apply { mkdirs() }

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun wipe_unlockedVault_erasesTheDataAndResetsTheOpenStores() = runTest {
        val settingsStore = DesktopSettingsStore(File(fixture.dataDirectory, "settings.preferences_pb"), scope)
        val favoritesManager = favoritesManager()
        val currencyManager = DefaultCurrencyManager(settingsStore)
        val indicatorStorage = DesktopChartIndicatorStorage(
            File(fixture.dataDirectory, "indicators.preferences_pb"),
            Gson(),
            dispatcherProvider,
        )
        val chartIndicatorManager = ChartIndicatorManager(indicatorStorage, settingsStore, dispatcherProvider)
        val languageManager = DesktopLanguageManager(settingsStore)
        val systemLocaleTag = Locale.getDefault().toLanguageTag()
        val vault = vault(settingsStore, favoritesManager, currencyManager, chartIndicatorManager, languageManager)

        vault.create(password())
        session(vault).database.syncerStateDao().insert(SyncerState("height", "42"))
        settingsStore.launchPage = "market"
        currencyManager.setBaseCurrencyCode("EUR")
        favoritesManager.add("bitcoin")
        languageManager.currentLocaleTag = "ru"
        indicatorStorage.disable(ChartIndicatorSetting.defaults().first().id)

        assertEquals(WipeOutcome.SUCCESS, vault.wipe())

        assertEquals(DesktopVaultState.NoVault, vault.state.value)
        assertFalse(fixture.files.vaultFile.exists())
        assertFalse(fixture.files.databaseFile.exists())
        assertEquals(null, settingsStore.launchPage)
        assertEquals(emptySet(), favoritesManager.favoriteCoinUids.first())
        assertEquals(DefaultCurrencyManager.DEFAULT_CURRENCY_CODE, currencyManager.baseCurrency.code)
        assertEquals(
            ChartIndicatorSetting.defaults().map { it.id to it.enabled },
            indicatorStorage.allFlow().first().map { it.id to it.enabled },
        )
        assertEquals(systemLocaleTag, languageManager.currentLocaleTag)
        assertEquals(systemLocaleTag, Locale.getDefault().toLanguageTag())
        assertTrue(publicCache.exists())

        assertEquals(CreateOutcome.SUCCESS, vault.create(password()))
        assertTrue(fixture.files.vaultFile.exists())
    }

    @Test
    fun wipe_orphanSlotTemp_deletesItToo() = runTest {
        val vault = fixture.vault()
        vault.create(password())
        val orphan = File(fixture.dataDirectory, "vault.pcv.123.tmp").apply { writeBytes(byteArrayOf(1)) }

        assertEquals(WipeOutcome.SUCCESS, vault.wipe())

        assertFalse(orphan.exists())
    }

    @Test
    fun wipe_dataDirectoryCannotBeListed_failsWithoutClaimingTheSlotsAreGone() = runTest {
        val directory = fixture.dataDirectory.toPath()
        assumeTrue(Files.getFileAttributeView(directory, PosixFileAttributeView::class.java) != null)
        val vault = fixture.vault()
        vault.create(password())
        val orphan = File(fixture.dataDirectory, "vault.pcv.123.tmp").apply { writeBytes(byteArrayOf(1)) }
        val permissions = Files.getPosixFilePermissions(directory)
        Files.setPosixFilePermissions(
            directory,
            setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
        )

        try {
            assertEquals(WipeOutcome.FAILED, vault.wipe())

            assertEquals(DesktopVaultState.Locked, vault.state.value)
            assertTrue(orphan.exists())
        } finally {
            Files.setPosixFilePermissions(directory, permissions)
        }

        assertEquals(WipeOutcome.SUCCESS, vault.wipe())
        assertFalse(orphan.exists())
    }

    @Test
    fun wipe_slotFileCannotBeDeleted_failsAndStaysLocked() = runTest {
        // A non-empty directory in the slot file's place makes File.delete fail on every OS.
        File(fixture.files.vaultFile, "content").apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1))
        val vault = fixture.vault()

        assertEquals(WipeOutcome.FAILED, vault.wipe())

        assertEquals(DesktopVaultState.Locked, vault.state.value)
    }

    @Test
    fun wipe_settingsResetFails_staysLockedAndTheRetryFinishesTheReset() = runTest {
        val settingsStore = mockk<DesktopSettingsStore>(relaxed = true)
        every { settingsStore.clearAll() } throws IOException("datastore is busy") andThenJust Runs
        val vault = vault(settingsStore = settingsStore)
        vault.create(password())

        assertEquals(WipeOutcome.FAILED, vault.wipe())
        assertEquals(DesktopVaultState.Locked, vault.state.value)

        assertEquals(WipeOutcome.SUCCESS, vault.wipe())
        assertEquals(DesktopVaultState.NoVault, vault.state.value)
    }

    private fun favoritesManager() = MarketFavoritesManager(
        PreferenceDataStoreFactory.create(scope = scope) { File(fixture.dataDirectory, "favorites.preferences_pb") },
        mockk(relaxed = true),
    )

    private fun vault(
        settingsStore: DesktopSettingsStore = mockk(relaxed = true),
        favoritesManager: MarketFavoritesManager = mockk(relaxed = true),
        currencyManager: CurrencyManager = mockk(relaxed = true),
        chartIndicatorManager: ChartIndicatorManager = mockk(relaxed = true),
        languageManager: DesktopLanguageManager = mockk(relaxed = true),
    ) = DesktopVault(
        files = fixture.files,
        passwordVault = PasswordVault(fixture.files.vaultFile),
        lockoutManager = fixture.lockoutManager,
        preferencesReset = DesktopPreferencesReset(
            settingsStore = settingsStore,
            favoritesManager = favoritesManager,
            currencyManager = currencyManager,
            chartIndicatorManager = chartIndicatorManager,
            languageManager = languageManager,
        ),
        dispatcherProvider = dispatcherProvider,
        driverFactory = DefaultSqlCipherDriverFactory(),
    )

    private fun session(vault: DesktopVault): DesktopStorageSession =
        (vault.state.value as DesktopVaultState.Unlocked).session
}
