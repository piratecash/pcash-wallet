package cash.p.terminal.modules.nft

import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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

    private val availabilityProvider = mockk<NftTransferAvailabilityProvider>()
    private val item = mockk<NftItemRecord>()

    private fun createViewModel() =
        NftPreviewViewModel(NftCollectionSource.AllWallets, hiddenManager, availabilityProvider)

    @Test
    fun onResume_checkCompletes_showsResult() {
        val onSale = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale)
        coEvery { availabilityProvider.check(item) } returns onSale
        val viewModel = createViewModel()

        viewModel.onResume(item)

        assertEquals(onSale, viewModel.uiState.transferAvailability)
    }

    @Test
    fun onResume_secondResume_showsLoadingUntilNewResult() {
        val gate = CompletableDeferred<NftTransferAvailability>()
        coEvery { availabilityProvider.check(item) } returns NftTransferAvailability.Hidden coAndThen { gate.await() }
        val viewModel = createViewModel()
        viewModel.onResume(item)
        assertEquals(NftTransferAvailability.Hidden, viewModel.uiState.transferAvailability)

        viewModel.onResume(item)

        assertEquals(NftTransferAvailability.Loading, viewModel.uiState.transferAvailability)
        coVerify(exactly = 2) { availabilityProvider.check(item) }
    }

    @Test
    fun hiddenFlow_changesAfterCheck_keepsTransferAvailability() {
        coEvery { availabilityProvider.check(item) } returns NftTransferAvailability.Hidden
        val viewModel = createViewModel()
        viewModel.onResume(item)

        hiddenState.value = false

        assertEquals(NftTransferAvailability.Hidden, viewModel.uiState.transferAvailability)
    }

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
