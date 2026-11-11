package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.NftCollectionPrefKey
import cash.p.terminal.core.nft.NftCollectionPrefsRepository
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.collectionKey
import cash.p.terminal.core.nft.collectionPrefKey
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftLinkButton
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        coEvery { check(any()) } returns NftLiveCheck(NftTransferAvailability.Hidden)
        coEvery { domainExpiry(any()) } returns null
    }
    private val expiryTriggers = MutableSharedFlow<Unit>()
    private val repository = mockk<NftCollectionsRepository> {
        every { domainExpiryTriggers(any()) } returns expiryTriggers
        every { explorerUrl(any(), any()) } returns "https://tonviewer.com/nft/EQBx"
    }
    private val prefs = MutableStateFlow<Map<NftCollectionPrefKey, NftCollectionPrefRecord>>(emptyMap())
    private val prefsRepository = mockk<NftCollectionPrefsRepository>(relaxed = true) {
        every { prefsFlow(any()) } returns prefs
    }
    private var item = previewNftItem("EQBx")

    private fun createViewModel() = NftPreviewViewModel(
        NftCollectionSource.AllWallets, item, hiddenManager, availabilityProvider, repository, prefsRepository,
    )

    private fun pref(userTrust: NftUserTrust) = NftCollectionPrefRecord(
        accountId = item.accountId,
        blockchainType = item.blockchainType,
        collectionKey = item.collectionKey,
        hidden = false,
        userTrust = userTrust,
    )

    private fun setPref(userTrust: NftUserTrust) {
        prefs.value = mapOf(item.collectionPrefKey to pref(userTrust))
    }

    private fun viewModelWith(
        trust: NftTrust,
        availability: NftTransferAvailability? = null,
        buttons: List<NftLinkButton> = emptyList(),
    ): NftPreviewViewModel {
        item = previewNftItem("EQBx", trust = trust)
        availability?.let { coEvery { availabilityProvider.check(item) } returns NftLiveCheck(it, buttons) }
        return createViewModel().also { if (availability != null) it.onResume() }
    }

    private val available get() = NftTransferAvailability.Available(mockk<Wallet>(), null)

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
        coEvery { availabilityProvider.check(item) } returns NftLiveCheck(onSale)
        val viewModel = createViewModel()

        viewModel.onResume()

        assertEquals(onSale, viewModel.uiState.transferAvailability)
    }

    @Test
    fun onResume_secondResume_showsLoadingUntilNewResult() {
        val gate = CompletableDeferred<NftLiveCheck>()
        coEvery { availabilityProvider.check(item) } returns NftLiveCheck(NftTransferAvailability.Hidden) coAndThen {
            gate.await()
        }
        val viewModel = createViewModel()
        viewModel.onResume()
        assertEquals(NftTransferAvailability.Hidden, viewModel.uiState.transferAvailability)

        viewModel.onResume()

        assertEquals(NftTransferAvailability.Loading, viewModel.uiState.transferAvailability)
        coVerify(exactly = 2) { availabilityProvider.check(item) }
    }

    @Test
    fun hiddenFlow_changesAfterCheck_keepsTransferAvailability() {
        coEvery { availabilityProvider.check(item) } returns NftLiveCheck(NftTransferAvailability.Hidden)
        val viewModel = createViewModel()
        viewModel.onResume()

        hiddenState.value = false

        assertEquals(NftTransferAvailability.Hidden, viewModel.uiState.transferAvailability)
    }

    @Test
    fun init_balanceHidden_startsHiddenAndRevealable() {
        assertFlags(createViewModel().uiState, hidden = true, canReveal = true)
    }

    @Test
    fun init_balanceVisible_nothingToReveal() {
        hiddenState.value = false

        assertFlags(createViewModel().uiState, hidden = false, canReveal = false)
    }

    @Test
    fun onToggleReveal_balanceHidden_showsNft() {
        val viewModel = createViewModel()

        viewModel.onToggleReveal()

        assertFlags(viewModel.uiState, hidden = false, canReveal = true)
    }

    @Test
    fun onToggleReveal_twice_hidesNftAgain() {
        val viewModel = createViewModel()

        viewModel.onToggleReveal()
        viewModel.onToggleReveal()

        assertFlags(viewModel.uiState, hidden = true, canReveal = true)
    }

    @Test
    fun hiddenFlow_shownThenHiddenAfterReveal_hidesNftAgain() {
        val viewModel = createViewModel()
        viewModel.onToggleReveal()

        hiddenState.value = false
        hiddenState.value = true

        assertFlags(viewModel.uiState, hidden = true, canReveal = true)
    }

    @Test
    fun onResume_domainExpiryKnown_showsExpiry() {
        val known = expiry(1_000)
        coEvery { availabilityProvider.domainExpiry(item) } returns known
        val viewModel = createViewModel()

        viewModel.onResume()

        assertEquals(known, viewModel.uiState.domainExpiry)
    }

    @Test
    fun onResume_expiryTriggerEmitted_rereadsExpiry() = runTest(UnconfinedTestDispatcher()) {
        val before = expiry(1_000)
        val after = expiry(1_000 + 31_536_000)
        coEvery { availabilityProvider.domainExpiry(item) } returns before andThen after
        val viewModel = createViewModel()
        viewModel.onResume()
        assertEquals(before, viewModel.uiState.domainExpiry)

        expiryTriggers.emit(Unit)

        assertEquals(after, viewModel.uiState.domainExpiry)
    }

    @Test
    fun onResume_triggerAfterFailedCheck_enablesRenewButton() = runTest(UnconfinedTestDispatcher()) {
        val failed = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.CheckFailed)
        coEvery { availabilityProvider.check(item) } returns NftLiveCheck(failed) andThen
            NftLiveCheck(NftTransferAvailability.Available(mockk<Wallet>(), null))
        coEvery { availabilityProvider.domainExpiry(item) } returns null andThen expiry(1_000)
        val viewModel = createViewModel()
        viewModel.onResume()

        expiryTriggers.emit(Unit)

        assertEquals(RenewButtonState.Enabled, viewModel.uiState.renewButton)
    }

    @Test
    fun onResume_secondResume_stopsListeningToPreviousTriggers() = runTest(UnconfinedTestDispatcher()) {
        coEvery { availabilityProvider.domainExpiry(item) } returns expiry(1_000)
        val viewModel = createViewModel()
        viewModel.onResume()
        viewModel.onResume()

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

    private fun assertFlags(state: NftPreviewUiState, hidden: Boolean, canReveal: Boolean) {
        assertEquals(hidden, state.hidden)
        assertEquals(canReveal, state.canReveal)
    }

    @Test
    fun menuActions_whitelist_copiesAddressAndCannotBurn() {
        val viewModel = viewModelWith(NftTrust.Whitelist, available)

        assertEquals(
            listOf(NftAction.HideCollection, NftAction.OpenExplorer, NftAction.CopyAddress),
            viewModel.uiState.menuActions,
        )
    }

    @Test
    fun menuActions_graylistAvailable_offersReportAndBurn() {
        val viewModel = viewModelWith(NftTrust.Graylist, available)

        assertEquals(
            listOf(NftAction.HideCollection, NftAction.HideAndReport, NftAction.OpenExplorer, NftAction.Burn),
            viewModel.uiState.menuActions,
        )
    }

    @Test
    fun menuActions_noneWithoutCollection_hidesTheNftItself() {
        item = previewNftItem("EQBx", trust = NftTrust.None).copy(collectionAddress = null)

        val actions = createViewModel().uiState.menuActions

        assertEquals(NftAction.HideNft, actions.first())
        assertFalse(NftAction.HideCollection in actions)
    }

    @Test
    fun menuActions_trustOverrideOnNone_behavesLikeWhitelist() {
        val viewModel = viewModelWith(NftTrust.None, available)

        setPref(NftUserTrust.Trust)

        assertEquals(
            listOf(NftAction.HideCollection, NftAction.OpenExplorer, NftAction.CopyAddress),
            viewModel.uiState.menuActions,
        )
    }

    @Test
    fun menuActions_unavailableOrLoadingTransfer_hasNoBurn() {
        val onSale = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale)

        listOf(onSale, NftTransferAvailability.Loading, NftTransferAvailability.Hidden).forEach {
            assertFalse(NftAction.Burn in viewModelWith(NftTrust.None, it).uiState.menuActions)
        }
    }

    @Test
    fun showSpamPlate_noneTrust_shownUntilNotSpamObserved() {
        val viewModel = viewModelWith(NftTrust.None)
        assertTrue(viewModel.uiState.showSpamPlate)

        viewModel.onNotSpam()
        setPref(NftUserTrust.Trust)

        assertFalse(viewModel.uiState.showSpamPlate)
        verify { prefsRepository.markNotSpam(item) }
    }

    @Test
    fun showSpamPlate_graylistOrWhitelist_notShown() {
        assertFalse(viewModelWith(NftTrust.Graylist).uiState.showSpamPlate)
        assertFalse(viewModelWith(NftTrust.Whitelist).uiState.showSpamPlate)
    }

    @Test
    fun onReportSpam_marksSpamAndDropsPlateAndReportAction() {
        val viewModel = viewModelWith(NftTrust.None)

        viewModel.onReportSpam()
        setPref(NftUserTrust.Spam)

        verify { prefsRepository.markSpam(item) }
        assertFalse(viewModel.uiState.showSpamPlate)
        assertFalse(NftAction.HideAndReport in viewModel.uiState.menuActions)
    }

    @Test
    fun onHideAndOnUndoHide_writeToRepository() {
        val viewModel = viewModelWith(NftTrust.Whitelist)

        viewModel.onHide()
        viewModel.onUndoHide()

        verify { prefsRepository.setHidden(item, true) }
        verify { prefsRepository.setHidden(item, false) }
    }

    @Test
    fun linkButtonsState_perAvailability_enabledOnlyWhenSendable() {
        val buttons = listOf(NftLinkButton("Claim", "https://example.com"))
        val onSale = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale)
        fun state(availability: NftTransferAvailability, with: List<NftLinkButton> = buttons) =
            viewModelWith(NftTrust.Whitelist, availability, with).uiState.linkButtonsState

        assertEquals(LinkButtonsState.Enabled, state(available))
        assertEquals(LinkButtonsState.Disabled, state(onSale))
        assertEquals(LinkButtonsState.Hidden, state(NftTransferAvailability.Hidden))
        assertEquals(LinkButtonsState.Hidden, state(available, with = emptyList()))
    }

    @Test
    fun linkButtonsState_recheckRunning_disabledWithPreviousButtons() {
        val buttons = listOf(NftLinkButton("Claim", "https://example.com"))
        val viewModel = viewModelWith(NftTrust.Whitelist, available, buttons)
        coEvery { availabilityProvider.check(item) } coAnswers { CompletableDeferred<NftLiveCheck>().await() }

        viewModel.onResume()

        assertEquals(buttons, viewModel.uiState.linkButtons)
        assertEquals(LinkButtonsState.Disabled, viewModel.uiState.linkButtonsState)
    }

    @Test
    fun canOpenConfirmedLink_recheckRunning_acceptsOnlyCurrentButtons() {
        val buttons = listOf(NftLinkButton("Claim", "https://example.com"))
        hiddenState.value = false
        val viewModel = viewModelWith(NftTrust.None, available, buttons)
        coEvery { availabilityProvider.check(item) } coAnswers { CompletableDeferred<NftLiveCheck>().await() }

        viewModel.onResume()

        assertTrue(viewModel.uiState.canOpenConfirmedLink("https://example.com"))
        assertFalse(viewModel.uiState.canOpenConfirmedLink("https://other.example"))
    }

    @Test
    fun canOpenConfirmedLink_afterConfirmation_onlyHiddenBalanceOrGoneButtonRejects() {
        val url = "https://example.com"
        val shown = NftPreviewUiState(transferAvailability = available, linkButtons = listOf(NftLinkButton("Claim", url)))
        val onSale = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale)

        assertTrue(shown.canOpenConfirmedLink(url))
        assertTrue(shown.copy(transferAvailability = onSale).canOpenConfirmedLink(url))
        assertFalse(shown.copy(hidden = true).canOpenConfirmedLink(url))
        assertFalse(shown.copy(linkButtons = emptyList()).canOpenConfirmedLink(url))
    }

    @Test
    fun isSuspicious_byProviderTrustAndUserMarks() {
        assertTrue(viewModelWith(NftTrust.None).uiState.isSuspicious)
        assertTrue(viewModelWith(NftTrust.Blacklist).uiState.isSuspicious)
        assertFalse(viewModelWith(NftTrust.Graylist).uiState.isSuspicious)

        val spam = viewModelWith(NftTrust.Whitelist)
        setPref(NftUserTrust.Spam)
        assertTrue(spam.uiState.isSuspicious)

        val trusted = viewModelWith(NftTrust.None)
        setPref(NftUserTrust.Trust)
        assertFalse(trusted.uiState.isSuspicious)
    }

    private fun stateWith(availability: NftTransferAvailability) =
        NftPreviewUiState(transferAvailability = availability, domainExpiry = expiry(1_000))
}
