package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.GramHintsFixture
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
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
class NftGramHintsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixture = GramHintsFixture()
    private val wallets = MutableStateFlow<List<Wallet>>(emptyList())
    private val activeAccountState = MutableStateFlow<ActiveAccountState>(ActiveAccountState.ActiveAccount(fixture.account))
    private val accountManager = mockk<IAccountManager> {
        every { activeAccountStateFlow } returns activeAccountState
    }
    private val walletManager = mockk<IWalletManager> {
        every { activeWalletsFlow } returns wallets
    }
    private val hiddenManager = mockk<IBalanceHiddenManager> {
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

    private fun createViewModel() =
        NftGramHintsViewModel(accountManager, walletManager, fixture.repository, fixture.settings, hiddenManager)

    @Test
    fun uiState_noGramAndNoNfts_showsAddBlockOnly() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.showAddGramBlock)
        assertEquals(emptyList(), viewModel.uiState.rows)
    }

    @Test
    fun uiState_noGramCountsArrive_rowsReplaceAddBlock() = runTest(dispatcher) {
        val gate = CompletableDeferred<Int?>()
        coEvery { fixture.tonNftProvider.countNfts(any(), TonWalletVersion.W5) } coAnswers { gate.await() }
        fixture.nftCounts[TonWalletVersion.V4R2] = 0

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.showAddGramBlock)

        gate.complete(4)

        assertFalse(viewModel.uiState.showAddGramBlock)
        assertEquals(listOf(NftVersionHint(TonWalletVersion.W5, 4)), viewModel.uiState.rows)
    }

    @Test
    fun uiState_v4r2AddedNftsOnW5_offersW5RowWithoutBlock() = runTest(dispatcher) {
        wallets.value = listOf(fixture.wallet(TonWalletVersion.V4R2))
        fixture.nftCounts[TonWalletVersion.W5] = 2

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.showAddGramBlock)
        assertEquals(listOf(NftVersionHint(TonWalletVersion.W5, 2)), viewModel.uiState.rows)
        coVerify(exactly = 0) { fixture.tonNftProvider.countNfts(any(), TonWalletVersion.V4R2) }
    }

    @Test
    fun uiState_onlyW5JettonNftsOnW5_offersW5Row() = runTest(dispatcher) {
        wallets.value = listOf(fixture.jettonWallet(TonWalletVersion.W5))
        fixture.nftCounts[TonWalletVersion.W5] = 1
        fixture.nftCounts[TonWalletVersion.V4R2] = 0

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.showAddGramBlock)
        assertEquals(listOf(NftVersionHint(TonWalletVersion.W5, 1)), viewModel.uiState.rows)
    }

    @Test
    fun uiState_onlyW5JettonNoNfts_showsAddBlock() = runTest(dispatcher) {
        wallets.value = listOf(fixture.jettonWallet(TonWalletVersion.W5))
        fixture.nftCounts[TonWalletVersion.W5] = 0
        fixture.nftCounts[TonWalletVersion.V4R2] = 0

        assertTrue(createViewModel().uiState.showAddGramBlock)
    }

    @Test
    fun uiState_otherAccountHasGram_stillCountsAsNoGram() = runTest(dispatcher) {
        val other = fixture.account("other", fixture.account.type)
        wallets.value = listOf(fixture.wallet(TonWalletVersion.V4R2, owner = other))

        assertTrue(createViewModel().uiState.showAddGramBlock)
    }

    @Test
    fun uiState_settingOff_showsNothingAndReadsNothing() = runTest(dispatcher) {
        fixture.showNft.value = false
        fixture.nftCounts[TonWalletVersion.W5] = 2

        val viewModel = createViewModel()

        assertEquals(NftGramHintsUiState(), viewModel.uiState)
        coVerify(exactly = 0) { fixture.tonNftProvider.countNfts(any(), any()) }
    }

    @Test
    fun uiState_accountCannotHoldGram_showsNothing() = runTest(dispatcher) {
        fixture.supportedVersions.clear()

        assertEquals(NftGramHintsUiState(), createViewModel().uiState)
    }

    @Test
    fun dismiss_row_removesItForGood() = runTest(dispatcher) {
        wallets.value = listOf(fixture.wallet(TonWalletVersion.V4R2))
        fixture.nftCounts[TonWalletVersion.W5] = 2
        val viewModel = createViewModel()

        viewModel.dismiss(TonWalletVersion.W5)

        assertEquals(emptyList(), viewModel.uiState.rows)
        assertEquals(emptyList(), createViewModel().uiState.rows)
    }

    @Test
    fun addDefault_noGram_addsV4R2Only() = runTest(dispatcher) {
        createViewModel().addDefault()

        coVerify(exactly = 1) {
            fixture.walletActivator.activateWalletsSuspended(
                fixture.account,
                listOf(TonAsset.Native(TonWalletVersion.V4R2).tokenQuery),
            )
        }
    }

    @Test
    fun add_noTonKey_opensCoinManager() = runTest(dispatcher) {
        coEvery { fixture.tonNftProvider.hasAddress(any(), any()) } returns false
        val viewModel = createViewModel()

        viewModel.addDefault()
        assertTrue(viewModel.uiState.openCoinManager)

        viewModel.onCoinManagerOpened()
        assertFalse(viewModel.uiState.openCoinManager)
    }

    @Test
    fun addDefault_accountSwitchedWhileReadIgnoresCancellation_addsToNewAccount() = runTest(dispatcher) {
        val gate = CompletableDeferred<Int?>()
        coEvery { fixture.tonNftProvider.countNfts(fixture.account, any()) } coAnswers {
            withContext(NonCancellable) { gate.await() }
        }
        val viewModel = createViewModel()
        val other = fixture.account("other", fixture.account.type)

        activeAccountState.value = ActiveAccountState.ActiveAccount(other)
        wallets.value = listOf(fixture.wallet(TonWalletVersion.V4R2, owner = other))
        viewModel.addDefault()

        assertFalse(viewModel.uiState.showAddGramBlock)
        coVerify(exactly = 1) { fixture.walletActivator.activateWalletsSuspended(other, any()) }
        coVerify(exactly = 0) { fixture.walletActivator.activateWalletsSuspended(fixture.account, any()) }
        gate.complete(0)
    }

    @Test
    fun onResume_afterFailedRead_retriesAndShowsRow() = runTest(dispatcher) {
        wallets.value = listOf(fixture.wallet(TonWalletVersion.V4R2))
        val viewModel = createViewModel()
        assertEquals(emptyList(), viewModel.uiState.rows)

        fixture.nftCounts[TonWalletVersion.W5] = 7
        viewModel.onResume()

        assertEquals(listOf(NftVersionHint(TonWalletVersion.W5, 7)), viewModel.uiState.rows)
    }
}
