package cash.p.terminal.shared.desktop

import cash.p.terminal.entities.AppVersion
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWalletSettingsStorageTest {

    private val directory = Files.createTempDirectory("wallet-settings-store").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsStore = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    private val storage = DesktopWalletSettingsStorage(settingsStore)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun releaseNotes_nothingStored_showsAfterUpdateAndHasNoShownVersion() {
        assertTrue(storage.showChangelogAfterUpdate)
        assertNull(storage.changelogShownForAppVersion)
    }

    @Test
    fun releaseNotes_written_isReadBack() {
        storage.showChangelogAfterUpdate = false
        storage.changelogShownForAppVersion = "1.2.3"

        assertFalse(storage.showChangelogAfterUpdate)
        assertEquals("1.2.3", storage.changelogShownForAppVersion)
    }

    @Test
    fun appVersions_nothingStored_isEmpty() {
        assertEquals(emptyList(), storage.appVersions)
    }

    @Test
    fun appVersions_written_isReadBack() {
        val versions = listOf(AppVersion("1.0.0", 10L), AppVersion("1.1.0", 20L))

        storage.appVersions = versions

        assertEquals(versions, storage.appVersions)
    }

    @Test
    fun appVersions_corruptValue_isEmpty() {
        settingsStore.setString("appVersions", "{not json")

        assertEquals(emptyList(), storage.appVersions)
    }
}
