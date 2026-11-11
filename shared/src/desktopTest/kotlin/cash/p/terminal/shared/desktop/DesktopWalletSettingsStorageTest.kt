package cash.p.terminal.shared.desktop

import cash.p.terminal.entities.AppVersion
import cash.p.terminal.modules.amount.AmountInputType
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
    fun sendSettings_nothingStored_returnTheAndroidDefaults() {
        assertNull(storage.amountInputType)
        assertTrue(storage.rbfEnabled)
        assertFalse(storage.utxoExpertModeEnabled)
        assertFalse(storage.utxoExpertModeEnabledFlow.value)
        assertFalse(storage.swapMevProtectionEnabled)
        assertFalse(storage.transferPasscodeEnabled)
    }

    @Test
    fun sendSettings_written_areReadBackAndTheFlowFollows() {
        storage.amountInputType = AmountInputType.CURRENCY
        storage.rbfEnabled = false
        storage.utxoExpertModeEnabled = true
        storage.swapMevProtectionEnabled = true
        storage.transferPasscodeEnabled = true

        assertEquals(AmountInputType.CURRENCY, storage.amountInputType)
        assertFalse(storage.rbfEnabled)
        assertTrue(storage.utxoExpertModeEnabled)
        assertTrue(storage.utxoExpertModeEnabledFlow.value)
        assertTrue(storage.swapMevProtectionEnabled)
        assertTrue(storage.transferPasscodeEnabled)

        storage.amountInputType = null
        assertNull(storage.amountInputType)
    }

    @Test
    fun swapProviders_written_areReadBack() {
        assertEquals(emptySet(), storage.disabledProviderIds)
        assertNull(storage.backendProviders)

        storage.disabledProviderIds = setOf("oneinch", "changenow")
        storage.backendProviders = """[{"name":"changelly"}]"""

        assertEquals(setOf("oneinch", "changenow"), storage.disabledProviderIds)
        assertEquals("""[{"name":"changelly"}]""", storage.backendProviders)
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
    fun nftSettings_nothingStored_showCollectionsWithNoDismissedHints() {
        assertTrue(storage.showNftCollections)
        assertTrue(storage.showNftCollectionsFlow.value)
        assertEquals(emptySet(), storage.dismissedNftVersionHints)
    }

    @Test
    fun nftSettings_written_areReadBackAndTheFlowFollows() {
        storage.showNftCollections = false
        storage.dismissedNftVersionHints = setOf("account:W5")

        assertFalse(storage.showNftCollections)
        assertFalse(storage.showNftCollectionsFlow.value)
        assertEquals(setOf("account:W5"), storage.dismissedNftVersionHints)
    }

    @Test
    fun appVersions_corruptValue_isEmpty() {
        settingsStore.setString("appVersions", "{not json")

        assertEquals(emptyList(), storage.appVersions)
    }
}
