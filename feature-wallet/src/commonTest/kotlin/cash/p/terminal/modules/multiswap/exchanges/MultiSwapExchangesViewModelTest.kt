package cash.p.terminal.modules.multiswap.exchanges

import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.providers.buildSwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.transaction_swap_status_exchanging
import cash.p.terminal.resources.transaction_swap_status_waiting
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class MultiSwapExchangesViewModelTest {

    private val pendingFlow = MutableStateFlow(listOf(executingSwap()))
    private val providerTransactionsFlow = MutableStateFlow(emptyList<SwapProviderTransaction>())
    private var providerTransaction = providerTransaction(TransactionStatusEnum.WAITING)

    private val pendingStorage = mockk<PendingMultiSwapStorage>(relaxed = true) {
        every { observeForActiveAccount(any()) } returns pendingFlow
    }
    private val providerStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { observeForActiveAccount(any()) } returns providerTransactionsFlow
        every { getByOutgoingRecordUid(TX_ID) } answers { providerTransaction }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(Translator)
        every { Translator.getString(Res.string.transaction_swap_status_waiting, *anyVararg()) } returns "waiting"
        every { Translator.getString(Res.string.transaction_swap_status_exchanging, *anyVararg()) } returns "exchanging"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun uiState_providerTransactionStatusChangesWithoutPendingChange_refreshesStatusText() {
        val viewModel = MultiSwapExchangesViewModel(
            pendingMultiSwapStorage = pendingStorage,
            marketKit = mockk<MarketKitWrapper>(relaxed = true),
            numberFormatter = mockk<IAppNumberFormatter>(relaxed = true),
            swapProviderTransactionsStorage = providerStorage,
            accountManager = mockk<IAccountManager>(relaxed = true),
        )
        assertEquals("waiting", viewModel.uiState.items.single().statusText)

        providerTransaction = providerTransaction(TransactionStatusEnum.EXCHANGING)
        providerTransactionsFlow.value = listOf(providerTransaction)

        assertEquals("exchanging", viewModel.uiState.items.single().statusText)
    }

    private fun providerTransaction(status: TransactionStatusEnum) =
        buildSwapProviderTransaction(SwapProvider.CHANGENOW, TX_ID).copy(status = status.name.lowercase())

    private fun executingSwap() = PendingMultiSwap(
        id = "swap-1",
        accountId = "account-1",
        createdAt = 1_000L,
        coinUidIn = "bitcoin",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal.ONE,
        coinUidIntermediate = "tether",
        blockchainTypeIntermediate = "ethereum",
        coinUidOut = "ethereum",
        blockchainTypeOut = "ethereum",
        leg1ProviderId = "changenow",
        leg1IsOffChain = true,
        leg1TransactionId = TX_ID,
        leg1AmountOut = null,
        leg1Status = PendingMultiSwap.STATUS_EXECUTING,
        leg2ProviderId = null,
        leg2IsOffChain = null,
        leg2TransactionId = null,
        leg2AmountOut = null,
        leg2Status = PendingMultiSwap.STATUS_PENDING,
        expectedAmountOut = BigDecimal.TEN,
    )

    private companion object {
        const val TX_ID = "outgoing-1"
    }
}
