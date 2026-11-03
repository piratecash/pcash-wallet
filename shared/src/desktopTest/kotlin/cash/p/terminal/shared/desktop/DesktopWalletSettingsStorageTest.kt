package cash.p.terminal.shared.desktop

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

    private val directory = Files.createTempDirectory("wallet-settings").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = DesktopWalletSettingsStorage(
        DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    )

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
}
