package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableAttachment
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableExecution
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableToken
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private val rune = nativeTestToken(BlockchainType.Thorchain, "RUNE")
    private val cacao = nativeTestToken(BlockchainType.Mayachain, "CACAO")

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
        coEvery { repository.getTokens("STEALTHEX") } returns UnstoppableProviderTokens(
            tokens = listOf(
                UnstoppableToken(chain = "solana", chainId = "solana", address = null, identifier = "SOL"),
                UnstoppableToken(chain = "ethereum", chainId = "1", address = null, identifier = "ETH"),
                UnstoppableToken(
                    chain = "thorchain", chainId = "thorchain-1", address = null, identifier = "THOR.RUNE",
                ),
                UnstoppableToken(
                    chain = "mayachain", chainId = "mayachain-mainnet-v1", address = null, identifier = "MAYA.CACAO",
                ),
            ),
            supportedChainIds = emptyList(),
        )
        every {
            marketKit.token(match { it.blockchainType == BlockchainType.Thorchain && it.tokenType == TokenType.Native })
        } returns rune
        every {
            marketKit.token(match { it.blockchainType == BlockchainType.Mayachain && it.tokenType == TokenType.Native })
        } returns cacao
        coEvery { walletUseCase.getReceiveAddress(rune) } returns THOR_ADDRESS
        coEvery { walletUseCase.getReceiveAddress(cacao) } returns MAYA_ADDRESS
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

    @Test
    fun supports_runeAndCacao_trueInBothDirections() = runTest(dispatcher) {
        val provider = createProvider(UnstoppableProvider.StealthEx)

        listOf(rune, cacao).forEach { native ->
            assertTrue(provider.supports(native, tokenOut))
            assertTrue(provider.supports(tokenOut, native))
        }
    }

    @Test
    fun supports_chainOnlyCatalog_onlyTokensWithDerivableIdentifier() = runTest(dispatcher) {
        coEvery { repository.getTokens("NEAR") } returns UnstoppableProviderTokens(
            tokens = emptyList(),
            supportedChainIds = listOf("thorchain-1", "1"),
        )
        val usdt = yiFiTestToken(BlockchainType.Ethereum, TokenType.Eip20(USDT_CONTRACT), "USDT")
        val resolver = UnstoppableTokenResolver(UnstoppableProvider.Near, repository, marketKit)

        assertFalse(resolver.supports(rune))
        assertTrue(resolver.supports(usdt))
    }

    @Test
    fun chainId_thorchainAndMaya_mapBackToCatalogChainIds() {
        val resolver = UnstoppableTokenResolver(UnstoppableProvider.StealthEx, repository, marketKit)

        assertEquals("thorchain-1", resolver.chainId(BlockchainType.Thorchain))
        assertEquals("mayachain-mainnet-v1", resolver.chainId(BlockchainType.Mayachain))
    }

    @Test
    fun fetchFinalQuote_nativeIn_sendsThorchainTransferWithMemoAndOwnRefund() = runTest(dispatcher) {
        coEvery { repository.swap(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            route(memo = "=:ETH.ETH:0xuser")
        val provider = createProvider(UnstoppableProvider.StealthEx)

        natives().forEach { (native, identifier, address) ->
            val quote = provider.fetchFinalQuote(native, tokenOut) as SwapFinalQuoteEvm

            assertEquals(
                SendTransactionData.Thorchain.Send("deposit", BigDecimal.ONE, "=:ETH.ETH:0xuser"),
                quote.sendTransactionData,
            )
            coVerify { repository.swap(identifier, "ETH", any(), any(), any(), any(), address, any(), any()) }
        }
    }

    @Test
    fun fetchFinalQuote_nativeOut_paysToOwnAddress() = runTest(dispatcher) {
        val provider = createProvider(UnstoppableProvider.StealthEx)

        natives().forEach { (native, identifier, address) ->
            provider.fetchFinalQuote(tokenIn, native)

            coVerify { repository.swap("SOL", identifier, any(), any(), any(), address, any(), any(), any()) }
        }
    }

    private fun natives() = listOf(
        Triple(rune, "THOR.RUNE", THOR_ADDRESS),
        Triple(cacao, "MAYA.CACAO", MAYA_ADDRESS),
    )

    private suspend fun UnstoppableSwapProvider.fetchFinalQuote(
        from: Token = tokenIn,
        to: Token = tokenOut,
    ) = fetchFinalQuote(from, to, BigDecimal.ONE, emptyMap(), null, mockk(relaxed = true))

    private fun createProvider(descriptor: UnstoppableProvider = UnstoppableProvider.Near) = UnstoppableSwapProvider(
        descriptor = descriptor,
        walletUseCase = walletUseCase,
        repository = repository,
        marketKit = marketKit,
        accountManager = accountManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        providerSupport = buildOffChainSwapProviderSupport(walletUseCase, accountManager, storage, marketKit),
    )

    private fun route(memo: String? = null) = UnstoppableRoute(
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
            attachment = memo?.let { UnstoppableAttachment(type = "memo", value = it) },
            unsignedTx = null,
        ),
        uuid = "route-uuid",
    )

    private companion object {
        const val THOR_ADDRESS = "thor1user"
        const val MAYA_ADDRESS = "maya1user"
        const val USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7"
    }
}
