package cash.p.terminal.modules.tokenselect

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.modules.balance.BalanceSorter
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.balance.DefaultBalanceService
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
class TokenSelectViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val balanceItems = MutableStateFlow<List<BalanceItem>>(emptyList())
    private val walletsLoaded = CompletableDeferred<Unit>()
    private val service = mockk<DefaultBalanceService>(relaxed = true) {
        every { balanceItemsFlow } returns balanceItems
        coEvery { awaitWalletsLoaded() } coAnswers { walletsLoaded.await() }
    }
    private val balanceViewTypeManager = mockk<BalanceViewTypeManager> {
        every { balanceViewTypeFlow } returns MutableStateFlow(BalanceViewType.CoinThenFiat)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(module {
                single<DispatcherProvider> { TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_walletsNotLoaded_isLoadingWithoutEmptyState() {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.isLoading)
        assertFalse(viewModel.uiState.noItems)
    }

    @Test
    fun uiState_walletsLoadedEmpty_endsLoadingWithEmptyState() {
        val viewModel = viewModel()

        walletsLoaded.complete(Unit)

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.noItems)
    }

    @Test
    fun uiState_walletsLoadedWithBalance_showsItems() {
        balanceItems.value = listOf(balanceItem(BalanceData(available = BigDecimal.ONE)))
        val viewModel = viewModel()

        walletsLoaded.complete(Unit)

        assertFalse(viewModel.uiState.isLoading)
        assertFalse(viewModel.uiState.noItems)
        assertEquals(1, viewModel.uiState.items.size)
    }

    @Test
    fun updateFilter_beforeWalletsLoaded_keepsLoadingAndAppliesQueryAfterwards() {
        balanceItems.value = listOf(balanceItem(BalanceData(available = BigDecimal.ONE)))
        val viewModel = viewModel()

        viewModel.updateFilter("zzz")

        assertTrue(viewModel.uiState.isLoading)

        walletsLoaded.complete(Unit)

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.items.isEmpty())
    }

    private fun viewModel() = TokenSelectViewModel(
        service = service,
        balanceViewItemFactory = mockk<BalanceViewItemFactory>(relaxed = true),
        balanceViewTypeManager = balanceViewTypeManager,
        itemsFilter = null,
        balanceSorter = BalanceSorter(),
        balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true),
        blockchainTypes = null,
        tokenTypes = null,
        totalBalance = mockk<TotalBalance>(relaxed = true),
    )

    @Test
    fun hasSelectableBalance_pendingOnlyBalance_returnsTrue() {
        val item = balanceItem(
            BalanceData(
                available = BigDecimal.ZERO,
                pending = BigDecimal.ONE
            )
        )

        assertTrue(item.hasSelectableBalance())
    }

    @Test
    fun hasSelectableBalance_zeroTotalBalance_returnsFalse() {
        val item = balanceItem(BalanceData(available = BigDecimal.ZERO))

        assertFalse(item.hasSelectableBalance())
    }

    private fun balanceItem(balanceData: BalanceData) = BalanceItem(
        wallet = mockk<Wallet>(relaxed = true) {
            every { coin } returns Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC")
        },
        balanceData = balanceData,
        state = AdapterState.Synced,
        sendAllowed = true,
        coinPrice = null,
    )
}
