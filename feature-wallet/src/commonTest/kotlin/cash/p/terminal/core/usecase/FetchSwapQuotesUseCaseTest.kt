package cash.p.terminal.core.usecase

import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountAccuracy
import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.SwapExecutionMode
import cash.p.terminal.modules.multiswap.SwapProviderQuote
import cash.p.terminal.modules.multiswap.providers.AllBridgeProvider
import cash.p.terminal.modules.multiswap.providers.IExactOutSwapProvider
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class FetchSwapQuotesUseCaseTest {

    private val useCase = FetchSwapQuotesUseCase(mockk(relaxed = true))
    private val tokenIn = mockToken(BlockchainType.Ethereum, TokenType.Native)
    private val tokenOut = mockk<Token>()
    private val amountIn = BigDecimal("1.0")

    private fun mockProvider(
        id: String,
        supports: Boolean = true,
        amountOut: BigDecimal = BigDecimal.ONE,
    ): IMultiSwapProvider {
        val quote = mockk<ISwapQuote> {
            every { this@mockk.amountOut } returns amountOut
        }
        return mockk(relaxed = true) {
            every { this@mockk.id } returns id
            coEvery { supports(tokenIn, tokenOut) } returns supports
            coEvery { fetchQuote(tokenIn, tokenOut, amountIn, any()) } returns quote
        }
    }

    @Test
    fun sortedByAmountOut_bestRateFirst() = runTest {
        val small = mockProvider("small", amountOut = BigDecimal("1"))
        val large = mockProvider("large", amountOut = BigDecimal("10"))
        val medium = mockProvider("medium", amountOut = BigDecimal("5"))

        val result = useCase(
            listOf(small, large, medium),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals("large", result[0].provider.id)
        assertEquals("medium", result[1].provider.id)
        assertEquals("small", result[2].provider.id)
    }

    @Test
    fun unsupportedProvider_excluded() = runTest {
        val supported = mockProvider("ok", supports = true)
        val unsupported = mockProvider("no", supports = false)

        val result = useCase(
            listOf(supported, unsupported),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals(1, result.size)
        assertEquals("ok", result[0].provider.id)
    }

    @Test
    fun allUnsupported_emptyList() = runTest {
        val unsupported = mockProvider("no", supports = false)

        val result = useCase(
            listOf(unsupported),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun invoke_nativeBeamSource_isQuotedLikeAnyOtherChain() = runTest {
        val nativeBeam = mockToken(BlockchainType.Beam, TokenType.Native)
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "remote-beam"
            coEvery { supports(nativeBeam, tokenOut) } returns true
            coEvery { fetchQuote(nativeBeam, tokenOut, amountIn, any()) } returns mockk {
                every { amountOut } returns BigDecimal.ONE
            }
        }

        val result = useCase(listOf(provider), nativeBeam, tokenOut, amountIn, SwapAmountDirection.In)

        assertEquals(1, result.size)
    }

    @Test
    fun invoke_nativeBeamDestination_allowsSupportedReceiveRoute() = runTest {
        val nativeBeam = mockToken(BlockchainType.Beam, TokenType.Native)
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "remote-beam"
            coEvery { supports(tokenIn, nativeBeam) } returns true
            coEvery { fetchQuote(tokenIn, nativeBeam, amountIn, any()) } returns mockk {
                every { amountOut } returns BigDecimal.ONE
            }
        }

        val result = useCase(
            listOf(provider),
            tokenIn,
            nativeBeam,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals(1, result.size)
        coVerify(exactly = 1) { provider.fetchQuote(tokenIn, nativeBeam, amountIn, any()) }
    }

    @Test
    fun invoke_beamGamingContractSource_remainsSupported() = runTest {
        val gamingBeam = mockToken(
            BlockchainType.Ethereum,
            TokenType.Eip20("0x62d0a8458ed7719fdaf978fe5929c6d342b0bfce"),
        )
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "gaming-beam"
            coEvery { supports(gamingBeam, tokenOut) } returns true
            coEvery { fetchQuote(gamingBeam, tokenOut, amountIn, any()) } returns mockk {
                every { amountOut } returns BigDecimal.ONE
            }
        }

        val result = useCase(
            listOf(provider),
            gamingBeam,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals(1, result.size)
        coVerify(exactly = 1) { provider.fetchQuote(gamingBeam, tokenOut, amountIn, any()) }
    }

    @Test
    fun providerSupportsThrows_excluded() = runTest {
        val failing = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "fail"
            coEvery { supports(tokenIn, tokenOut) } throws RuntimeException("network")
        }
        val ok = mockProvider("ok")

        val result = useCase(
            listOf(failing, ok),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals(1, result.size)
        assertEquals("ok", result[0].provider.id)
    }

    @Test
    fun providerFetchQuoteThrows_excluded() = runTest {
        val failing = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "fail"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, amountIn, any()) } throws RuntimeException("error")
        }
        val ok = mockProvider("ok")

        val result = useCase(
            listOf(failing, ok),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals(1, result.size)
        assertEquals("ok", result[0].provider.id)
    }

    @Test
    fun providerSupportsTimeout_excluded() = runTest {
        val slow = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "slow"
            coEvery { supports(tokenIn, tokenOut) } coAnswers {
                delay(10_000)
                true
            }
        }
        val fast = mockProvider("fast")

        val result = useCase(
            listOf(slow, fast),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )

        assertEquals(1, result.size)
        assertEquals("fast", result[0].provider.id)
    }

    @Test
    fun onProviderError_callbackInvoked() = runTest {
        val error = RuntimeException("deposit too small")
        // Use a provider that supports the pair but throws on fetchQuote
        val ok = mockProvider("ok")
        val failing = mockProvider("fail")
        coEvery { failing.fetchQuote(tokenIn, tokenOut, amountIn, any()) } throws error

        val errors = mutableListOf<Pair<String, Throwable>>()
        val result = useCase(
            providers = listOf(ok, failing),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amount = amountIn,
            direction = SwapAmountDirection.In,
            onProviderError = { provider, e -> errors.add(provider.id to e) },
        )

        assertEquals(1, result.size)
        assertEquals("ok", result[0].provider.id)
        assertEquals(1, errors.size)
        assertEquals("fail", errors[0].first)
        assertEquals(error.message, errors[0].second.message)
    }

    @Test
    fun emptyProvidersList_emptyResult() = runTest {
        val result = useCase(
            emptyList(),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.In,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun exactOut_nativeProvider_preservesQuoteAndExecutionMetadata() = runTest {
        val search = mockk<IterativeExactOutSearch>(relaxed = true)
        val useCase = FetchSwapQuotesUseCase(search)
        val sourceQuote = mockk<ISwapQuote>(relaxed = true) {
            every { amountIn } returns BigDecimal("2")
            every { amountOut } returns BigDecimal("1")
        }
        val provider = mockk<ExactOutProvider>(relaxed = true) {
            every { id } returns "native"
            every { exactOutAccuracy } returns SwapAmountAccuracy.AtLeast
            coEvery { supportsExactOut(tokenIn, tokenOut) } returns true
            coEvery { fetchQuoteExactOut(tokenIn, tokenOut, amountIn, any()) } returns sourceQuote
        }

        val result = useCase(
            listOf(provider),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.Out,
        ).single()

        assertSame(sourceQuote, result.swapQuote)
        assertEquals(SwapExecutionMode.NativeExactOut, result.executionMode)
        assertEquals(SwapAmountAccuracy.AtLeast, result.amountOutAccuracy)
        coVerify(exactly = 0) { search.search(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun exactOut_nativeProviderRejectsPair_doesNotFallBackToIterativeSearch() = runTest {
        val search = mockk<IterativeExactOutSearch>(relaxed = true)
        val provider = mockk<ExactOutProvider>(relaxed = true) {
            every { id } returns "native"
            coEvery { supportsExactOut(tokenIn, tokenOut) } returns false
        }

        val result = FetchSwapQuotesUseCase(search)(
            listOf(provider),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.Out,
        )

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { search.search(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun exactOut_withoutNativeSupport_usesIterativeSearch() = runTest {
        val provider = mockProvider("iterative")
        val estimated = estimatedQuote(provider)
        val search = mockSearch(provider, estimated)
        val result = FetchSwapQuotesUseCase(search)(
            listOf(provider),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.Out,
        )

        assertEquals(listOf(estimated), result)
        coVerify(exactly = 1) {
            search.search(provider, tokenIn, tokenOut, amountIn, any(), any())
        }
    }

    @Test
    fun exactOut_allBridgeProvider_usesIterativeSearch() = runTest {
        mockkObject(AllBridgeProvider)
        try {
            coEvery { AllBridgeProvider.supports(tokenIn, tokenOut) } returns true
            val estimated = estimatedQuote(AllBridgeProvider)
            val search = mockSearch(AllBridgeProvider, estimated)

            val result = FetchSwapQuotesUseCase(search)(
                listOf(AllBridgeProvider),
                tokenIn,
                tokenOut,
                amountIn,
                SwapAmountDirection.Out,
            )

            assertEquals(listOf(estimated), result)
            coVerify(exactly = 1) {
                search.search(AllBridgeProvider, tokenIn, tokenOut, amountIn, any(), any())
            }
        } finally {
            unmockkObject(AllBridgeProvider)
        }
    }

    @Test
    fun exactOut_iterativeSearchFails_preservesOtherProviderQuote() = runTest {
        val failing = mockProvider("failing")
        val working = mockProvider("working")
        val estimated = estimatedQuote(working)
        val search = mockk<IterativeExactOutSearch> {
            coEvery {
                search(failing, tokenIn, tokenOut, amountIn, any(), any())
            } returns null
            coEvery {
                search(working, tokenIn, tokenOut, amountIn, any(), any())
            } returns estimated
        }

        val result = FetchSwapQuotesUseCase(search)(
            listOf(failing, working),
            tokenIn,
            tokenOut,
            amountIn,
            SwapAmountDirection.Out,
        )

        assertEquals(listOf(estimated), result)
        coVerify(exactly = 1) {
            search.search(failing, tokenIn, tokenOut, amountIn, any(), any())
            search.search(working, tokenIn, tokenOut, amountIn, any(), any())
        }
    }

    @Test
    fun swapProviderQuote_providerCautions_areExposed() {
        val caution = mockk<HSCaution>()
        val swapQuote = mockk<ISwapQuote>(relaxed = true) {
            every { cautions } returns listOf(caution)
        }

        assertEquals(listOf(caution), SwapProviderQuote(mockProvider("provider"), swapQuote).cautions)
    }

    private fun estimatedQuote(provider: IMultiSwapProvider) = SwapProviderQuote(
        provider,
        mockk(relaxed = true),
        amountOutAccuracy = SwapAmountAccuracy.Estimated,
    )

    private fun mockSearch(
        provider: IMultiSwapProvider,
        result: SwapProviderQuote,
    ) = mockk<IterativeExactOutSearch> {
        coEvery {
            search(provider, tokenIn, tokenOut, amountIn, any(), any())
        } returns result
    }

    private fun mockToken(blockchainType: BlockchainType, tokenType: TokenType): Token = mockk {
        every { this@mockk.blockchainType } returns blockchainType
        every { type } returns tokenType
    }

    private interface ExactOutProvider : IMultiSwapProvider, IExactOutSwapProvider
}
