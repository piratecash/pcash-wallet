package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.multiswap.SwapAmountOutOfRange
import cash.p.terminal.modules.multiswap.SwapDepositTooSmall
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteOffChain
import cash.p.terminal.modules.multiswap.SwapRouteNotFound
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.yifi.data.entity.BackendYiFiResponseError
import cash.p.terminal.network.yifi.data.repository.YiFiRepository
import cash.p.terminal.network.yifi.domain.entity.YiFiOrder
import cash.p.terminal.network.yifi.domain.entity.YiFiPair
import cash.p.terminal.network.yifi.domain.entity.YiFiQuote
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.MockKVerificationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class YiFiProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val repository = mockk<YiFiRepository>()
    private val resolver = mockk<YiFiTokenResolver>()
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { activeAccount } returns buildTestAccount("acc-1")
    }
    private val providerSupport = mockk<OffChainSwapProviderSupport>(relaxed = true)

    private val provider = YiFiProvider(
        walletUseCase = walletUseCase,
        yiFiRepository = repository,
        tokenResolver = resolver,
        accountManager = accountManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        providerSupport = providerSupport,
    )

    private val eth = yiFiTestToken(BlockchainType.Ethereum, TokenType.Native, "ETH")
    private val btc = yiFiTestToken(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84), "BTC")

    init {
        coEvery { resolver.resolveAsset(any()) } coAnswers {
            firstArg<Token>().coin.code.let { YiFiAsset(it, it) }
        }
        coEvery { walletUseCase.getReceiveAddress(any()) } coAnswers { "recv-${firstArg<Token>().coin.code}" }
        coEvery { providerSupport.getRefundAddress(any(), any()) } returns "refund"
        every { providerSupport.buildTransactionData(any(), any(), any(), any()) } returns
            SendTransactionData.Unsupported
        coEvery { repository.getPair(any(), any(), any(), any()) } returns
            YiFiPair(BigDecimal("0.001"), BigDecimal("10"))
        stubQuotes(quote("ff", "FixedFloat"))
        stubOrders(order())
    }

    @Test
    fun fetchQuote_amountBelowMin_throwsDepositTooSmall() = runTest(dispatcher) {
        val error = assertFailsWith<SwapDepositTooSmall> { fetchQuote(BigDecimal("0.0001")) }

        assertEquals(BigDecimal("0.001"), error.minValue)
    }

    @Test
    fun fetchQuote_amountAboveMax_throwsOutOfRange() = runTest(dispatcher) {
        assertFailsWith<SwapAmountOutOfRange> { fetchQuote(BigDecimal("11")) }
    }

    @Test
    fun fetchQuote_zeroMax_hasNoUpperBound() = runTest(dispatcher) {
        coEvery { repository.getPair(any(), any(), any(), any()) } returns
            YiFiPair(BigDecimal("0.001"), BigDecimal.ZERO)

        assertEquals(BigDecimal("0.3"), fetchQuote(BigDecimal("1000")).amountOut)
    }

    @Test
    fun fetchQuote_noPair_throwsRouteNotFound() = runTest(dispatcher) {
        coEvery { repository.getPair(any(), any(), any(), any()) } returns null

        assertFailsWith<SwapRouteNotFound> { fetchQuote() }
    }

    @Test
    fun fetchQuote_emptyBestQuote_throwsRouteNotFound() = runTest(dispatcher) {
        stubQuotes(null)

        assertFailsWith<SwapRouteNotFound> { fetchQuote() }
    }

    @Test
    fun fetchQuote_noQuotesAvailable404_throwsRouteNotFound() = runTest(dispatcher) {
        coEvery { repository.getBestQuote(any(), any(), any(), any(), any(), any()) } throws
            BackendYiFiResponseError(BackendYiFiResponseError.NO_QUOTES_AVAILABLE, "none", 404)

        assertFailsWith<SwapRouteNotFound> { fetchQuote() }
    }

    @Test
    fun fetchQuote_unresolvableToken_throwsRouteNotFound() = runTest(dispatcher) {
        coEvery { resolver.resolveAsset(btc) } returns null

        assertFailsWith<SwapRouteNotFound> { fetchQuote() }
    }

    @Test
    fun fetchQuote_inRange_returnsBestQuoteWithEta() = runTest(dispatcher) {
        val quote = fetchQuote()

        assertEquals(BigDecimal("0.3"), quote.amountOut)
        assertEquals(30L, quote.estimationTime)
    }

    @Test
    fun fetchFinalQuote_sameKeyTwice_createsOneOrderAndKeepsExchangerName() = runTest(dispatcher) {
        fetchFinalQuote()
        fetchFinalQuote()

        coVerify(exactly = 1) { repository.createSwap(any()) }
        verify(exactly = 2) { yiFiTransactionBuilt(subProviderId = "FixedFloat") }
    }

    @Test
    fun fetchFinalQuote_changedAmount_createsNewOrder() = runTest(dispatcher) {
        fetchFinalQuote(BigDecimal("1"))
        fetchFinalQuote(BigDecimal("2"))

        coVerify(exactly = 2) { repository.createSwap(any()) }
    }

    @Test
    fun fetchFinalQuote_rateExpiredOnce_requotesAndSucceeds() = runTest(dispatcher) {
        coEvery { repository.createSwap(any()) } throws rateExpired() andThen order()

        fetchFinalQuote()

        coVerify(exactly = 2) { repository.getBestQuote(any(), any(), any(), any(), any(), emptySet()) }
        coVerify(exactly = 2) { repository.createSwap(any()) }
    }

    @Test
    fun fetchFinalQuote_rateExpiredEveryAttempt_rethrowsAfterCap() = runTest(dispatcher) {
        val error = rateExpired()
        coEvery { repository.createSwap(any()) } throws error

        assertSame(error, assertFailsWith<BackendYiFiResponseError> { fetchFinalQuote() })
        coVerify(exactly = 4) { repository.createSwap(any()) }
    }

    @Test
    fun fetchFinalQuote_memoOnEvmInput_excludesProviderAndUsesNextQuote() = runTest(dispatcher) {
        coEvery { repository.getBestQuote(any(), any(), any(), any(), any(), emptySet()) } returns
            quote("ff", "FixedFloat")
        coEvery { repository.getBestQuote(any(), any(), any(), any(), any(), setOf("ff")) } returns
            quote("ss", "SimpleSwap")
        coEvery { repository.createSwap(match { it.provider == "ff" }) } returns order(memo = "251398")
        coEvery { repository.createSwap(match { it.provider == "ss" }) } returns order()

        fetchFinalQuote()

        verify { providerSupport.buildTransactionData(eth, any(), "deposit", null) }
        verify { yiFiTransactionBuilt(subProviderId = "SimpleSwap") }
    }

    @Test
    fun fetchFinalQuote_memoOnEveryAttempt_throwsMemoUnsupported() = runTest(dispatcher) {
        stubOrders(order(memo = "251398"))

        assertFailsWith<YiFiDepositMemoUnsupported> { fetchFinalQuote() }
        verify(exactly = 0) { providerSupport.buildTransactionData(any(), any(), any(), any()) }
    }

    @Test
    fun fetchFinalQuote_memoExcludedThenNoQuote_throwsMemoUnsupported() = runTest(dispatcher) {
        coEvery { repository.getBestQuote(any(), any(), any(), any(), any(), setOf("ff")) } returns null
        stubOrders(order(memo = "251398"))

        assertFailsWith<YiFiDepositMemoUnsupported> { fetchFinalQuote() }
        coVerify(exactly = 1) { repository.createSwap(any()) }
    }

    @Test
    fun fetchFinalQuote_emptyFirstQuote_throwsRouteNotFound() = runTest(dispatcher) {
        stubQuotes(null)

        assertFailsWith<SwapRouteNotFound> { fetchFinalQuote() }
    }

    @Test
    fun fetchFinalQuote_memoOnStellarAndTon_passedToTransactionData() = runTest(dispatcher) {
        stubOrders(order(memo = "251398"))
        listOf(BlockchainType.Stellar, BlockchainType.Ton).forEach { blockchainType ->
            val tokenIn = yiFiTestToken(blockchainType, TokenType.Native, blockchainType.uid.uppercase())

            fetchFinalQuote(tokenIn = tokenIn)

            verify { providerSupport.buildTransactionData(tokenIn, any(), "deposit", "251398") }
        }
    }

    @Test
    fun fetchFinalQuote_unifiedZecInput_sendsTransparentRefundAddress() = runTest(dispatcher) {
        val zec = yiFiTestToken(
            BlockchainType.Zcash,
            TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified),
            "ZEC"
        )
        coEvery { providerSupport.getRefundAddress(zec, true) } returns "t1transparent"

        fetchFinalQuote(tokenIn = zec)

        coVerify { repository.createSwap(match { it.refundAddress == "t1transparent" }) }
    }

    @Test
    fun fetchFinalQuote_bchAddresses_cashAddrPrefixStripped() = runTest(dispatcher) {
        val bch = yiFiTestToken(
            BlockchainType.BitcoinCash,
            TokenType.AddressTyped(TokenType.AddressType.Type145),
            "BCH"
        )
        coEvery { walletUseCase.getReceiveAddress(bch) } returns "bitcoincash:qrecv"
        coEvery { providerSupport.getRefundAddress(bch, any()) } returns "BITCOINCASH:qrefund"

        fetchFinalQuote(tokenIn = bch, tokenOut = bch)

        coVerify {
            repository.createSwap(match { it.receiveAddress == "qrecv" && it.refundAddress == "qrefund" })
        }
    }

    @Test
    fun fetchFinalQuote_invalidAddressError_propagatesUnwrapped() = runTest(dispatcher) {
        val error = BackendYiFiResponseError("INVALID_RECEIVE_ADDRESS", "bad address", 400)
        coEvery { repository.createSwap(any()) } throws error

        assertSame(error, assertFailsWith<BackendYiFiResponseError> { fetchFinalQuote() })
        coVerify(exactly = 1) { repository.createSwap(any()) }
    }

    @Test
    fun onTransactionCompleted_afterFinalQuote_nextFinalQuoteCreatesNewOrder() = runTest(dispatcher) {
        val transaction = buildSwapProviderTransaction(SwapProvider.YIFI, "tx-1")
        val result = mockk<SendTransactionResult>(relaxed = true)

        fetchFinalQuote()
        fetchFinalQuote()
        provider.onTransactionCompleted(transaction, result)
        fetchFinalQuote()

        coVerify(exactly = 2) { repository.createSwap(any()) }
        verify { providerSupport.onTransactionCompleted(transaction, result, null) }
    }

    @Test
    fun parseYiFiEtaSeconds_supportedFormats_returnSeconds() {
        assertEquals(30L, parseYiFiEtaSeconds("~30s"))
        assertEquals(194L * 60, parseYiFiEtaSeconds("~194m"))
        assertEquals(3600L, parseYiFiEtaSeconds("10-60"))
        assertNull(parseYiFiEtaSeconds(null))
    }

    private suspend fun fetchQuote(amountIn: BigDecimal = BigDecimal.ONE) =
        provider.fetchQuote(eth, btc, amountIn, emptyMap()) as SwapQuoteOffChain

    private suspend fun fetchFinalQuote(
        amountIn: BigDecimal = BigDecimal.ONE,
        tokenIn: Token = eth,
        tokenOut: Token = btc,
    ) = provider.fetchFinalQuote(tokenIn, tokenOut, amountIn, emptyMap(), null, mockk(relaxed = true))
        as SwapFinalQuoteEvm

    private fun MockKVerificationScope.yiFiTransactionBuilt(subProviderId: String) =
        providerSupport.buildSwapProviderTransaction(
            SwapProvider.YIFI, any(), any(), any(), any(), any(), subProviderId,
        )

    private fun stubQuotes(quote: YiFiQuote?) {
        coEvery { repository.getBestQuote(any(), any(), any(), any(), any(), any()) } returns quote
    }

    private fun stubOrders(order: YiFiOrder) {
        coEvery { repository.createSwap(any()) } returns order
    }

    private fun quote(provider: String, exchangerName: String) = YiFiQuote(
        rateId = "rate-$provider",
        provider = provider,
        exchangerName = exchangerName,
        estimatedOutput = BigDecimal("0.3"),
        estimatedTime = "~30s",
    )

    private fun order(memo: String? = null) = YiFiOrder(
        transactionId = "tx-1",
        provider = "ff",
        depositAddress = "deposit",
        extraIdDeposit = memo,
        receiveAmount = BigDecimal("0.29"),
    )

    private fun rateExpired() = BackendYiFiResponseError(BackendYiFiResponseError.RATE_EXPIRED, "expired", 400)
}
