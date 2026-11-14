package cash.p.terminal.modules.nft.manage

import cash.p.terminal.core.nft.NftCollectionPrefKey
import cash.p.terminal.core.nft.NftCollectionPrefsRepository
import cash.p.terminal.core.nft.NftCollectionState
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftItemsState
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
import cash.p.terminal.modules.nft.NftCollectionSource
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class NftManageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = NftOwner("account", BlockchainType.Ton, "owner")
    private val items = MutableStateFlow(NftItemsState(emptyList(), isLoaded = false))
    private val prefs = MutableStateFlow<Map<NftCollectionPrefKey, NftCollectionPrefRecord>>(emptyMap())

    private val repository = mockk<NftCollectionsRepository> {
        every { ownersFlow(any()) } returns MutableStateFlow(setOf(owner))
        every { itemsFlow(setOf(owner)) } returns items
    }
    private val prefsRepository = mockk<NftCollectionPrefsRepository>(relaxed = true) {
        every { prefsFlow("account") } returns prefs
    }
    private val walletManager = mockk<IWalletManager> {
        every { activeWalletsFlow } returns MutableStateFlow(listOf(WalletFactory.previewWallet()))
    }
    private val hiddenManager = mockk<IBalanceHiddenManager>(relaxed = true) {
        every { balanceHiddenFlow } returns MutableStateFlow(false)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = NftManageViewModel(
        NftCollectionSource.AllWallets, repository, prefsRepository, walletManager, hiddenManager,
    )

    private fun nft(
        address: String,
        collection: String? = null,
        collectionName: String? = null,
        trust: NftTrust = NftTrust.Whitelist,
    ) = NftItemRecord(
        accountId = owner.accountId,
        blockchainType = owner.blockchainType,
        ownerAddress = owner.address,
        address = address,
        position = 0,
        name = "NFT $address",
        description = null,
        realOwnerAddress = null,
        collectionAddress = collection,
        collectionName = collectionName,
        collectionDescription = null,
        trust = trust,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    private fun pref(key: String, hidden: Boolean = false, userTrust: NftUserTrust = NftUserTrust.None) =
        NftCollectionPrefKey(owner.blockchainType, key) to
            NftCollectionPrefRecord(owner.accountId, owner.blockchainType, key, hidden, userTrust)

    private fun show(vararg records: NftItemRecord) {
        items.value = NftItemsState(records.toList(), isLoaded = true)
    }

    @Test
    fun uiState_nftsOfOneCollection_formOneRowWithCountAndFirstNft() = runTest(dispatcher) {
        val viewModel = createViewModel()

        show(nft("a", "c1", "Cats"), nft("b", "c1", "Cats"), nft("c", "c2", "Dogs"))

        val rows = viewModel.uiState.rows
        assertEquals(listOf(2, 1), rows.map { it.nftCount })
        assertEquals("a", rows.first().item.address)
        assertFalse(rows.first().isSingle)
    }

    @Test
    fun uiState_nftWithoutCollection_isSingleRowOfItsOwn() = runTest(dispatcher) {
        val viewModel = createViewModel()

        show(nft("a"), nft("b"))

        assertEquals(2, viewModel.uiState.rows.size)
        assertTrue(viewModel.uiState.rows.all { it.isSingle && it.nftCount == 1 })
    }

    @Test
    fun uiState_mixedStates_spreadOverSections() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("hid", hidden = true), pref("mine", userTrust = NftUserTrust.Spam))

        show(
            nft("a", "vis"),
            nft("b", "hid"),
            nft("c", "mine"),
            nft("d", "prov", trust = NftTrust.Blacklist),
        )

        val state = viewModel.uiState
        assertEquals(listOf("a"), state.visibleRows.map { it.item.address })
        assertEquals(listOf("b"), state.hiddenRows.map { it.item.address })
        assertEquals(
            listOf(NftCollectionState.SpamByUser, NftCollectionState.SpamByProvider),
            state.spamRows.map { it.state },
        )
    }

    @Test
    fun uiState_collectionWithBlacklistedMember_isSpamByProvider() = runTest(dispatcher) {
        val viewModel = createViewModel()

        show(nft("a", "mixed"), nft("b", "mixed", trust = NftTrust.Blacklist))

        assertTrue(viewModel.uiState.visibleRows.isEmpty())
        assertEquals(listOf(NftCollectionState.SpamByProvider), viewModel.uiState.spamRows.map { it.state })
    }

    @Test
    fun uiState_userTrustOnBlacklist_movesCollectionToVisible() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("prov", userTrust = NftUserTrust.Trust))

        show(nft("d", "prov", trust = NftTrust.Blacklist))

        assertEquals(1, viewModel.uiState.visibleRows.size)
        assertTrue(viewModel.uiState.spamRows.isEmpty())
    }

    @Test
    fun showAll_fourVisibleAndHiddenPresent_collapsesToThreeUntilToggled() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("hid", hidden = true))
        show(nft("1", "c1"), nft("2", "c2"), nft("3", "c3"), nft("4", "c4"), nft("5", "hid"))

        assertTrue(viewModel.uiState.canCollapseVisible)
        assertEquals(3, viewModel.uiState.shownVisibleRows.size)

        viewModel.onToggleShowAllVisible()

        assertEquals(4, viewModel.uiState.shownVisibleRows.size)
    }

    @Test
    fun showAll_noHiddenCollections_showsAllVisibleWithoutToggle() = runTest(dispatcher) {
        val viewModel = createViewModel()

        show(nft("1", "c1"), nft("2", "c2"), nft("3", "c3"), nft("4", "c4"))

        assertFalse(viewModel.uiState.canCollapseVisible)
        assertEquals(4, viewModel.uiState.shownVisibleRows.size)
    }

    @Test
    fun showAll_threeVisibleAndHiddenPresent_doesNotCollapse() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("hid", hidden = true))

        show(nft("1", "c1"), nft("2", "c2"), nft("3", "c3"), nft("4", "hid"))

        assertFalse(viewModel.uiState.canCollapseVisible)
    }

    @Test
    fun onVisibleChange_collapsedList_expandsVisibleList() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("hid", hidden = true))
        show(nft("1", "c1"), nft("2", "c2"), nft("3", "c3"), nft("4", "c4"), nft("5", "hid"))

        viewModel.onVisibleChange(viewModel.uiState.hiddenRows.single(), visible = true)

        assertTrue(viewModel.uiState.showAllVisible)
    }

    @Test
    fun onNotSpam_collapsedList_expandsVisibleList() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("hid", hidden = true))
        show(
            nft("1", "c1"), nft("2", "c2"), nft("3", "c3"), nft("4", "c4"), nft("5", "hid"),
            nft("6", "s", trust = NftTrust.Blacklist),
        )

        viewModel.onNotSpam(viewModel.uiState.spamRows.single())

        assertTrue(viewModel.uiState.showAllVisible)
    }

    @Test
    fun onVisibleChange_switchedOff_hidesCollectionThroughRepository() = runTest(dispatcher) {
        val viewModel = createViewModel()
        show(nft("a", "c1"))
        val row = viewModel.uiState.rows.single()

        viewModel.onVisibleChange(row, visible = false)

        verify(exactly = 1) { prefsRepository.setHidden(row.item, true) }
    }

    @Test
    fun onVisibleChange_switchedOn_unhidesCollectionThroughRepository() = runTest(dispatcher) {
        val viewModel = createViewModel()
        prefs.value = mapOf(pref("c1", hidden = true))
        show(nft("a", "c1"))
        val row = viewModel.uiState.rows.single()

        viewModel.onVisibleChange(row, visible = true)

        verify(exactly = 1) { prefsRepository.setHidden(row.item, false) }
    }

    @Test
    fun onNotSpam_spamRow_marksNotSpamThroughRepository() = runTest(dispatcher) {
        val viewModel = createViewModel()
        show(nft("d", "prov", trust = NftTrust.Blacklist))
        val row = viewModel.uiState.spamRows.single()

        viewModel.onNotSpam(row)

        verify(exactly = 1) { prefsRepository.markNotSpam(row.item) }
    }
}
