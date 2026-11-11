package cash.p.terminal.modules.receive.viewmodels

import cash.p.terminal.modules.receive.FullCoinsPage
import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveTokenSelectViewModelTest {

    private val walletUseCase = mockk<WalletUseCase> {
        every { getWallet(any<Token>()) } returns null
    }
    private val account = mockk<Account>(relaxed = true) {
        every { type } returns AccountType.Mnemonic(listOf("word"), "")
    }
    private val walletManager = mockk<IWalletManager>(relaxed = true) {
        every { activeWallets } returns emptyList()
    }
    private val fullCoinsProvider = mockk<FullCoinsProvider>(relaxed = true) {
        coEvery { getPage(any()) } returns FullCoinsPage(emptyList(), hasMore = false)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startKoin {
            modules(module {
                single { walletUseCase }
                single<PlatformChainSupport> { EthereumOnlyChainSupport }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun getCoinForReceiveType_unsupportedChain_reportsItWithoutCreatingWallet() = runTest {
        val result = viewModel().getCoinForReceiveType(fullCoin(BlockchainType.Tron))

        assertEquals(CoinForReceiveType.NotSupportedOnPlatform, result)
        coVerify(exactly = 0) { walletUseCase.createWalletIfNotExists(any()) }
    }

    @Test
    fun getCoinForReceiveType_supportedChain_createsWallet() = runTest {
        val wallet = mockk<Wallet>()
        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns wallet

        val result = viewModel().getCoinForReceiveType(fullCoin(BlockchainType.Ethereum))

        assertEquals(CoinForReceiveType.Single(wallet), result)
    }

    @Test
    fun uiState_beforeFirstPageArrives_isLoading() {
        pendingPages()

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.fullCoins.isEmpty())
    }

    @Test
    fun uiState_firstPageArrives_showsItemsAndStopsLoading() {
        val pages = pendingPages()
        val viewModel = viewModel()

        pages.first().complete(FullCoinsPage(listOf(fullCoin(BlockchainType.Ethereum)), hasMore = true))

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.hasMore)
        assertEquals(1, viewModel.uiState.fullCoins.size)
    }

    @Test
    fun onBottomReached_hasMore_requestsNextPageWithBiggerLimit() {
        val viewModel = loadedViewModel(hasMore = true)

        viewModel.onBottomReached()

        coVerify(exactly = 1) { fullCoinsProvider.getPage(400) }
    }

    @Test
    fun onBottomReached_noMore_doesNotRequest() {
        val viewModel = loadedViewModel(hasMore = false)

        viewModel.onBottomReached()

        coVerify(exactly = 1) { fullCoinsProvider.getPage(any()) }
    }

    @Test
    fun onBottomReached_repeatedWhileLoading_requestsOnce() {
        val viewModel = loadedViewModel(hasMore = true)
        pendingPages()

        viewModel.onBottomReached()
        viewModel.onBottomReached()

        coVerify(exactly = 1) { fullCoinsProvider.getPage(400) }
        coVerify(exactly = 0) { fullCoinsProvider.getPage(600) }
    }

    @Test
    fun updateFilter_afterPaging_resetsLimit() {
        val viewModel = loadedViewModel(hasMore = true)
        viewModel.onBottomReached()

        viewModel.updateFilter("btc")

        verify { fullCoinsProvider.setQuery("btc") }
        coVerify(exactly = 2) { fullCoinsProvider.getPage(200) }
    }

    @Test
    fun updateFilter_sameQuery_isNoOp() {
        val viewModel = loadedViewModel(hasMore = true)
        viewModel.updateFilter("btc")

        viewModel.updateFilter("btc")

        verify(exactly = 1) { fullCoinsProvider.setQuery("btc") }
        coVerify(exactly = 2) { fullCoinsProvider.getPage(200) }
    }

    @Test
    fun updateFilter_blankQueryOnStart_isNoOp() {
        val viewModel = loadedViewModel(hasMore = true)

        viewModel.updateFilter("")

        coVerify(exactly = 1) { fullCoinsProvider.getPage(any()) }
    }

    @Test
    fun updateFilter_whilePreviousLoadPending_dropsStaleResult() {
        val pages = pendingPages()
        val viewModel = viewModel()
        viewModel.updateFilter("btc")

        pages[0].complete(FullCoinsPage(listOf(fullCoin(BlockchainType.Tron)), hasMore = false))

        assertTrue(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.fullCoins.isEmpty())

        pages[1].complete(FullCoinsPage(listOf(fullCoin(BlockchainType.Ethereum)), hasMore = false))

        assertEquals(listOf(BlockchainType.Ethereum.uid), viewModel.uiState.fullCoins.map { it.coin.uid })
    }

    @Test
    fun updateFilter_newQueryLoading_disablesPaginationOfRetainedList() {
        val viewModel = loadedViewModel(hasMore = true)
        pendingPages()

        viewModel.updateFilter("btc")
        viewModel.onBottomReached()

        assertFalse(viewModel.uiState.hasMore)
        assertTrue(viewModel.uiState.fullCoins.isNotEmpty())
        coVerify(exactly = 0) { fullCoinsProvider.getPage(400) }
    }

    private fun loadedViewModel(hasMore: Boolean): ReceiveTokenSelectViewModel {
        coEvery { fullCoinsProvider.getPage(any()) } returns
            FullCoinsPage(listOf(fullCoin(BlockchainType.Ethereum)), hasMore)
        return viewModel()
    }

    private fun pendingPages(): List<CompletableDeferred<FullCoinsPage>> {
        val pages = List(MAX_PENDING_PAGES) { CompletableDeferred<FullCoinsPage>() }
        var next = 0
        coEvery { fullCoinsProvider.getPage(any()) } coAnswers { pages[next++].await() }
        return pages
    }

    private fun viewModel() = ReceiveTokenSelectViewModel(
        walletManager,
        account,
        fullCoinsProvider,
        mockk<IAdapterManager>(relaxed = true),
    )

    private fun fullCoin(blockchainType: BlockchainType): FullCoin {
        val coin = Coin(uid = blockchainType.uid, name = blockchainType.uid, code = blockchainType.uid)
        val token = Token(
            coin = coin,
            blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
            type = TokenType.Native,
            decimals = 8,
        )
        return FullCoin(coin = coin, tokens = listOf(token))
    }

    private companion object {
        const val MAX_PENDING_PAGES = 4
    }

    private object EthereumOnlyChainSupport : PlatformChainSupport {
        override fun isSupported(blockchainType: BlockchainType) = blockchainType == BlockchainType.Ethereum
        override fun isSupported(account: Account) = true
    }
}
