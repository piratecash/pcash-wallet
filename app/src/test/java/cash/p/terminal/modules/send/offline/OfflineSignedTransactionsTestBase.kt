package cash.p.terminal.modules.send.offline

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.modules.send.beam.BeamOfflineOperations
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.MnemonicDerivation
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule

/** Shared fixtures of the "Signed offline" list ViewModel tests. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class OfflineSignedTransactionsTestBase {

    protected val dispatcher = UnconfinedTestDispatcher()
    protected val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    protected val accountManager = mockk<IAccountManager>(relaxed = true)
    protected val walletManager = mockk<IWalletManager>(relaxed = true)
    protected val adapterManager = mockk<IAdapterManager>(relaxed = true)
    protected val transactionAdapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    protected val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    protected val rateRepository = mockk<TransactionsRateRepository>(relaxed = true)

    protected val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, "", MnemonicDerivation.Legacy),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { marketKit.token(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(emptyMap())
        every { rateRepository.getHistoricalRate(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    protected fun viewModel(
        scope: CoroutineScope,
        beamOperations: BeamOfflineOperations = mockk {
            every { observe(any()) } returns flowOf(BeamOfflineOperations.Inventory(emptyList()))
        },
    ) = OfflineSignedTransactionsViewModel(
        repository = repository,
        accountManager = accountManager,
        walletManager = walletManager,
        adapterManager = adapterManager,
        transactionAdapterManager = transactionAdapterManager,
        marketKit = marketKit,
        rateRepository = rateRepository,
        dispatcherProvider = TestDispatcherProvider(dispatcher, scope),
        beamOperations = beamOperations,
    )

    protected fun setupState(
        entities: List<OfflineSignedTransactionEntity>,
        wallets: List<Wallet>,
    ) {
        every { accountManager.activeAccountStateFlow } returns MutableStateFlow(
            ActiveAccountState.ActiveAccount(account)
        )
        every { walletManager.activeWalletsFlow } returns MutableStateFlow(wallets)
        every { repository.observe(account.id) } returns flowOf(entities)
    }

    protected fun assertStatusColor(item: OfflineSignedTransactionViewItem, color: ColorName) {
        assertEquals(color, item.statusValue.color)
        assertEquals(color, item.transactionItem.offlineStatus?.color)
    }

    protected fun wallet(token: Token): Wallet {
        val testAccount = account
        return mockk(relaxed = true) {
            every { this@mockk.token } returns token
            every { this@mockk.account } returns testAccount
            every { this@mockk.hardwarePublicKey } returns null
        }
    }
}
