package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.network.quickex.domain.entity.ClaimedPublicRate
import cash.p.terminal.network.quickex.domain.entity.DepositAddress
import cash.p.terminal.network.quickex.domain.entity.InstrumentInfo
import cash.p.terminal.network.quickex.domain.entity.NewTransactionQuickexResponse
import cash.p.terminal.network.quickex.domain.entity.Pair
import cash.p.terminal.network.quickex.domain.entity.QuickexInstrument
import cash.p.terminal.network.quickex.domain.repository.QuickexRepository
import cash.p.terminal.network.swaprepository.SwapProvider
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
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

class QuickexProviderTest {

    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val quickexRepository = mockk<QuickexRepository>(relaxed = true)
    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)

    private val tokenIn = nativeTestToken(BlockchainType.Bitcoin, "BTC")
    private val tokenOut = nativeTestToken(BlockchainType.Litecoin, "LTC")
    private val rune = nativeTestToken(BlockchainType.Thorchain, "RUNE")

    @Before
    fun setUp() {
        every { accountManager.activeAccount } returns buildTestAccount("acc-1")
        startKoin {
            modules(module { single { marketKit } })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun onTransactionCompleted_savesTransactionWithRecordUidAndUpdatedDate() {
        val provider = createProvider()
        val transaction = buildSwapProviderTransaction(SwapProvider.QUICKEX, "qx-tx-555")
        val result = mockk<SendTransactionResult>(relaxed = true)
        every { result.getRecordUid() } returns "record-77"

        provider.onTransactionCompleted(transaction, result)

        verify {
            storage.save(
                match {
                    it.transactionId == "qx-tx-555" &&
                        it.outgoingRecordUid == "record-77" &&
                        it.date >= transaction.date
                }
            )
        }
    }

    @Test
    fun activeAccountChanged_invalidatesZcashTransparentAddressCache() = runTest {
        val transparentToken = mockZcashToken(TokenType.AddressSpecType.Transparent)
        marketKit.stubZcashTransparentToken(transparentToken)
        coEvery { walletUseCase.getOneTimeReceiveAddress(transparentToken) } returnsMany
            listOf("addr-1", "addr-2")

        val zcashUnifiedToken = mockZcashToken(TokenType.AddressSpecType.Unified)
        val anyToken = mockk<Token>(relaxed = true)

        val provider = createProvider()

        val before = provider.getWarningMessage(zcashUnifiedToken, anyToken)
        assertEquals("addr-1", before.formatArgFirst())

        every { accountManager.activeAccount } returns buildTestAccount("acc-2")

        val after = provider.getWarningMessage(zcashUnifiedToken, anyToken)
        assertEquals("addr-2", after.formatArgFirst())
    }

    @Test
    fun sameAccount_reusesZcashTransparentAddressCache() = runTest {
        val transparentToken = mockZcashToken(TokenType.AddressSpecType.Transparent)
        marketKit.stubZcashTransparentToken(transparentToken)
        coEvery { walletUseCase.getOneTimeReceiveAddress(transparentToken) } returns "addr-cached"

        val zcashUnifiedToken = mockZcashToken(TokenType.AddressSpecType.Unified)
        val anyToken = mockk<Token>(relaxed = true)

        val provider = createProvider()

        repeat(3) { provider.getWarningMessage(zcashUnifiedToken, anyToken) }

        coEvery { walletUseCase.getOneTimeReceiveAddress(transparentToken) } returns "addr-fresh"

        val cached = provider.getWarningMessage(zcashUnifiedToken, anyToken)
        assertEquals("addr-cached", cached.formatArgFirst())
    }

    @Test
    fun getWarningMessage_nonZcashTokenIn_returnsNull() = runTest {
        val provider = createProvider()

        assertNull(provider.getWarningMessage(mockNonZcashNativeToken(), mockk(relaxed = true)))
    }

    @Test
    fun onTransactionCompleted_afterFinalQuote_nextFinalQuoteCreatesNewTransaction() = runTest {
        coEvery { quickexRepository.createTransaction(any()) } returns newTransactionResponse()
        val provider = createProvider()

        provider.fetchFinalQuote()
        provider.fetchFinalQuote()
        provider.onTransactionCompleted(
            buildSwapProviderTransaction(SwapProvider.QUICKEX, "qx-tx-555"),
            mockk(relaxed = true),
        )
        provider.fetchFinalQuote()

        coVerify(exactly = 2) { quickexRepository.createTransaction(any()) }
    }

    @Test
    fun supports_rune_trueInBothDirections() = runTest {
        val provider = createStartedProviderWithRune()

        assertTrue(provider.supports(rune, tokenOut))
        assertTrue(provider.supports(tokenIn, rune))
    }

    @Test
    fun fetchFinalQuote_runeIn_sendsToDepositAddressWithMemo() = runTest {
        coEvery { quickexRepository.createTransaction(any()) } returns newTransactionResponse(memo = "7788")
        coEvery { walletUseCase.getReceiveAddress(rune) } returns THOR_ADDRESS
        val provider = createStartedProviderWithRune()

        val quote = provider.fetchFinalQuote(rune, tokenOut) as SwapFinalQuoteEvm

        assertEquals(SendTransactionData.Thorchain.Send("deposit", BigDecimal.ONE, "7788"), quote.sendTransactionData)
        coVerify {
            quickexRepository.createTransaction(
                match { it.instrumentFrom.networkTitle == "RUNE" && it.refundAddress == THOR_ADDRESS }
            )
        }
    }

    @Test
    fun fetchFinalQuote_runeOut_paysToThorAddress() = runTest {
        coEvery { quickexRepository.createTransaction(any()) } returns newTransactionResponse()
        coEvery { walletUseCase.getReceiveAddress(rune) } returns THOR_ADDRESS
        val provider = createStartedProviderWithRune()

        provider.fetchFinalQuote(tokenIn, rune)

        coVerify {
            quickexRepository.createTransaction(
                match { it.instrumentTo.networkTitle == "RUNE" && it.destinationAddress == THOR_ADDRESS }
            )
        }
    }

    private suspend fun createStartedProviderWithRune() = createProvider().apply {
        coEvery { quickexRepository.getAvailablePairs() } returns
            listOf(instrument("RUNE", requiresMemo = true), instrument("BTC"), instrument("LTC"))
        start()
    }

    private fun instrument(ticker: String, requiresMemo: Boolean = false) = QuickexInstrument(
        currencyTitle = ticker,
        networkTitle = ticker,
        currencyFriendlyTitle = ticker,
        slug = ticker.lowercase(),
        precisionDecimals = 8,
        requiresMemo = requiresMemo,
        bestChangeName = ticker,
        contractAddress = "",
    )

    private suspend fun QuickexProvider.fetchFinalQuote(
        from: Token = tokenIn,
        to: Token = tokenOut,
    ) = fetchFinalQuote(from, to, BigDecimal.ONE, emptyMap(), null, mockk(relaxed = true))

    private fun newTransactionResponse(memo: String? = null): NewTransactionQuickexResponse {
        val instrument = InstrumentInfo(currencyTitle = "BTC", networkTitle = "BTC")
        return NewTransactionQuickexResponse(
            depositAddress = DepositAddress(instrument, depositAddress = "deposit", depositAddressMemo = memo),
            orderId = "qx-order-1",
            pair = Pair(instrument, instrument),
            claimedDepositAmount = BigDecimal.ONE,
            amountToGet = BigDecimal("0.5"),
            claimedPublicRate = ClaimedPublicRate(BigDecimal.ONE, BigDecimal("0.5"), null),
        )
    }

    private fun createProvider() = QuickexProvider(
        walletUseCase = walletUseCase,
        quickexRepository = quickexRepository,
        accountManager = accountManager,
        providerSupport = buildOffChainSwapProviderSupport(
            walletUseCase = walletUseCase,
            accountManager = accountManager,
            storage = storage,
            marketKit = marketKit,
        ),
    )

    private companion object {
        const val THOR_ADDRESS = "thor1user"
    }
}
