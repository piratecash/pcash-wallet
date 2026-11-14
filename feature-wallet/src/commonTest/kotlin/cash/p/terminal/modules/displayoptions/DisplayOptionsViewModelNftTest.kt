package cash.p.terminal.modules.displayoptions

import cash.p.terminal.core.nft.GramHintsFixture
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayOptionsViewModelNftTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixture = GramHintsFixture()
    private val wallets = MutableStateFlow<List<Wallet>>(emptyList())
    private val accountManager = mockk<IAccountManager> {
        every { activeAccount } returns fixture.account
    }
    private val walletManager = mockk<IWalletManager> {
        every { activeWalletsFlow } returns wallets
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { fixture.settings.isRoundingAmountMainPage } returns true
        every { fixture.settings.displayDiffPricePeriod } returns DisplayPricePeriod.ONE_DAY
        every { fixture.settings.displayDiffOptionType } returns DisplayDiffOptionType.BOTH
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DisplayOptionsViewModel(accountManager, fixture.settings, walletManager, fixture.repository)

    @Test
    fun nftSection_accountCannotHoldGram_isAbsent() = runTest(dispatcher) {
        fixture.supportedVersions.clear()

        assertNull(createViewModel().uiState.value.nftSection)
    }

    @Test
    fun nftSection_gramAddedLater_switchesToGramHint() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(NftDisplaySection(show = true, hasGram = false), viewModel.uiState.value.nftSection)

        wallets.value = listOf(fixture.wallet(TonWalletVersion.W5))

        assertEquals(NftDisplaySection(show = true, hasGram = true), viewModel.uiState.value.nftSection)
    }

    @Test
    fun nftSection_onlyGramJettonAdded_countsAsNoGram() = runTest(dispatcher) {
        wallets.value = listOf(fixture.jettonWallet(TonWalletVersion.W5))

        assertEquals(NftDisplaySection(show = true, hasGram = false), createViewModel().uiState.value.nftSection)
    }

    @Test
    fun onShowNftCollectionsToggled_off_storesAndShowsOff() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onShowNftCollectionsToggled(false)

        assertEquals(false, fixture.showNft.value)
        assertEquals(NftDisplaySection(show = false, hasGram = false), viewModel.uiState.value.nftSection)
    }
}
