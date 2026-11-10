package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

    private val availabilityProvider = mockk<NftTransferAvailabilityProvider> {
        coEvery { check(any()) } returns NftTransferAvailability.Hidden
        coEvery { domainExpiry(any()) } returns null
    }
    private val expiryTriggers = MutableSharedFlow<Unit>()
    private val repository = mockk<NftCollectionsRepository> {
        every { domainExpiryTriggers(any()) } returns expiryTriggers
    }
    private val item = previewNftItem("EQBx")

    private fun createViewModel() =
        NftPreviewViewModel(NftCollectionSource.AllWallets, hiddenManager, availabilityProvider, repository)

    private fun expiry(expiresAt: Long) = NftDomainExpiry(
        owner = NftOwner(item.accountId, BlockchainType.Ton, item.ownerAddress),
        nftAddress = item.address,
        name = "piratecash.ton",
        expiresAt = expiresAt,
        isOnSale = false,
    )

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

    @Test
    fun onResume_domainExpiryKnown_showsExpiry() {
        val known = expiry(1_000)
        coEvery { availabilityProvider.domainExpiry(item) } returns known
        val viewModel = createViewModel()

        viewModel.onResume(item)

        assertEquals(known, viewModel.uiState.domainExpiry)
    }

    @Test
    fun onResume_expiryTriggerEmitted_rereadsExpiry() = runTest(UnconfinedTestDispatcher()) {
        val before = expiry(1_000)
        val after = expiry(1_000 + 31_536_000)
        coEvery { availabilityProvider.domainExpiry(item) } returns before andThen after
        val viewModel = createViewModel()
        viewModel.onResume(item)
        assertEquals(before, viewModel.uiState.domainExpiry)

        expiryTriggers.emit(Unit)

        assertEquals(after, viewModel.uiState.domainExpiry)
    }

    @Test
    fun onResume_triggerAfterFailedCheck_enablesRenewButton() = runTest(UnconfinedTestDispatcher()) {
        val failed = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.CheckFailed)
        coEvery { availabilityProvider.check(item) } returns failed andThen
            NftTransferAvailability.Available(mockk<Wallet>(), null)
        coEvery { availabilityProvider.domainExpiry(item) } returns null andThen expiry(1_000)
        val viewModel = createViewModel()
        viewModel.onResume(item)

        expiryTriggers.emit(Unit)

        assertEquals(RenewButtonState.Enabled, viewModel.uiState.renewButton)
    }

    @Test
    fun onResume_secondResume_stopsListeningToPreviousTriggers() = runTest(UnconfinedTestDispatcher()) {
        coEvery { availabilityProvider.domainExpiry(item) } returns expiry(1_000)
        val viewModel = createViewModel()
        viewModel.onResume(item)
        viewModel.onResume(item)

        expiryTriggers.emit(Unit)

        coVerify(exactly = 3) { availabilityProvider.domainExpiry(item) }
    }

    @Test
    fun renewButton_expiryUnknown_isHidden() {
        val state = NftPreviewUiState(transferAvailability = NftTransferAvailability.Available(mockk<Wallet>(), null))

        assertEquals(RenewButtonState.Hidden, state.renewButton)
    }

    @Test
    fun renewButton_transferAvailable_isEnabled() {
        val state = stateWith(NftTransferAvailability.Available(mockk<Wallet>(), null))

        assertEquals(RenewButtonState.Enabled, state.renewButton)
    }

    @Test
    fun renewButton_transferAvailableButDomainListedSince_isDisabled() {
        val state = NftPreviewUiState(
            transferAvailability = NftTransferAvailability.Available(mockk<Wallet>(), null),
            domainExpiry = expiry(1_000).copy(isOnSale = true),
        )

        assertEquals(RenewButtonState.Disabled, state.renewButton)
    }

    @Test
    fun renewButton_transferHiddenOrWatchOnly_isHidden() {
        val watchOnly = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.WatchOnly)

        assertEquals(RenewButtonState.Hidden, stateWith(NftTransferAvailability.Hidden).renewButton)
        assertEquals(RenewButtonState.Hidden, stateWith(watchOnly).renewButton)
    }

    @Test
    fun renewButton_transferLoadingOrBlocked_isDisabled() {
        val onSale = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale)
        val notOwner = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.NotOwner)
        val checkFailed = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.CheckFailed)

        listOf(NftTransferAvailability.Loading, onSale, notOwner, checkFailed).forEach {
            assertEquals(RenewButtonState.Disabled, stateWith(it).renewButton)
        }
    }

    private fun stateWith(availability: NftTransferAvailability) =
        NftPreviewUiState(transferAvailability = availability, domainExpiry = expiry(1_000))
}
