package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.network.exolix.domain.entity.ExolixNetwork
import cash.p.terminal.network.exolix.domain.entity.ExolixTransaction
import cash.p.terminal.network.exolix.domain.repository.ExolixRepository
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
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
import org.junit.Assert.assertTrue
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
    private val rune = nativeTestToken(BlockchainType.Thorchain, "RUNE")

    @Before
    fun setUp() {
        every { accountManager.activeAccount } returns buildTestAccount("acc-1")
        coEvery { exolixRepository.getCurrencyNetworks("BTC") } returns listOf(exolixNetwork("BTC"))
        coEvery { exolixRepository.getCurrencyNetworks("LTC") } returns listOf(exolixNetwork("LTC"))
        coEvery { exolixRepository.getCurrencyNetworks("RUNE") } returns
            listOf(exolixNetwork("RUNE", memoNeeded = true))
        coEvery { walletUseCase.getReceiveAddress(rune) } returns THOR_ADDRESS
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

    @Test
    fun supports_rune_trueInBothDirections() = runTest(dispatcher) {
        val provider = createProvider()

        assertTrue(provider.supports(rune, tokenOut))
        assertTrue(provider.supports(tokenIn, rune))
    }

    @Test
    fun fetchFinalQuote_runeIn_sendsToDepositAddressWithMemoAndThorRefund() = runTest(dispatcher) {
        coEvery { exolixRepository.createTransaction(any()) } returns exolixTransaction(memo = "123456")

        val quote = createProvider().fetchFinalQuote(rune, tokenOut) as SwapFinalQuoteEvm

        assertEquals(
            SendTransactionData.Thorchain.Send("deposit", BigDecimal.ONE, "123456"),
            quote.sendTransactionData,
        )
        coVerify {
            exolixRepository.createTransaction(match { it.networkFrom == "RUNE" && it.refundAddress == THOR_ADDRESS })
        }
    }

    @Test
    fun fetchFinalQuote_runeOut_withdrawsToThorAddress() = runTest(dispatcher) {
        createProvider().fetchFinalQuote(tokenIn, rune)

        coVerify {
            exolixRepository.createTransaction(
                match { it.networkTo == "RUNE" && it.withdrawalAddress == THOR_ADDRESS }
            )
        }
    }

    private suspend fun ExolixProvider.fetchFinalQuote(
        from: Token = tokenIn,
        to: Token = tokenOut,
    ) = fetchFinalQuote(from, to, BigDecimal.ONE, emptyMap(), null, mockk(relaxed = true))

    private fun createProvider() = ExolixProvider(
        walletUseCase = walletUseCase,
        exolixRepository = exolixRepository,
        accountManager = accountManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        providerSupport = buildOffChainSwapProviderSupport(walletUseCase, accountManager, storage, marketKit),
    )

    private fun exolixNetwork(network: String, memoNeeded: Boolean = false) = ExolixNetwork(
        network = network,
        name = network,
        shortName = null,
        memoNeeded = memoNeeded,
        memoName = null,
        contract = null,
    )

    private fun exolixTransaction(memo: String? = null) = ExolixTransaction(
        id = "ex-order-1",
        amount = BigDecimal.ONE,
        amountTo = BigDecimal("0.5"),
        createdAt = null,
        updatedAt = null,
        depositAddress = "deposit",
        depositExtraId = memo,
        withdrawalAddress = "withdrawal",
        withdrawalExtraId = null,
        status = "wait",
    )

    private companion object {
        const val THOR_ADDRESS = "thor1user"
    }
}
