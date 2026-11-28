package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SwapSelectCoinViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))

    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val currencyManager = mockk<CurrencyManager>(relaxed = true)
    private val tron = Blockchain(BlockchainType.Tron, "TRON", null)

    private val mnemonicAccount = mockk<Account> {
        every { type } returns mockk<AccountType.Mnemonic>()
    }

    private var storedBalanceHidden = false
    private val localStorage = mockk<BalanceSettingsStorage>(relaxed = true)
    private lateinit var balanceHiddenManager: BalanceHiddenManager

    private val trx = tronToken("tron", "TRX", TokenType.Native, decimals = 6)
    private val usdt = tronToken("usdt", "USDT", TokenType.Eip20("TUsdt"), decimals = 6)
    private val noBalance = tronToken("nobalance", "NOB", TokenType.Eip20("TNoBalance"), decimals = 6)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { localStorage.balanceHidden } answers { storedBalanceHidden }
        every { localStorage.balanceHidden = any() } answers { storedBalanceHidden = firstArg() }
        every { localStorage.balanceAutoHideEnabled } returns false
        balanceHiddenManager = BalanceHiddenManager(
            localStorage = localStorage,
            backgroundManager = mockk<IBackgroundManager>(relaxed = true) {
                every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
            },
            dispatcherProvider = dispatcherProvider,
        )
        startKoin {
            modules(module {
                single { marketKit }
                single { walletManager }
                single { adapterManager }
                single { currencyManager }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
            })
        }
        every { walletManager.activeWallets } returns emptyList()
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2)
        every { marketKit.coinPrice(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        stopKoin()
    }

    @Test
    fun uiState_suggestedTrc10_excludesUnsupportedToken() {
        val trc10 = tronToken("trc10", "T10", TokenType.Trc10("1005114"), decimals = 0)
        val trc20 = tronToken("trc20", "T20", TokenType.Eip20("TContract"), decimals = 6)
        coEvery { marketKit.fullCoins("", limit = 100) } returns listOf(
            FullCoin(trc10.coin, listOf(trc10)),
            FullCoin(trc20.coin, listOf(trc20)),
        )

        val viewModel = createViewModel(otherSelectedToken = trx)

        assertFalse(viewModel.uiState.coinBalanceItems.any { it.token == trc10 })
        assertTrue(viewModel.uiState.coinBalanceItems.any { it.token == trc20 })
    }

    @Test
    fun uiState_balanceHiddenGlobally_marksWalletItemsHidden() {
        givenWallets()
        balanceHiddenManager.setBalanceHidden(true)

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.balanceHidden)
        assertTrue(viewModel.item(trx).balanceHidden)
        assertTrue(viewModel.item(usdt).balanceHidden)
        assertTrue(viewModel.item(trx).balanceClickToggles)
    }

    @Test
    fun toggleItemBalanceHidden_hiddenItem_revealsOnlyThatItemAndSecondToggleHidesIt() {
        givenWallets()
        balanceHiddenManager.setBalanceHidden(true)
        val viewModel = createViewModel()

        viewModel.toggleItemBalanceHidden(viewModel.item(trx))

        assertFalse(viewModel.item(trx).balanceHidden)
        assertTrue(viewModel.item(trx).balanceClickToggles)
        assertTrue(viewModel.item(usdt).balanceHidden)

        viewModel.toggleItemBalanceHidden(viewModel.item(trx))

        assertTrue(viewModel.item(trx).balanceHidden)
    }

    @Test
    fun toggleItemBalanceHidden_individuallyHiddenWithGlobalVisible_secondClickStillToggles() {
        givenWallets()
        balanceHiddenManager.toggleWalletBalanceHidden(trx.tokenQuery.id)
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.balanceHidden)
        assertTrue(viewModel.item(trx).balanceHidden)
        assertTrue(viewModel.item(trx).balanceClickToggles)

        viewModel.toggleItemBalanceHidden(viewModel.item(trx))

        assertFalse(viewModel.item(trx).balanceHidden)
        assertTrue(viewModel.item(trx).balanceClickToggles)

        viewModel.toggleItemBalanceHidden(viewModel.item(trx))

        assertTrue(viewModel.item(trx).balanceHidden)
    }

    @Test
    fun uiState_globalVisibleItemNotToggled_balanceClickSelects() {
        givenWallets()

        val viewModel = createViewModel()

        assertFalse(viewModel.item(usdt).balanceHidden)
        assertFalse(viewModel.item(usdt).balanceClickToggles)
    }

    @Test
    fun toggleBalanceHidden_hidden_showsAllAndClearsGlobalFlag() {
        givenWallets()
        balanceHiddenManager.setBalanceHidden(true)
        val viewModel = createViewModel()
        viewModel.toggleItemBalanceHidden(viewModel.item(trx))

        viewModel.toggleBalanceHidden()

        assertFalse(viewModel.uiState.balanceHidden)
        assertFalse(viewModel.item(trx).balanceHidden)
        assertFalse(viewModel.item(usdt).balanceHidden)
        assertFalse(viewModel.item(trx).balanceClickToggles)
    }

    @Test
    fun uiState_visibilityChangedElsewhere_updatesItems() {
        givenWallets()
        val viewModel = createViewModel()

        balanceHiddenManager.setBalanceHidden(true)

        assertTrue(viewModel.uiState.balanceHidden)
        assertTrue(viewModel.item(usdt).balanceHidden)
    }

    @Test
    fun uiState_itemWithoutBalance_neverHidden() {
        givenWallets()
        balanceHiddenManager.setBalanceHidden(true)

        val viewModel = createViewModel()

        val item = viewModel.item(noBalance)
        assertEquals(null, item.balance)
        assertFalse(item.balanceHidden)
        assertFalse(item.balanceClickToggles)
    }

    private fun createViewModel(otherSelectedToken: Token? = null) = SwapSelectCoinViewModel(
        otherSelectedToken = otherSelectedToken,
        activeAccount = mnemonicAccount,
        dispatcherProvider = dispatcherProvider,
        balanceHiddenManager = balanceHiddenManager,
    )

    private fun givenWallets() {
        val withBalance = listOf(trx, usdt).map { token ->
            mockk<Wallet>(relaxed = true) { every { this@mockk.token } returns token }.also { wallet ->
                every { adapterManager.getBalanceAdapterForWallet(wallet) } returns mockk<IBalanceAdapter> {
                    every { balanceData } returns BalanceData(available = BigDecimal.ONE)
                }
            }
        }
        val withoutBalance = mockk<Wallet>(relaxed = true) { every { token } returns noBalance }
        every { adapterManager.getBalanceAdapterForWallet(withoutBalance) } returns null
        every { walletManager.activeWallets } returns withBalance + withoutBalance
    }

    private fun SwapSelectCoinViewModel.item(token: Token) =
        uiState.coinBalanceItems.first { it.token == token }

    private fun tronToken(
        uid: String,
        code: String,
        type: TokenType,
        decimals: Int,
    ) = Token(
        coin = Coin(uid = uid, name = code, code = code),
        blockchain = tron,
        type = type,
        decimals = decimals,
    )
}
