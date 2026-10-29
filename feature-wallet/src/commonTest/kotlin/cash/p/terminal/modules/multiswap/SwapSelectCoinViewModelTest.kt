package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.CurrencyManager
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(module {
                single { marketKit }
                single { walletManager }
                single { adapterManager }
                single { currencyManager }
            })
        }
        every { walletManager.activeWallets } returns emptyList()
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        stopKoin()
    }

    @Test
    fun uiState_suggestedTrc10_excludesUnsupportedToken() {
        val trx = tronToken("tron", "TRX", TokenType.Native, decimals = 6)
        val trc10 = tronToken("trc10", "T10", TokenType.Trc10("1005114"), decimals = 0)
        val trc20 = tronToken("trc20", "T20", TokenType.Eip20("TContract"), decimals = 6)
        coEvery { marketKit.fullCoins("", limit = 100) } returns listOf(
            FullCoin(trc10.coin, listOf(trc10)),
            FullCoin(trc20.coin, listOf(trc20)),
        )

        val viewModel = SwapSelectCoinViewModel(
            otherSelectedToken = trx,
            activeAccount = mnemonicAccount,
            dispatcherProvider = dispatcherProvider,
        )

        assertFalse(viewModel.uiState.coinBalanceItems.any { it.token == trc10 })
        assertTrue(viewModel.uiState.coinBalanceItems.any { it.token == trc20 })
    }

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
