package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.NftChange
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftItemsState
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NftCollectionsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val wallet: Wallet = WalletFactory.previewWallet()
    private val owner = NftOwner("account", BlockchainType.Ton, "owner")
    private val otherOwner = NftOwner("other-account", BlockchainType.Ton, "other-owner")

    private val globalHidden = MutableStateFlow(false)
    private val walletHidden = MutableStateFlow(true)
    private val walletsFlow = MutableStateFlow(listOf(wallet))
    private val items = MutableStateFlow(NftItemsState(emptyList(), isLoaded = false))
    private val changes = MutableSharedFlow<NftChange>(extraBufferCapacity = 8)

    private val repository = mockk<NftCollectionsRepository> {
        coEvery { owners(listOf(wallet)) } returns setOf(owner)
        coEvery { owners(emptyList()) } returns setOf(otherOwner)
        every { itemsFlow(any()) } returns items
        every { changes(owner) } returns changes
        every { changes(otherOwner) } returns MutableSharedFlow()
        every { networkTitle(any()) } returns "TON"
        every { networkResumed(any()) } returns emptyFlow()
        coEvery { refresh(any(), any()) } just Runs
    }
    private val walletManager = mockk<IWalletManager> {
        every { activeWalletsFlow } returns walletsFlow
    }
    private val hiddenManager = mockk<IBalanceHiddenManager>(relaxed = true) {
        every { balanceHiddenFlow } returns globalHidden
        every { walletBalanceHiddenFlow(wallet.tokenQueryId) } returns walletHidden
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(source: NftCollectionSource = NftCollectionSource.AllWallets) =
        NftCollectionsViewModel(source, repository, walletManager, hiddenManager)

    private fun record(address: String) = NftItemRecord(
        accountId = owner.accountId,
        blockchainType = owner.blockchainType,
        ownerAddress = owner.address,
        address = address,
        position = 0,
        name = "NFT $address",
        description = null,
        realOwnerAddress = null,
        collectionAddress = null,
        collectionName = null,
        collectionDescription = null,
        trust = NftTrust.Whitelist,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    @Test
    fun hidden_singleWallet_followsWalletFlowNotGlobal() = runTest(dispatcher) {
        val viewModel = createViewModel(NftCollectionSource.SingleWallet(wallet))
        assertTrue(viewModel.uiState.hidden)

        globalHidden.value = true
        walletHidden.value = false

        assertFalse(viewModel.uiState.hidden)
    }

    @Test
    fun hidden_allWallets_followsGlobalFlowNotWallet() = runTest(dispatcher) {
        val viewModel = createViewModel(NftCollectionSource.AllWallets)
        assertFalse(viewModel.uiState.hidden)

        walletHidden.value = false
        globalHidden.value = true

        assertTrue(viewModel.uiState.hidden)
    }

    @Test
    fun toggleHidden_singleWallet_togglesWalletEyeOnly() = runTest(dispatcher) {
        createViewModel(NftCollectionSource.SingleWallet(wallet)).toggleHidden()

        verify(exactly = 1) { hiddenManager.toggleWalletBalanceHidden(wallet.tokenQueryId) }
        verify(exactly = 0) { hiddenManager.toggleBalanceHidden() }
    }

    @Test
    fun toggleHidden_allWallets_togglesGlobalEyeOnly() = runTest(dispatcher) {
        createViewModel(NftCollectionSource.AllWallets).toggleHidden()

        verify(exactly = 1) { hiddenManager.toggleBalanceHidden() }
        verify(exactly = 0) { hiddenManager.toggleWalletBalanceHidden(any()) }
    }

    @Test
    fun uiState_notLoadedAndNoCache_staysLoadingNotEmpty() = runTest(dispatcher) {
        val state = createViewModel().uiState

        assertTrue(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun uiState_cachePresent_showsItemsAndCount() = runTest(dispatcher) {
        val viewModel = createViewModel()

        items.value = NftItemsState(listOf(record("a"), record("b")), isLoaded = true)

        assertFalse(viewModel.uiState.isLoading)
        assertFalse(viewModel.uiState.isEmpty)
        assertEquals(2, viewModel.uiState.totalCount)
        assertEquals("TON", viewModel.uiState.networkTitle)
    }

    @Test
    fun uiState_loadedAndEmpty_isEmpty() = runTest(dispatcher) {
        val viewModel = createViewModel()

        items.value = NftItemsState(emptyList(), isLoaded = true)

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.isEmpty)
    }

    @Test
    fun init_ownersResolved_refreshesWithoutForce() = runTest(dispatcher) {
        createViewModel()

        coVerify(exactly = 1) { repository.refresh(setOf(owner), false) }
        coVerify(exactly = 0) { repository.refresh(any(), true) }
    }

    @Test
    fun kitAppeared_refreshesWithoutForce() = runTest(dispatcher) {
        createViewModel()

        changes.emit(NftChange.KitAppeared)

        coVerify(exactly = 2) { repository.refresh(setOf(owner), false) }
        coVerify(exactly = 0) { repository.refresh(any(), true) }
    }

    @Test
    fun networkResumed_emits_requestsNonForcedRefresh() = runTest(dispatcher) {
        val resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        every { repository.networkResumed(setOf(owner)) } returns resumed
        createViewModel()

        resumed.emit(Unit)

        coVerify(exactly = 2) { repository.refresh(setOf(owner), false) }
        coVerify(exactly = 0) { repository.refresh(any(), true) }
    }

    @Test
    fun newEvent_burst_refreshesOnceWithForceAfterDebounce() = runTest(dispatcher) {
        createViewModel()

        repeat(3) {
            changes.emit(NftChange.NewEvent)
            advanceTimeBy(500)
        }
        coVerify(exactly = 0) { repository.refresh(any(), true) }

        advanceTimeBy(1_600)

        coVerify(exactly = 1) { repository.refresh(setOf(owner), true) }
    }

    @Test
    fun refresh_pullToRefresh_forcesRefresh() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.refresh()

        coVerify(exactly = 1) { repository.refresh(setOf(owner), true) }
    }

    @Test
    fun onResume_ownersKnown_refreshesWithoutForce() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onResume()

        coVerify(exactly = 2) { repository.refresh(setOf(owner), false) }
    }

    @Test
    fun onResume_ownersNotKnownYet_doesNothing() = runTest(dispatcher) {
        coEvery { repository.owners(any()) } coAnswers { awaitCancellation() }

        createViewModel().onResume()

        coVerify(exactly = 0) { repository.refresh(any(), any()) }
    }

    @Test
    fun accountSwitch_cancelsPreviousChangesSubscription() = runTest(dispatcher) {
        var oldSubscriptionCancelled = false
        every { repository.changes(owner) } returns flow {
            try {
                awaitCancellation()
            } finally {
                oldSubscriptionCancelled = true
            }
        }
        createViewModel()
        assertFalse(oldSubscriptionCancelled)

        walletsFlow.value = emptyList()

        assertTrue(oldSubscriptionCancelled)
        coVerify(exactly = 1) { repository.refresh(setOf(otherOwner), false) }
    }

    @Test
    fun ownersChanged_differentAccount_dropsPreviousItemsBeforeNewQueryEmits() = runTest(dispatcher) {
        every { repository.itemsFlow(setOf(otherOwner)) } returns MutableSharedFlow()
        val viewModel = createViewModel()
        items.value = NftItemsState(listOf(record("a")), isLoaded = true)
        assertEquals(1, viewModel.uiState.totalCount)

        walletsFlow.value = emptyList()

        assertTrue(viewModel.uiState.items.isEmpty())
        assertTrue(viewModel.uiState.isLoading)
    }

    @Test
    fun ownersChanged_ownerStillPresent_keepsItsItems() = runTest(dispatcher) {
        val grownOwners = setOf(owner, otherOwner)
        val grownWallets = listOf(wallet, wallet)
        coEvery { repository.owners(grownWallets) } returns grownOwners
        every { repository.itemsFlow(grownOwners) } returns MutableSharedFlow()
        val viewModel = createViewModel()
        items.value = NftItemsState(listOf(record("a")), isLoaded = true)

        walletsFlow.value = grownWallets

        assertEquals(listOf("a"), viewModel.uiState.items.map { it.address })
        assertFalse(viewModel.uiState.isLoading)
    }
}
