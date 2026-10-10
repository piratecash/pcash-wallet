package cash.p.terminal.modules.zcashnetwork

import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.core.managers.AddResult
import cash.p.terminal.core.managers.ZcashServer
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.modules.zcashnetwork.addserver.AddZcashServerViewModel
import cash.p.terminal.strings.helpers.Translator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddZcashServerViewModelTest {

    private val manager = mockk<ZcashServerManager>()
    private val viewModel = AddZcashServerViewModel(manager)

    @Before
    fun setUp() {
        // Translator has no Android context here; the resource id itself identifies the message.
        mockkObject(Translator)
        every { Translator.getString(any<Int>()) } answers { firstArg<Int>().toString() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun onAddClick_added_closesScreenWithoutCaution() {
        every { manager.addCustom("https://a.example.com") } returns
            AddResult.Added(ZcashServer("a.example.com:443", "https://a.example.com:443", isCustom = true))
        viewModel.onUrlChange("https://a.example.com")

        viewModel.onAddClick()

        assertTrue(viewModel.uiState.closeScreen)
        assertNull(viewModel.uiState.caution)
    }

    @Test
    fun onAddClick_duplicate_showsUrlExistsWarning() {
        every { manager.addCustom(any()) } returns AddResult.Duplicate
        viewModel.onUrlChange("https://zec.rocks")

        viewModel.onAddClick()

        assertFalse(viewModel.uiState.closeScreen)
        assertEquals(
            Caution(R.string.AddEvmSyncSource_Warning_UrlExists.toString(), Caution.Type.Warning),
            viewModel.uiState.caution
        )
    }

    @Test
    fun onAddClick_invalid_showsInvalidUrlError() {
        every { manager.addCustom(any()) } returns AddResult.Invalid
        viewModel.onUrlChange("ftp://nope")

        viewModel.onAddClick()

        assertFalse(viewModel.uiState.closeScreen)
        assertEquals(
            Caution(R.string.zcash_network_invalid_url.toString(), Caution.Type.Error),
            viewModel.uiState.caution
        )
    }

    @Test
    fun onUrlChange_afterError_clearsCaution() {
        every { manager.addCustom(any()) } returns AddResult.Invalid
        viewModel.onAddClick()

        viewModel.onUrlChange("https://fixed.example.com")

        assertNull(viewModel.uiState.caution)
    }
}
