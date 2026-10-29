package cash.p.terminal.modules.multiswap.providersettings

import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_swap_24
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapProvidersSettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val disabledIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    private val registry = mockk<SwapProvidersRegistry> {
        every { providers } returns listOf(
            provider("exolix", "Exolix"),
            provider("quickex", "Quickex"),
        )
    }
    private val repository = mockk<SwapProvidersRepository>(relaxed = true) {
        every { disabledIds } returns disabledIdsFlow
        every { isDisabled(any()) } returns false
        every { isMandatory(any()) } returns false
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun createState_registryProviders_listsEveryProvider() {
        val viewModel = SwapProvidersSettingsViewModel(registry, repository)

        assertEquals(listOf("exolix", "quickex"), viewModel.uiState.items.map { it.id })
    }

    private fun provider(providerId: String, providerTitle: String): IMultiSwapProvider =
        mockk(relaxed = true) {
            every { id } returns providerId
            every { title } returns providerTitle
            every { icon } returns Res.drawable.ic_swap_24
        }
}
