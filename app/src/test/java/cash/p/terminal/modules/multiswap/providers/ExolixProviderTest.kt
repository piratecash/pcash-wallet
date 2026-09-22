package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.network.exolix.domain.entity.ExolixNetwork
import cash.p.terminal.network.exolix.domain.entity.ExolixTransaction
import cash.p.terminal.network.exolix.domain.repository.ExolixRepository
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ExolixProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val exolixRepository = mockk<ExolixRepository>(relaxed = true)
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)

    private val tokenIn = nativeTestToken(BlockchainType.Bitcoin, "BTC")
    private val tokenOut = nativeTestToken(BlockchainType.Litecoin, "LTC")

    @Before
    fun setUp() {
        every { accountManager.activeAccount } returns buildTestAccount("acc-1")
        coEvery { exolixRepository.getCurrencyNetworks("BTC") } returns listOf(exolixNetwork("BTC"))
        coEvery { exolixRepository.getCurrencyNetworks("LTC") } returns listOf(exolixNetwork("LTC"))
        coEvery { exolixRepository.createTransaction(any()) } returns exolixTransaction()
    }

    @Test
    fun onTransactionCompleted_afterFinalQuote_nextFinalQuoteCreatesNewTransaction() = runTest(dispatcher) {
        val provider = createProvider()

        provider.fetchFinalQuote()
        provider.fetchFinalQuote()
        provider.onTransactionCompleted(
            buildSwapProviderTransaction(SwapProvider.EXOLIX, "ex-tx-1"),
            mockk(relaxed = true),
        )
        provider.fetchFinalQuote()

        coVerify(exactly = 2) { exolixRepository.createTransaction(any()) }
    }

    private suspend fun ExolixProvider.fetchFinalQuote() =
        fetchFinalQuote(tokenIn, tokenOut, BigDecimal.ONE, emptyMap(), null, mockk(relaxed = true))

    private fun createProvider() = ExolixProvider(
        walletUseCase = walletUseCase,
        exolixRepository = exolixRepository,
        accountManager = accountManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        providerSupport = buildOffChainSwapProviderSupport(walletUseCase, accountManager, storage, marketKit),
    )

    private fun exolixNetwork(network: String) = ExolixNetwork(
        network = network,
        name = network,
        shortName = null,
        memoNeeded = false,
        memoName = null,
        contract = null,
    )

    private fun exolixTransaction() = ExolixTransaction(
        id = "ex-order-1",
        amount = BigDecimal.ONE,
        amountTo = BigDecimal("0.5"),
        createdAt = null,
        updatedAt = null,
        depositAddress = "deposit",
        depositExtraId = null,
        withdrawalAddress = "withdrawal",
        withdrawalExtraId = null,
        status = "wait",
    )
}
