package cash.p.terminal.modules.evmnetwork.addrpc

import cash.p.terminal.core.Caution
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.entities.EvmSyncSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class AddRpcViewModelDesktopTest {

    private val blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
    private val evmSyncSourceManager = mockk<EvmSyncSourceManager>(relaxed = true) {
        every { allSyncSources(BlockchainType.Ethereum) } returns listOf(syncSource(EXISTING_URL))
    }
    private val viewModel = AddRpcViewModel(blockchain, evmSyncSourceManager)

    @Test
    fun onAddClick_httpUrl_showsInvalidUrlError() {
        viewModel.onEnterRpcUrl("http://rpc.example.com")
        viewModel.onAddClick()

        assertEquals(Caution.Type.Error, viewModel.viewState.urlCaution?.type)
        verify(exactly = 0) { evmSyncSourceManager.saveSyncSource(any(), any(), any()) }
    }

    @Test
    fun onAddClick_existingUrl_showsDuplicateWarning() {
        viewModel.onEnterRpcUrl(EXISTING_URL)
        viewModel.onAddClick()

        assertEquals(Caution.Type.Warning, viewModel.viewState.urlCaution?.type)
        verify(exactly = 0) { evmSyncSourceManager.saveSyncSource(any(), any(), any()) }
    }

    @Test
    fun onAddClick_newWssUrl_savesAndCloses() {
        viewModel.onEnterRpcUrl(" wss://ws.example.com ")
        viewModel.onEnterBasicAuth("user:pass")
        viewModel.onAddClick()

        assertTrue(viewModel.viewState.closeScreen)
        verify { evmSyncSourceManager.saveSyncSource(BlockchainType.Ethereum, "wss://ws.example.com", "user:pass") }
    }

    private fun syncSource(url: String) = mockk<EvmSyncSource> {
        every { uri } returns URI(url)
    }

    private companion object {
        const val EXISTING_URL = "https://rpc.example.com"
    }
}
