package cash.p.terminal.modules.zcashnetwork

import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashNetworkViewModelTest {

    private var storedUrl: String? = null
    private var storedCustom: List<String> = emptyList()
    private val storage = mockk<BlockchainSettingsStorage> {
        every { zcashServerUrl() } answers { storedUrl }
        every { saveZcashServerUrl(any()) } answers { storedUrl = firstArg() }
        every { zcashCustomServers() } answers { storedCustom }
        every { saveZcashCustomServers(any()) } answers { storedCustom = firstArg() }
    }
    private val manager = ZcashServerManager(storage)
    private val customUrl = "https://custom.example.com:443"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_customServerSelected_marksOnlyCurrentAsSelected() {
        storedCustom = listOf(customUrl)
        storedUrl = customUrl

        val state = ZcashNetworkViewModel(manager).uiState

        assertEquals(manager.defaultServers.map { it.url }, state.defaultItems.map { it.url })
        assertEquals(listOf(false, false, false, false, false), state.defaultItems.map { it.selected })
        assertEquals(
            listOf(ZcashNetworkViewModel.ViewItem("custom.example.com:443", customUrl, selected = true)),
            state.customItems
        )
    }

    @Test
    fun onSelect_otherServer_persistsSelectionAndMovesCheckmark() {
        val viewModel = ZcashNetworkViewModel(manager)
        val target = manager.defaultServers[3].url

        viewModel.onSelect(target)

        assertEquals(target, storedUrl)
        assertEquals(listOf(target), viewModel.uiState.defaultItems.filter { it.selected }.map { it.url })
    }

    @Test
    fun onDelete_unselectedCustomServer_removesRowFromList() {
        storedCustom = listOf(customUrl)
        val viewModel = ZcashNetworkViewModel(manager)
        assertEquals(1, viewModel.uiState.customItems.size)

        viewModel.onDelete(customUrl)

        assertEquals(emptyList<ZcashNetworkViewModel.ViewItem>(), viewModel.uiState.customItems)
    }
}
