package cash.p.terminal.modules.nft

import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.nft.DOMAIN_RENEW_SOON_DAYS
import cash.p.terminal.core.nft.NftChange
import cash.p.terminal.core.nft.NftCollectionPrefKey
import cash.p.terminal.core.nft.NftCollectionPrefsRepository
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftItemsState
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
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
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    private val prefs = MutableStateFlow<Map<NftCollectionPrefKey, NftCollectionPrefRecord>>(emptyMap())
    private val domainTriggers = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val showNft = MutableStateFlow(true)
    private val settings = mockk<BalanceSettingsStorage> {
        every { showNftCollectionsFlow } returns showNft
    }

    private val repository = mockk<NftCollectionsRepository> {
        coEvery { owners(listOf(wallet)) } returns setOf(owner)
        coEvery { owners(emptyList()) } returns setOf(otherOwner)
        every { ownersFlow(any()) } answers { callOriginal() }
        every { itemsFlow(any()) } returns items
        every { changes(owner) } returns changes
        every { changes(otherOwner) } returns MutableSharedFlow()
        every { networkTitle(any()) } returns "TON"
        every { networkResumed(any()) } returns emptyFlow()
        coEvery { refresh(any(), any()) } just Runs
        every { supportsSection(any()) } returns true
        every { domainExpiryTriggers(any()) } returns domainTriggers
        every { expiringDomainsFlow(any(), any(), any()) } answers { callOriginal() }
        coEvery { expiringDomains(any(), any()) } returns emptyList()
    }
    private val prefsRepository = mockk<NftCollectionPrefsRepository> {
        every { prefsFlow(any()) } returns prefs
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
        NftCollectionsViewModel(source, repository, prefsRepository, walletManager, hiddenManager, settings)

    private fun pref(address: String, hidden: Boolean = false, userTrust: NftUserTrust = NftUserTrust.None) =
        NftCollectionPrefRecord(owner.accountId, owner.blockchainType, address, hidden, userTrust)
            .let { NftCollectionPrefKey(it.blockchainType, it.collectionKey) to it }

    private fun record(address: String, trust: NftTrust = NftTrust.Whitelist, collection: String? = null) = NftItemRecord(
        accountId = owner.accountId,
        blockchainType = owner.blockchainType,
        ownerAddress = owner.address,
        address = address,
        position = 0,
        name = "NFT $address",
        description = null,
        realOwnerAddress = null,
        collectionAddress = collection,
        collectionName = null,
        collectionDescription = null,
        trust = trust,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    private fun domain(address: String) = NftDomainExpiry(owner, address, "$address.ton", expiresAt = 0, isOnSale = false)

    private fun expiringReturns(domains: List<NftDomainExpiry>?) {
        coEvery { repository.expiringDomains(setOf(owner), DOMAIN_RENEW_SOON_DAYS) } returns domains
    }

    private fun turnOffWithNoOwners() {
        coEvery { repository.owners(emptyList()) } returns emptySet()
        every { repository.itemsFlow(emptySet()) } returns MutableStateFlow(NftItemsState(emptyList(), isLoaded = true))
        showNft.value = false
    }

    @Test
    fun showNftCollections_offAtStart_readsNothingAndShowsNothing() = runTest(dispatcher) {
        turnOffWithNoOwners()

        val viewModel = createViewModel()
        viewModel.onResume()
        viewModel.refresh()

        coVerify(exactly = 0) { repository.refresh(match { it.isNotEmpty() }, any()) }
        coVerify(exactly = 0) { repository.expiringDomains(match { it.isNotEmpty() }, any()) }
        assertTrue(viewModel.uiState.items.isEmpty())
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun showNftCollections_turnedOn_refreshesAndShowsItems() = runTest(dispatcher) {
        turnOffWithNoOwners()
        val viewModel = createViewModel()

        showNft.value = true
        items.value = NftItemsState(listOf(record("a")), isLoaded = true)

        coVerify(exactly = 1) { repository.refresh(setOf(owner), false) }
        assertEquals(1, viewModel.uiState.totalCount)
    }

    @Test
    fun showNftCollections_turnedOffWhileShown_clearsItemsAndIgnoresNewEvents() = runTest(dispatcher) {
        val viewModel = createViewModel()
        items.value = NftItemsState(listOf(record("a")), isLoaded = true)

        turnOffWithNoOwners()
        changes.emit(NftChange.NewEvent)
        advanceTimeBy(5_000)

        assertTrue(viewModel.uiState.items.isEmpty())
        coVerify(exactly = 0) { repository.refresh(any(), true) }
    }

    @Test
    fun refresh_inFlightWhenTurnedOff_isCancelled() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var finished = false
        coEvery { repository.refresh(setOf(owner), true) } coAnswers {
            gate.await()
            finished = true
        }
        val viewModel = createViewModel()
        viewModel.refresh()

        turnOffWithNoOwners()
        gate.complete(Unit)

        assertFalse(finished)
    }

    @Test
    fun receiveWallet_singleWallet_isThatWallet() = runTest(dispatcher) {
        val viewModel = createViewModel(NftCollectionSource.SingleWallet(wallet))

        assertSame(wallet, viewModel.receiveWallet)
    }

    @Test
    fun receiveWallet_allWallets_isFirstWalletWithNftSection() = runTest(dispatcher) {
        val unsupported = mockk<Wallet>()
        val supported = mockk<Wallet>()
        val laterSupported = mockk<Wallet>()
        val wallets = listOf(unsupported, supported, laterSupported)
        every { repository.supportsSection(unsupported) } returns false
        every { repository.supportsSection(supported) } returns true
        every { repository.supportsSection(laterSupported) } returns true
        coEvery { repository.owners(wallets) } returns setOf(owner)
        walletsFlow.value = wallets

        val viewModel = createViewModel()

        assertSame(supported, viewModel.receiveWallet)
    }

    @Test
    fun receiveWallet_noWalletWithNftSection_isNull() = runTest(dispatcher) {
        every { repository.supportsSection(any()) } returns false

        val viewModel = createViewModel()

        assertNull(viewModel.receiveWallet)
    }

    @Test
    fun expiringDomainAddresses_domainsExpireSoon_exposesTheirAddresses() = runTest(dispatcher) {
        expiringReturns(listOf(domain("a"), domain("b")))

        val viewModel = createViewModel()

        assertEquals(setOf("a", "b"), viewModel.uiState.expiringDomainAddresses)
    }

    @Test
    fun expiringDomainAddresses_nullRead_keepsPreviousAddresses() = runTest(dispatcher) {
        expiringReturns(listOf(domain("a")))
        val viewModel = createViewModel()

        expiringReturns(null)
        domainTriggers.emit(Unit)

        assertEquals(setOf("a"), viewModel.uiState.expiringDomainAddresses)
    }

    @Test
    fun expiringDomainAddresses_onResume_rereadsDomains() = runTest(dispatcher) {
        expiringReturns(listOf(domain("a")))
        val viewModel = createViewModel()

        expiringReturns(listOf(domain("b")))
        viewModel.onResume()

        assertEquals(setOf("b"), viewModel.uiState.expiringDomainAddresses)
    }

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

    @Test
    fun uiState_hiddenCollection_isNotListedButCounted() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("c1", hidden = true))

        items.value = NftItemsState(
            listOf(record("a", collection = "c1"), record("b", collection = "c1"), record("c")),
            isLoaded = true,
        )

        assertEquals(listOf("c"), viewModel.uiState.items.map { it.address })
        assertEquals(1, viewModel.uiState.hiddenCollectionsCount)
        assertEquals(1, viewModel.uiState.totalCount)
    }

    @Test
    fun uiState_blacklistAndUserSpam_areNotListedAndCountedAsCollections() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("c2", userTrust = NftUserTrust.Spam))

        items.value = NftItemsState(
            listOf(
                record("a", NftTrust.Blacklist, "c1"),
                record("b", NftTrust.Blacklist, "c1"),
                record("c", collection = "c2"),
                record("d"),
            ),
            isLoaded = true,
        )

        assertEquals(listOf("d"), viewModel.uiState.items.map { it.address })
        assertEquals(2, viewModel.uiState.hiddenCollectionsCount)
    }

    @Test
    fun uiState_blacklistWithUserTrust_isListedAsWhitelist() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("c1", userTrust = NftUserTrust.Trust))

        items.value = NftItemsState(listOf(record("a", NftTrust.Blacklist, "c1")), isLoaded = true)

        assertEquals(listOf(NftTrust.Whitelist), viewModel.uiState.items.map { it.trust })
        assertEquals(0, viewModel.uiState.hiddenCollectionsCount)
    }

    @Test
    fun uiState_everyCollectionHidden_isAllCollectionsHiddenNotEmpty() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("a", hidden = true))

        items.value = NftItemsState(listOf(record("a")), isLoaded = true)

        assertTrue(viewModel.uiState.allCollectionsHidden)
        assertFalse(viewModel.uiState.isEmpty)
    }

    @Test
    fun uiState_noNftsAtAll_isEmptyAndNotAllHidden() = runTest(dispatcher) {
        val viewModel = createViewModel()

        items.value = NftItemsState(emptyList(), isLoaded = true)

        assertTrue(viewModel.uiState.isEmpty)
        assertFalse(viewModel.uiState.allCollectionsHidden)
    }

    @Test
    fun uiState_hiddenCollectionShownAgain_returnsToList() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("a", hidden = true))
        items.value = NftItemsState(listOf(record("a")), isLoaded = true)

        prefs.value = mapOf(pref("a", hidden = false))

        assertEquals(listOf("a"), viewModel.uiState.items.map { it.address })
        assertEquals(0, viewModel.uiState.hiddenCollectionsCount)
        assertFalse(viewModel.uiState.allCollectionsHidden)
    }
}
