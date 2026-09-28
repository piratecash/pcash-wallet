package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.multiswap.providers.yiFiTestToken
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCurrency
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class BackendSwapAssetResolverTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<BackendSwapRepository>()
    private val resolver = BackendSwapAssetResolver(
        backendSwapRepository = repository,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    private val btc = yiFiTestToken(BlockchainType.Bitcoin, TokenType.Native, "BTC", coinGeckoId = "bitcoin")
    private val bnb = yiFiTestToken(
        BlockchainType.BinanceSmartChain, TokenType.Native, "BNB", coinGeckoId = "binancecoin"
    )
    private val usdt = yiFiTestToken(
        BlockchainType.Ethereum, TokenType.Eip20(USDT_CONTRACT), "USDT", coinGeckoId = "tether"
    )
    private val stellarUsdc = yiFiTestToken(
        BlockchainType.Stellar, TokenType.Asset("USDC", STELLAR_USDC_ISSUER), "USDC", coinGeckoId = "usd-coin"
    )

    @Test
    fun resolve_nativeWithSingleRow_returnsBackendNetworkName() = runTest(dispatcher) {
        stubCurrencies(currency("binancecoin", "binance_smart_chain"))

        assertEquals(BackendSwapAsset("binancecoin", "binance_smart_chain"), resolve(bnb))
    }

    @Test
    fun resolve_ambiguousRows_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("bitcoin", "bitcoin"), currency("bitcoin", "bitcoin"))

        assertNull(resolve(btc))
    }

    @Test
    fun resolve_nativeWhoseOnlyMatchingRowHasContract_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("bitcoin", "bitcoin", "0x0000000000000000000000000000000000000001"))

        assertNull(resolve(btc))
    }

    @Test
    fun resolve_nativeWithSameCoinIdContractRowPresent_returnsNull() = runTest(dispatcher) {
        stubCurrencies(
            currency("bitcoin", "bitcoin"),
            currency("bitcoin", "bitcoin", "0x0000000000000000000000000000000000000001"),
        )

        assertNull(resolve(btc))
    }

    @Test
    fun resolve_missingCoinGeckoId_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("btc", "bitcoin"))

        assertNull(resolve(yiFiTestToken(BlockchainType.Bitcoin, TokenType.Native, "BTC")))
    }

    @Test
    fun resolve_unmappedChain_returnsNullWithoutLoading() = runTest(dispatcher) {
        val xdai = yiFiTestToken(BlockchainType.Gnosis, TokenType.Native, "XDAI", coinGeckoId = "xdai")

        assertNull(resolve(xdai))
        coVerify(exactly = 0) { repository.getCurrencies(any()) }
    }

    @Test
    fun resolve_contractWithoutBackendContractAddress_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("tether", "ethereum"))

        assertNull(resolve(usdt))
    }

    @Test
    fun resolve_contractWithSameAddressInOtherCase_returnsAsset() = runTest(dispatcher) {
        stubCurrencies(currency("tether", "ethereum", USDT_CONTRACT.uppercase().replace("0X", "0x")))

        assertEquals(BackendSwapAsset("tether", "ethereum"), resolve(usdt))
    }

    @Test
    fun resolve_contractWithDifferentAddress_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("tether", "ethereum", "0x0000000000000000000000000000000000000001"))

        assertNull(resolve(usdt))
    }

    @Test
    fun resolve_contractWithBackendCoinIdDifferentFromOurs_returnsBackendCoinId() = runTest(dispatcher) {
        stubCurrencies(currency("tether", "binance_smart_chain", BSC_USDT_CONTRACT))
        val bscUsdt = yiFiTestToken(
            BlockchainType.BinanceSmartChain,
            TokenType.Eip20(BSC_USDT_CONTRACT),
            "USDT",
            coinGeckoId = "binance-bridged-usdt-bnb-smart-chain",
        )

        assertEquals(BackendSwapAsset("tether", "binance_smart_chain"), resolve(bscUsdt))
    }

    @Test
    fun resolve_contractWithoutCoinGeckoId_returnsAsset() = runTest(dispatcher) {
        stubCurrencies(currency("tether", "ethereum", USDT_CONTRACT))
        val usdtWithoutGeckoId = yiFiTestToken(BlockchainType.Ethereum, TokenType.Eip20(USDT_CONTRACT), "USDT")

        assertEquals(BackendSwapAsset("tether", "ethereum"), resolve(usdtWithoutGeckoId))
    }

    @Test
    fun resolve_twoRowsWithSameNetworkAndContract_returnsNull() = runTest(dispatcher) {
        stubCurrencies(
            currency("tether", "ethereum", USDT_CONTRACT),
            currency("usdt0", "ethereum", USDT_CONTRACT),
        )

        assertNull(resolve(usdt))
    }

    @Test
    fun resolve_sameContractOnOtherNetwork_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("tether", "binance_smart_chain", USDT_CONTRACT))

        assertNull(resolve(usdt))
    }

    @Test
    fun resolve_stellarAssetSharingNativeCoinId_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("stellar", "stellar"))
        val stellarAsset = yiFiTestToken(
            BlockchainType.Stellar, TokenType.Asset("USDC", "GISSUER"), "USDC", coinGeckoId = "stellar"
        )

        assertNull(resolve(stellarAsset))
    }

    @Test
    fun resolve_stellarAssetWithLowercasedBackendContract_returnsAsset() = runTest(dispatcher) {
        stubCurrencies(currency("usd-coin", "stellar", "usdc-${STELLAR_USDC_ISSUER.lowercase()}"))

        assertEquals(BackendSwapAsset("usd-coin", "stellar"), resolve(stellarUsdc))
    }

    @Test
    fun resolve_stellarAssetFromOtherIssuer_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("usd-coin", "stellar", "usdc-${OTHER_STELLAR_ISSUER.lowercase()}"))

        assertNull(resolve(stellarUsdc))
    }

    @Test
    fun resolve_splContractInOtherCase_returnsNull() = runTest(dispatcher) {
        stubCurrencies(currency("usd-coin", "solana", SOLANA_USDC_MINT.lowercase()))
        val solanaUsdc = yiFiTestToken(
            BlockchainType.Solana, TokenType.Spl(SOLANA_USDC_MINT), "USDC", coinGeckoId = "usd-coin"
        )

        assertNull(resolve(solanaUsdc))
    }

    @Test
    fun resolve_loadFailed_retriesOnNextCall() = runTest(dispatcher) {
        coEvery { repository.getCurrencies(PROVIDER) } throws IOException("offline") andThen
            listOf(currency("bitcoin", "bitcoin"))

        assertFailsWith<IOException> { resolve(btc) }
        assertEquals(BackendSwapAsset("bitcoin", "bitcoin"), resolve(btc))
    }

    @Test
    fun resolve_calledTwiceWithinTtl_loadsCatalogOnce() = runTest(dispatcher) {
        stubCurrencies(currency("bitcoin", "bitcoin"))

        resolve(btc)
        resolve(btc)

        coVerify(exactly = 1) { repository.getCurrencies(PROVIDER) }
    }

    private suspend fun resolve(token: Token) = resolver.resolve(PROVIDER, token)

    private fun stubCurrencies(vararg currencies: BackendSwapCurrency) {
        coEvery { repository.getCurrencies(PROVIDER) } returns currencies.toList()
    }

    private fun currency(coinId: String, blockchain: String, contractAddress: String? = null) = BackendSwapCurrency(
        ticker = coinId,
        coinId = coinId,
        blockchain = blockchain,
        contractAddress = contractAddress,
    )

    private companion object {
        const val PROVIDER = "changelly"
        const val USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val BSC_USDT_CONTRACT = "0x55d398326f99059ff775485246999027b3197955"
        const val STELLAR_USDC_ISSUER = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
        const val OTHER_STELLAR_ISSUER = "GDSTRSHXHGJ7ZIVRBXEYE5Q74XUVCUSEKEBR7UCHEUUEK72N7I7KJ6JH"
        const val SOLANA_USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    }
}
