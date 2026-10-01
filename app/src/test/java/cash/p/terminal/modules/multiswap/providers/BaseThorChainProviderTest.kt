package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.App
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.multiswap.SwapQuoteThorChain
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import java.math.BigDecimal

class BaseThorChainProviderTest {

    private val api = mockk<ThornodeAPI>()
    private val marketKit = mockk<MarketKitWrapper>()

    private val rune = token(BlockchainType.Thorchain, TokenType.Native, "RUNE", 8)
    private val tcy = token(BlockchainType.Thorchain, TokenType.ThorchainAsset("tcy"), "TCY", 8)
    private val securedBtc = token(BlockchainType.Thorchain, TokenType.ThorchainAsset("btc-btc"), "BTC", 8)
    private val cacao = token(BlockchainType.Mayachain, TokenType.Native, "CACAO", 10)
    private val eth = token(BlockchainType.Ethereum, TokenType.Native, "ETH", 18)
    private val tokens = listOf(rune, tcy, securedBtc, cacao, eth).associateBy { it.tokenQuery }
    private val providers = listOf(ThorChainProvider, MayaProvider)
    private val originalApis = providers.map { it.thornodeAPI }

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single<SwapProviderTransactionFactory> { mockk(relaxed = true) }
                single<WalletUseCase> { mockk(relaxed = true) }
            })
        }
        mockkObject(App)
        every { App.marketKit } returns marketKit
        every { marketKit.token(any<TokenQuery>()) } answers { tokens[firstArg()] }
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()
        providers.forEach { it.setThornodeApi(api) }
        coEvery { api.securedAssets() } returns listOf(ThornodeAPI.Response.SecuredAsset("BTC-BTC"))
    }

    @After
    fun tearDown() {
        providers.zip(originalApis).forEach { (provider, original) -> provider.setThornodeApi(original) }
        unmockkAll()
        stopKoin()
    }

    @Test
    fun start_thorchainPools_mapsRuneTcyAndSecuredAssets() = runTest {
        pools("ETH.ETH", "THOR.TCY", "GAIA.ATOM")

        ThorChainProvider.start()

        assertTrue(ThorChainProvider.supports(eth, rune))
        assertTrue(ThorChainProvider.supports(eth, tcy))
        assertTrue(ThorChainProvider.supports(rune, securedBtc))
    }

    @Test
    fun start_securedAssetsUnavailable_keepsPools() = runTest {
        pools("ETH.ETH", "THOR.TCY")
        coEvery { api.securedAssets() } throws IOException("offline")

        ThorChainProvider.start()

        assertTrue(ThorChainProvider.supports(eth, tcy))
        assertFalse(ThorChainProvider.supports(eth, securedBtc))
    }

    @Test
    fun start_maya_mapsCacaoAndThorRuneWithoutSecuredAssets() = runTest {
        pools("THOR.RUNE", "ETH.ETH")

        MayaProvider.start()

        assertTrue(MayaProvider.supports(cacao, rune))
        assertTrue(MayaProvider.supports(eth, cacao))
        coVerify(exactly = 0) { api.securedAssets() }
    }

    @Test
    fun fetchFinalQuote_runeWithoutInboundAddress_depositsRune() = runTest {
        pools("ETH.ETH")
        ThorChainProvider.start()
        stubQuote(inboundAddress = null)

        val quote = finalQuote(ThorChainProvider, rune, eth, BigDecimal("1.5"))

        assertEquals(
            SendTransactionData.Thorchain.Deposit("THOR.RUNE", BigDecimal("1.5"), MEMO),
            quote.sendTransactionData,
        )
        coVerify { api.quoteSwap("THOR.RUNE", "ETH.ETH", 150_000_000, any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun fetchFinalQuote_runeWithInboundAddress_sendsToVault() = runTest {
        pools("THOR.RUNE")
        MayaProvider.start()
        stubQuote(inboundAddress = VAULT)

        val quote = finalQuote(MayaProvider, rune, cacao, BigDecimal("1.5"))

        assertEquals(
            SendTransactionData.Thorchain.Send(VAULT, BigDecimal("1.5"), MEMO),
            quote.sendTransactionData,
        )
    }

    @Test
    fun fetchFinalQuote_cacaoInput_scalesAmountByTenDecimals() = runTest {
        pools("THOR.RUNE")
        MayaProvider.start()
        stubQuote(inboundAddress = null)

        finalQuote(MayaProvider, cacao, rune, BigDecimal("1.5"))

        coVerify { api.quoteSwap("MAYA.CACAO", "THOR.RUNE", 15_000_000_000, any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun fetchFinalQuote_cacaoOutput_scalesAmountOutByTenDecimals() = runTest {
        pools("THOR.RUNE")
        MayaProvider.start()
        stubQuote(inboundAddress = VAULT, expectedAmountOut = BigDecimal(15_000_000_000))

        val quote = finalQuote(MayaProvider, rune, cacao, BigDecimal.ONE)

        assertEquals(0, BigDecimal("1.5").compareTo(quote.amountOut))
    }

    // The providers are singletons that read the API field directly, so a getter stub never reaches start().
    private fun BaseThorChainProvider.setThornodeApi(value: ThornodeAPI) {
        BaseThorChainProvider::class.java.getDeclaredField("thornodeAPI").apply { isAccessible = true }.set(this, value)
    }

    private fun pools(vararg assets: String) {
        coEvery { api.pools() } returns assets.map { ThornodeAPI.Response.Pool(it, "Available") }
    }

    private fun stubQuote(inboundAddress: String?, expectedAmountOut: BigDecimal = BigDecimal(100_000_000)) {
        coEvery { api.quoteSwap(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            ThornodeAPI.Response.QuoteSwap(
                inbound_address = inboundAddress,
                fees = ThornodeAPI.Response.QuoteSwap.Fees(
                    affiliate = BigDecimal.ZERO,
                    outbound = BigDecimal.ZERO,
                    liquidity = BigDecimal.ZERO,
                    total = BigDecimal.ZERO,
                ),
                router = null,
                expiry = 0,
                dust_threshold = null,
                recommended_gas_rate = null,
                memo = MEMO,
                expected_amount_out = expectedAmountOut,
            )
    }

    private suspend fun finalQuote(
        provider: BaseThorChainProvider,
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
    ) = provider.fetchFinalQuote(
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        amountIn = amountIn,
        swapSettings = mapOf("recipient" to Address(RECIPIENT)),
        sendTransactionSettings = null,
        swapQuote = SwapQuoteThorChain(
            amountOut = BigDecimal.ONE,
            priceImpact = null,
            fields = emptyList(),
            settings = emptyList(),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = null,
            cautions = emptyList(),
            slippageThreshold = BigDecimal.ZERO,
        ),
    )

    private fun token(blockchainType: BlockchainType, type: TokenType, code: String, decimals: Int) = Token(
        coin = Coin(uid = code.lowercase(), name = code, code = code),
        blockchain = Blockchain(blockchainType, blockchainType.uid, null),
        type = type,
        decimals = decimals,
    )

    private companion object {
        const val MEMO = "=:ETH.ETH:0xrecipient"
        const val VAULT = "thor1vault"
        const val RECIPIENT = "0x1111111111111111111111111111111111111111"
    }
}
