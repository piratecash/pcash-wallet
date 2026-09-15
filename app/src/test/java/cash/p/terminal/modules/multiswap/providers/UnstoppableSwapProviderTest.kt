package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableExecution
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableToken
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
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
class UnstoppableSwapProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val repository = mockk<UnstoppableRepository>(relaxed = true)
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)

    private val tokenIn = nativeTestToken(BlockchainType.Solana, "SOL")
    private val tokenOut = nativeTestToken(BlockchainType.Ethereum, "ETH")

    @Before
    fun setUp() {
        every { accountManager.activeAccount } returns buildTestAccount("acc-1")
        coEvery { repository.getTokens("NEAR") } returns UnstoppableProviderTokens(
            tokens = listOf(
                UnstoppableToken(chain = "solana", chainId = "solana", address = null, identifier = "SOL"),
                UnstoppableToken(chain = "ethereum", chainId = "1", address = null, identifier = "ETH"),
            ),
            supportedChainIds = emptyList(),
        )
        every { marketKit.token(match { it.blockchainType == BlockchainType.Solana }) } returns tokenIn
        every { marketKit.token(match { it.blockchainType == BlockchainType.Ethereum }) } returns tokenOut
        coEvery { repository.swap(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns route()
    }

    @Test
    fun onTransactionCompleted_afterFinalQuote_nextFinalQuoteCommitsNewRoute() = runTest(dispatcher) {
        val provider = createProvider()

        provider.fetchFinalQuote()
        provider.fetchFinalQuote()
        provider.onTransactionCompleted(
            buildSwapProviderTransaction(SwapProvider.UNSTOPPABLE, "route-uuid"),
            mockk(relaxed = true),
        )
        provider.fetchFinalQuote()

        coVerify(exactly = 2) { repository.swap(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private suspend fun UnstoppableSwapProvider.fetchFinalQuote() =
        fetchFinalQuote(tokenIn, tokenOut, BigDecimal.ONE, emptyMap(), null, mockk(relaxed = true))

    private fun createProvider() = UnstoppableSwapProvider(
        descriptor = UnstoppableProvider.Near,
        walletUseCase = walletUseCase,
        repository = repository,
        marketKit = marketKit,
        accountManager = accountManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        providerSupport = buildOffChainSwapProviderSupport(walletUseCase, accountManager, storage, marketKit),
    )

    private fun route() = UnstoppableRoute(
        expectedBuyAmount = BigDecimal("100"),
        minBuyAmount = null,
        estimatedTimeSeconds = null,
        approvalSpender = null,
        execution = UnstoppableExecution(
            method = UnstoppableExecution.METHOD_TRANSFER,
            chain = "solana",
            transactions = emptyList(),
            approval = null,
            depositAddress = "deposit",
            attachment = null,
            unsignedTx = null,
        ),
        uuid = "route-uuid",
    )
}
