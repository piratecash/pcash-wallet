package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.ThorchainKitManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThorchainBlockchainStatusProviderTest {

    private fun provider(
        blockchainType: BlockchainType,
        kitStarted: Boolean,
        statusInfo: Map<String, Any>?,
    ) = ThorchainBlockchainStatusProvider(
        mockk<ThorchainKitManager> {
            every { this@mockk.blockchainType } returns blockchainType
            every { kitStartedFlow } returns MutableStateFlow(kitStarted)
            every { this@mockk.statusInfo } returns statusInfo
        }
    )

    @Test
    fun getStatus_startedMayaKit_reportsThorchainKitVersionAndSyncState() {
        val provider = provider(BlockchainType.Mayachain, kitStarted = true, mapOf("Sync State" to "Synced"))

        val section = provider.getStatus().sections.single()

        assertEquals(BuildConfig.THORCHAIN_KIT_VERSION, provider.kitVersion)
        assertEquals(BlockchainType.Mayachain.uid, provider.logFilterTag)
        assertTrue(provider.kitStarted)
        assertEquals("Maya", section.title)
        assertEquals(listOf(StatusItem.KeyValue("Sync State", "Synced")), section.items)
    }

    @Test
    fun getStatus_kitNotCreated_reportsNotStartedWithoutSections() {
        val provider = provider(BlockchainType.Thorchain, kitStarted = false, statusInfo = null)

        assertFalse(provider.kitStarted)
        assertTrue(provider.getStatus().sections.isEmpty())
    }
}
