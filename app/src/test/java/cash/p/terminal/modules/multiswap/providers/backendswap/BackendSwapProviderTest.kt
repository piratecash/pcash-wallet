package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.multiswap.SwapAmountOutOfRange
import cash.p.terminal.modules.multiswap.SwapDepositTooSmall
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteOffChain
import cash.p.terminal.modules.multiswap.SwapRouteNotFound
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProviderSupport
import cash.p.terminal.modules.multiswap.providers.SwapDepositMemoUnsupported
import cash.p.terminal.modules.multiswap.providers.buildSwapProviderTransaction
import cash.p.terminal.modules.multiswap.providers.buildTestAccount
import cash.p.terminal.modules.multiswap.providers.yiFiTestToken
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.network.backendswap.data.entity.BackendSwapError
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapConfirmRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreatedOrder
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapEstimate
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import cash.p.terminal.network.swaprepository.SwapProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class BackendSwapProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { activeAccount } returns buildTestAccount("acc-1")
    }
    private val repository = mockk<BackendSwapRepository>(relaxed = true)
    private val resolver = mockk<BackendSwapAssetResolver>()
    private val signer = mockk<BackendSwapSigner>()
    private val providerSupport = mockk<OffChainSwapProviderSupport>(relaxed = true)

    private val provider = BackendSwapProvider(
        info = BackendSwapProviderInfo(
            name = PROVIDER,
            displayName = "Changelly",
            logoUrl = "https://p.cash/changelly.png",
            active = true,
            supportsFixed = false,
            supportsFloat = true,
        ),
        walletUseCase = walletUseCase,
        accountManager = accountManager,
        backendSwapRepository = repository,
        assetResolver = resolver,
        signer = signer,
        providerSupport = providerSupport,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    private val eth = yiFiTestToken(BlockchainType.Ethereum, TokenType.Native, "ETH")
    private val btc = yiFiTestToken(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84), "BTC")
    private val xlm = yiFiTestToken(BlockchainType.Stellar, TokenType.Native, "XLM")

    init {
        coEvery { resolver.resolve(PROVIDER, any()) } coAnswers { asset(secondArg()) }
        coEvery { signer.walletAddress(any()) } returns WALLET
        coEvery { signer.sign(any(), any()) } returns SIGNATURE
        every { walletUseCase.getReceiveAddress(any()) } answers { " recv-${firstArg<Token>().coin.code} " }
        coEvery { providerSupport.getRefundAddress(any(), any()) } returns "refund"
        every { providerSupport.buildTransactionData(any(), any(), any(), any()) } returns
            SendTransactionData.Unsupported
        coEvery { repository.estimate(any()) } returns
            BackendSwapEstimate(BigDecimal.ONE, BigDecimal("0.03"), minAmount = null, maxAmount = null)
        stubOrder(order())
    }

    @Test
    fun supports_signerAndResolvedAsset_returnsTrue() = runTest(dispatcher) {
        assertTrue(provider.supports(eth, btc))
    }

    @Test
    fun supports_accountWithoutTypedDataSigner_returnsFalse() = runTest(dispatcher) {
        coEvery { signer.walletAddress(any()) } returns null

        assertFalse(provider.supports(eth))
    }

    @Test
    fun supports_unresolvedAsset_returnsFalse() = runTest(dispatcher) {
        coEvery { resolver.resolve(PROVIDER, btc) } returns null

        assertFalse(provider.supports(eth, btc))
    }

    @Test
    fun supports_shieldedZecOutput_returnsFalse() = runTest(dispatcher) {
        val zec = yiFiTestToken(
            BlockchainType.Zcash,
            TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
            "ZEC",
        )

        assertFalse(provider.supports(eth, zec))
    }

    @Test
    fun fetchQuote_estimateSucceeds_returnsAmountWithFeeForFloatType() = runTest(dispatcher) {
        val quote = fetchQuote()

        assertEquals(BigDecimal("0.03"), quote.amountOut)
        coVerify {
            repository.estimate(match { it.type == "float" && it.from == asset(eth) && it.to == asset(btc) })
        }
    }

    @Test
    fun fetchQuote_minimumLimitError_throwsDepositTooSmallWithMinimum() = runTest(dispatcher) {
        stubEstimateError(amountLimitsError("minimum"))

        assertEquals(BigDecimal("0.01"), assertFailsWith<SwapDepositTooSmall> { fetchQuote() }.minValue)
    }

    @Test
    fun fetchQuote_maximumLimitError_throwsOutOfRange() = runTest(dispatcher) {
        stubEstimateError(amountLimitsError("maximum"))

        assertFailsWith<SwapAmountOutOfRange> { fetchQuote() }
    }

    @Test
    fun fetchQuote_limitsErrorWithoutLimitType_throwsOutOfRange() = runTest(dispatcher) {
        stubEstimateError(amountLimitsError(limitType = null))

        assertFailsWith<SwapAmountOutOfRange> { fetchQuote() }
    }

    @Test
    fun fetchQuote_amountOutOfRangeError_throwsOutOfRange() = runTest(dispatcher) {
        stubEstimateError(BackendSwapError(statusCode = 422, code = "AMOUNT_OUT_OF_RANGE"))

        assertFailsWith<SwapAmountOutOfRange> { fetchQuote() }
    }

    @Test
    fun fetchQuote_currencyErrors_throwRouteNotFound() = runTest(dispatcher) {
        listOf(
            BackendSwapError(statusCode = 400, code = "CURRENCY_NOT_FOUND"),
            BackendSwapError(statusCode = 422, code = "CURRENCY_TEMPORARILY_DISABLED"),
            BackendSwapError(statusCode = 422, code = "INVALID_PARAMETER"),
        ).forEach { error ->
            stubEstimateError(error)

            assertFailsWith<SwapRouteNotFound> { fetchQuote() }
        }
    }

    @Test
    fun fetchQuote_rateLimitOrServerError_rethrown() = runTest(dispatcher) {
        listOf(
            BackendSwapError(statusCode = 429, code = "RATE_LIMIT_EXCEEDED"),
            BackendSwapError(statusCode = 502, code = "PROVIDER_INTERNAL_ERROR"),
        ).forEach { error ->
            stubEstimateError(error)

            assertSame(error, assertFailsWith<BackendSwapError> { fetchQuote() })
        }
    }

    @Test
    fun fetchQuote_unresolvedAsset_throwsRouteNotFound() = runTest(dispatcher) {
        coEvery { resolver.resolve(PROVIDER, btc) } returns null

        assertFailsWith<SwapRouteNotFound> { fetchQuote() }
    }

    @Test
    fun fetchFinalQuote_newOrder_sendsSignedRequestWithTrimmedAddresses() = runTest(dispatcher) {
        fetchFinalQuote()

        coVerify {
            repository.create(
                match {
                    it.signature == SIGNATURE && it.recipient == "recv-BTC" && it.refundAddress == "refund" &&
                        it.type == "float" && it.provider == PROVIDER && it.clientRequestId.isNotBlank()
                },
                WALLET,
            )
        }
    }

    @Test
    fun fetchFinalQuote_memoOnMemoChain_buildsDepositDataWithMemo() = runTest(dispatcher) {
        stubOrder(order(memo = "251398"))

        fetchFinalQuote(tokenIn = xlm)

        verify { providerSupport.buildTransactionData(xlm, BigDecimal.ONE, "deposit", "251398") }
    }

    @Test
    fun fetchFinalQuote_memoOnEvmInput_throwsMemoUnsupported() = runTest(dispatcher) {
        stubOrder(order(memo = "251398"))

        assertFailsWith<SwapDepositMemoUnsupported> { fetchFinalQuote() }
        verify(exactly = 0) { providerSupport.buildTransactionData(any(), any(), any(), any()) }
    }

    @Test
    fun fetchFinalQuote_blankPayinAddress_throwsWithoutDepositData() = runTest(dispatcher) {
        stubOrder(order(payinAddress = " "))

        assertFailsWith<IllegalStateException> { fetchFinalQuote() }
        verify(exactly = 0) { providerSupport.buildTransactionData(any(), any(), any(), any()) }
    }

    @Test
    fun fetchFinalQuote_sameKeyWithinTtl_createsOneOrder() = runTest(dispatcher) {
        stubOrder(order(payinExpiresAt = System.currentTimeMillis() + THREE_HOURS_MS))

        fetchFinalQuote()
        fetchFinalQuote()

        coVerify(exactly = 1) { repository.create(any(), any()) }
    }

    @Test
    fun fetchFinalQuote_depositDeadlineReached_createsNewOrder() = runTest(dispatcher) {
        stubOrder(order(payinExpiresAt = System.currentTimeMillis() + DEADLINE_MARGIN_MS))

        fetchFinalQuote()
        fetchFinalQuote()

        coVerify(exactly = 2) { repository.create(any(), any()) }
    }

    @Test
    fun fetchFinalQuote_payinExpiry_setsDeadlineWithSafetyMargin() = runTest(dispatcher) {
        val payinExpiresAt = System.currentTimeMillis() + THREE_HOURS_MS
        stubOrder(order(payinExpiresAt = payinExpiresAt))

        assertEquals(payinExpiresAt - DEADLINE_MARGIN_MS, fetchFinalQuote().validUntilMillis)
    }

    @Test
    fun fetchFinalQuote_order_buildsBackendSwapTransaction() = runTest(dispatcher) {
        val quote = fetchFinalQuote()

        assertEquals(BigDecimal("0.029"), quote.amountOut)
        verify {
            providerSupport.buildSwapProviderTransaction(
                provider = SwapProvider.PCASH_BACKEND,
                transactionId = ORDER_ID,
                tokenIn = eth,
                tokenOut = btc,
                amountIn = BigDecimal.ONE,
                amountOut = BigDecimal("0.029"),
                subProviderId = PROVIDER,
                externalId = "ext-1",
                walletAddress = WALLET,
            )
        }
    }

    @Test
    fun onTransactionCompleted_withCanonicalHash_confirmsDeposit() = runTest(dispatcher) {
        stubOrder(order(memo = "251398"))
        fetchFinalQuote(tokenIn = xlm)

        provider.onTransactionCompleted(transaction(), sendResult(txHash = "hash"))

        coVerify {
            repository.confirm(
                ORDER_ID,
                WALLET,
                BackendSwapConfirmRequest(
                    txHash = "hash",
                    amount = BigDecimal.ONE,
                    address = "deposit",
                    addressExtraId = "251398",
                    currency = asset(xlm),
                ),
            )
        }
    }

    @Test
    fun onTransactionCompleted_noCanonicalHash_skipsConfirm() = runTest(dispatcher) {
        fetchFinalQuote()
        val transaction = transaction()
        val result = sendResult(txHash = null)

        provider.onTransactionCompleted(transaction, result)

        verify { providerSupport.onTransactionCompleted(transaction, result, null) }
        coVerify(exactly = 0) { repository.confirm(any(), any(), any()) }
    }

    @Test
    fun onTransactionCompleted_afterFinalQuote_nextFinalQuoteCreatesNewOrder() = runTest(dispatcher) {
        stubOrder(order(payinExpiresAt = System.currentTimeMillis() + THREE_HOURS_MS))
        fetchFinalQuote()

        provider.onTransactionCompleted(transaction(), sendResult(txHash = "hash"))
        fetchFinalQuote()

        coVerify(exactly = 2) { repository.create(any(), any()) }
    }

    private suspend fun fetchQuote() =
        provider.fetchQuote(eth, btc, BigDecimal.ONE, emptyMap()) as SwapQuoteOffChain

    private suspend fun fetchFinalQuote(tokenIn: Token = eth) = provider.fetchFinalQuote(
        tokenIn, btc, BigDecimal.ONE, emptyMap(), null, mockk(relaxed = true)
    ) as SwapFinalQuoteEvm

    private fun asset(token: Token) = BackendSwapAsset(token.coin.uid, token.blockchainType.uid)

    private fun stubOrder(order: BackendSwapCreatedOrder) {
        coEvery { repository.create(any(), any()) } returns order
    }

    private fun stubEstimateError(error: BackendSwapError) {
        coEvery { repository.estimate(any()) } throws error
    }

    private fun amountLimitsError(limitType: String?) = BackendSwapError(
        statusCode = 422,
        code = "AMOUNT_LIMITS_ERROR",
        minAmount = "0.01",
        maxAmount = "10",
        limitType = limitType,
    )

    private fun order(
        memo: String? = null,
        payinAddress: String = "deposit",
        payinExpiresAt: Long? = null,
    ) = BackendSwapCreatedOrder(
        id = ORDER_ID,
        externalId = "ext-1",
        payinAddress = payinAddress,
        payinExtraId = memo,
        amountToExpected = BigDecimal("0.029"),
        payinExpiresAt = payinExpiresAt,
        status = "waiting",
    )

    private fun transaction() = buildSwapProviderTransaction(SwapProvider.PCASH_BACKEND, ORDER_ID)

    private fun sendResult(txHash: String?) = mockk<SendTransactionResult>(relaxed = true) {
        every { getCanonicalTxHash() } returns txHash
    }

    private companion object {
        const val PROVIDER = "changelly"
        const val ORDER_ID = "order-1"
        const val WALLET = "0x2c7536E3605D9C16a7a3D7b1898e529396a65c23"
        const val SIGNATURE = "0xsig"
        const val DEADLINE_MARGIN_MS = 10 * 60 * 1000L
        const val THREE_HOURS_MS = 3 * 60 * 60 * 1000L
    }
}
