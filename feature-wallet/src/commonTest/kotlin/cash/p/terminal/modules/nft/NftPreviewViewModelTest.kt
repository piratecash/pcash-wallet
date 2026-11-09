package cash.p.terminal.modules.nft

import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NftPreviewViewModelTest {

    private val hiddenState = MutableStateFlow(true)
    private val hiddenManager = mockk<IBalanceHiddenManager>(relaxed = true) {
        every { balanceHiddenFlow } returns hiddenState
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = NftPreviewViewModel(NftCollectionSource.AllWallets, hiddenManager)

    @Test
    fun init_balanceHidden_startsHiddenAndRevealable() {
        assertEquals(NftPreviewUiState(hidden = true, canReveal = true), createViewModel().uiState)
    }

    @Test
    fun init_balanceVisible_nothingToReveal() {
        hiddenState.value = false

        assertEquals(NftPreviewUiState(hidden = false, canReveal = false), createViewModel().uiState)
    }

    @Test
    fun onToggleReveal_balanceHidden_showsNft() {
        val viewModel = createViewModel()

        viewModel.onToggleReveal()

        assertEquals(NftPreviewUiState(hidden = false, canReveal = true), viewModel.uiState)
    }

    @Test
    fun onToggleReveal_twice_hidesNftAgain() {
        val viewModel = createViewModel()

        viewModel.onToggleReveal()
        viewModel.onToggleReveal()

        assertEquals(NftPreviewUiState(hidden = true, canReveal = true), viewModel.uiState)
    }

    @Test
    fun hiddenFlow_shownThenHiddenAfterReveal_hidesNftAgain() {
        val viewModel = createViewModel()
        viewModel.onToggleReveal()

        hiddenState.value = false
        hiddenState.value = true

        assertEquals(NftPreviewUiState(hidden = true, canReveal = true), viewModel.uiState)
    }
}
