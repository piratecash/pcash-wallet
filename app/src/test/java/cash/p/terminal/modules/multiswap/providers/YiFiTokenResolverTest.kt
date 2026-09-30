package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.network.yifi.data.repository.YiFiRepository
import cash.p.terminal.network.yifi.domain.entity.YiFiChain
import cash.p.terminal.network.yifi.domain.entity.YiFiToken
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith

internal fun yiFiTestToken(
    blockchainType: BlockchainType,
    type: TokenType,
    code: String,
    coinUid: String = code.lowercase(),
    coinGeckoId: String? = null,
) = Token(
    coin = Coin(uid = coinUid, name = code, code = code, coinGeckoId = coinGeckoId),
    blockchain = Blockchain(blockchainType, blockchainType.uid, null),
    type = type,
    decimals = 8,
)

class YiFiTokenResolverTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<YiFiRepository>()
    private val evmBlockchainManager = mockk<EvmBlockchainManager> {
        every { getChain(BlockchainType.ArbitrumOne) } returns Chain.ArbitrumOne
        every { getChain(BlockchainType.Ethereum) } returns Chain.Ethereum
        every { getChain(BlockchainType.BinanceSmartChain) } returns Chain.BinanceSmartChain
        every { getChain(BlockchainType.Gnosis) } returns Chain.Gnosis
    }
    private val marketKit = mockk<MarketKitWrapper> {
        every { nativeToken(BlockchainType.Tron) } returns yiFiTestToken(BlockchainType.Tron, TokenType.Native, "TRX")
        every { nativeToken(BlockchainType.Ethereum) } returns
            yiFiTestToken(BlockchainType.Ethereum, TokenType.Native, "ETH")
        every { nativeToken(BlockchainType.BinanceSmartChain) } returns
            yiFiTestToken(BlockchainType.BinanceSmartChain, TokenType.Native, "BNB")
    }
    private val resolver = YiFiTokenResolver(
        yiFiRepository = repository,
        evmBlockchainManager = evmBlockchainManager,
        marketKit = marketKit,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    init {
        coEvery { repository.getChains() } returns CHAINS
        coEvery { repository.searchTokens(any(), any()) } coAnswers {
            CATALOG[firstArg<String>() to secondArg<String>()].orEmpty()
        }
    }

    @Test
    fun resolveAsset_evmNative_matchesChainByChainId() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.ArbitrumOne, TokenType.Native, "ETH")

        assertEquals(YiFiAsset("ETH", "ARB"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_btcInSeveralAliases_choosesChainWithContractlessTicker() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84), "BTC")

        assertEquals(YiFiAsset("BTC", "BTC"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_trxNative_resolvesTronNetwork() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Tron, TokenType.Native, "TRX")

        assertEquals(YiFiAsset("TRX", "TRON"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_trc20ByLowercasedContract_resolvesTicker() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Tron, TokenType.Eip20(TRC20_USDT), "USDT")

        assertEquals(YiFiAsset("USDT", "TRON"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_duplicateContractTicker_returnsNull() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Ethereum, TokenType.Eip20(NEIRO_CONTRACT), "NEIRO")

        assertNull(resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_nativeWithDuplicateTickerRow_returnsNull() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Litecoin, TokenType.Derived(TokenType.Derivation.Bip84), "LTC")

        assertNull(resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_tokenTickerAlsoContractless_returnsNull() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.BinanceSmartChain, TokenType.Eip20(BSC_ETH_CONTRACT), "ETH")

        assertNull(resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_contractSearchReturnsOnlyContractlessRow_returnsNull() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Tron, TokenType.Eip20(UNLISTED_TRC20), "USDT")

        assertNull(resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_gnosisChainWithoutCexTokens_returnsNull() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Gnosis, TokenType.Native, "XDAI")

        assertNull(resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_unsupportedKinds_returnNullWithoutNetworkCalls() = runTest(dispatcher) {
        val tokens = listOf(
            yiFiTestToken(BlockchainType.Stellar, TokenType.Asset("USDC", "GISSUER"), "USDC"),
            yiFiTestToken(BlockchainType.Tron, TokenType.Trc10("1002000"), "BTT"),
            yiFiTestToken(BlockchainType.Litecoin, TokenType.Mweb, "LTC"),
            yiFiTestToken(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded), "ZEC"),
        )

        tokens.forEach { assertNull(it.type.toString(), resolver.resolveAsset(it)) }
        coVerify(exactly = 0) { repository.getChains() }
        coVerify(exactly = 0) { repository.searchTokens(any(), any()) }
    }

    @Test
    fun resolveAsset_tonWithUnrelatedCoinGeckoId_resolvesByCoinCode() = runTest(dispatcher) {
        val token = yiFiTestToken(
            BlockchainType.Ton, TokenType.Native, "TON",
            coinUid = "the-open-network", coinGeckoId = "toncoin-wrong",
        )

        assertEquals(YiFiAsset("TON", "TON"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_nativeBeam_notMatchedToBeamGamingNetwork() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Beam, TokenType.Native, "BEAM")

        assertNull(resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_ioErrorThenSuccess_errorNotCached() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Tron, TokenType.Native, "TRX")
        coEvery { repository.getChains() } throws IOException("offline") andThen CHAINS

        assertFailsWith<IOException> { resolver.resolveAsset(token) }
        assertEquals(YiFiAsset("TRX", "TRON"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_cancelledThenRetried_cancellationNotCached() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Tron, TokenType.Native, "TRX")
        coEvery { repository.searchTokens("TRON", "TRX") } throws CancellationException("timeout") andThen
            CATALOG.getValue("TRON" to "TRX")

        assertFailsWith<CancellationException> { resolver.resolveAsset(token) }
        assertEquals(YiFiAsset("TRX", "TRON"), resolver.resolveAsset(token))
    }

    @Test
    fun resolveAsset_successfulNull_cachedWithoutRepeatCalls() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Ethereum, TokenType.Eip20(NEIRO_CONTRACT), "NEIRO")

        assertNull(resolver.resolveAsset(token))
        assertNull(resolver.resolveAsset(token))

        coVerify(exactly = 1) { repository.getChains() }
        coVerify(exactly = 1) { repository.searchTokens("ETH", NEIRO_CONTRACT) }
    }

    @Test
    fun clear_afterSuccessfulNull_looksUpAgain() = runTest(dispatcher) {
        val token = yiFiTestToken(BlockchainType.Ethereum, TokenType.Eip20(NEIRO_CONTRACT), "NEIRO")

        resolver.resolveAsset(token)
        resolver.clear()
        resolver.resolveAsset(token)

        coVerify(exactly = 2) { repository.searchTokens("ETH", NEIRO_CONTRACT) }
    }

    private companion object {
        const val TRC20_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val UNLISTED_TRC20 = "TUnlistedContract000000000000000000"
        const val NEIRO_CONTRACT = "0xNeiro1"
        const val BSC_ETH_CONTRACT = "0x2170Ed0880ac9A55fd8Bd6D6Ad4d3F3C0f1e1a3F"

        fun chain(id: String, chainId: Long?, nativeToken: String, vararg aliases: String) =
            YiFiChain(id, chainId, aliases.toList(), nativeToken)

        fun row(network: String, ticker: String, contract: String? = null) = YiFiToken(network, ticker, contract)

        val CHAINS = listOf(
            chain("ETH", 1, "ETH", "ETH", "ERC20"),
            chain("ARB", 42161, "ETH", "ARBITRUM", "ARB"),
            chain("BSC", 56, "BNB", "BEP20", "BSC", "BNB"),
            chain("DAI", 100, "XDAI", "GNOSIS", "XDAI"),
            chain("BTC", null, "", "BTC"),
            chain("MERLIN", 4200, "BTC", "MERLIN", "BTC"),
            chain("MEZO", 31612, "BTC", "MEZO", "BTC"),
            chain("LTC", null, "", "LTC"),
            chain("TRON", null, "TRX", "TRX", "TRON", "TRC20"),
            chain("TON", 5545, "TON", "TON"),
            chain("BEAM", 4337, "BEAM", "BEAM"),
        )

        val CATALOG = mapOf(
            ("BTC" to "BTC") to listOf(row("BTC", "BTC"), row("BTC", "BTCB", "0xbtcb")),
            ("MERLIN" to "BTC") to listOf(row("MERLIN", "BTC", "0xmerlinbtc")),
            ("MEZO" to "BTC") to listOf(row("MEZO", "BTC", "0xmezobtc")),
            ("LTC" to "LTC") to listOf(row("LTC", "LTC"), row("LTC", "LTC")),
            ("ARB" to "ETH") to listOf(row("ARB", "ETH"), row("ARB", "WETH", "0xweth")),
            ("TRON" to "TRX") to listOf(row("TRON", "TRX")),
            ("TRON" to TRC20_USDT) to listOf(row("TRON", "USDT", TRC20_USDT.lowercase())),
            ("TRON" to "USDT") to listOf(row("TRON", "USDT", TRC20_USDT.lowercase()), row("TRON", "USDTB", "tusdtb")),
            ("TRON" to UNLISTED_TRC20) to listOf(row("TRON", "USDT")),
            ("ETH" to NEIRO_CONTRACT) to listOf(row("ETH", "NEIRO", "0xneiro1")),
            ("ETH" to "NEIRO") to listOf(row("ETH", "NEIRO", "0xneiro1"), row("ETH", "NEIRO", "0xneiro2")),
            ("BSC" to BSC_ETH_CONTRACT) to listOf(row("BSC", "ETH", BSC_ETH_CONTRACT.lowercase())),
            ("BSC" to "ETH") to listOf(row("BSC", "ETH", BSC_ETH_CONTRACT.lowercase()), row("BSC", "ETH")),
            ("TON" to "TON") to listOf(row("TON", "TON")),
            ("BEAM" to "BEAM") to listOf(row("BEAM", "BEAM")),
        )
    }
}
