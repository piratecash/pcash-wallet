package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.App
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal

/**
 * `setSendTransactionData` dispatches address validation onto the base class's real-IO
 * `coroutineScope` field (not the `coroutineScope` param of `start()`), so unlike its siblings
 * this service cannot be driven deterministically with `runTest`/`advanceUntilIdle()`. These
 * tests run for real (`runBlocking`) and poll [SendTransactionServiceZCash.stateFlow] with a
 * bounded timeout instead.
 */
class SendTransactionServiceZCashTest : KoinTest {

    private lateinit var pendingRegistrar: PendingTransactionRegistrar
    private lateinit var adapter: ISendZcashAdapter
    private lateinit var walletUseCase: WalletUseCase
    private lateinit var adapterManager: IAdapterManager
    private lateinit var marketKit: MarketKitWrapper
    private lateinit var numberFormatter: IAppNumberFormatter
    private lateinit var currencyManager: CurrencyManager

    private lateinit var testToken: Token
    private lateinit var testWallet: Wallet

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<PendingTransactionRegistrar> { pendingRegistrar }
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { numberFormatter }
                single<CurrencyManager> { currencyManager }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        pendingRegistrar = mockk(relaxed = true)
        adapter = mockk(relaxed = true)
        walletUseCase = mockk(relaxed = true)
        adapterManager = mockk(relaxed = true)
        marketKit = mockk(relaxed = true)
        numberFormatter = mockk(relaxed = true)
        currencyManager = mockk(relaxed = true)

        val testCoin = Coin(uid = "zcash", name = "Zcash", code = "ZEC")
        testToken = Token(
            coin = testCoin,
            blockchain = Blockchain(type = BlockchainType.Zcash, name = "Zcash", eip3091url = null),
            type = TokenType.Native,
            decimals = 8
        )
        testWallet = mockk(relaxed = true) {
            every { token } returns testToken
            every { coin } returns testCoin
        }

        every { adapter.maxSpendableBalance } returns BigDecimal("100")
        every { adapter.fee } returns MutableStateFlow(BigDecimal.ZERO)
        every { adapter.balanceUpdatedFlow } returns emptyFlow()

        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns testWallet
        coEvery { adapterManager.awaitAdapterForWallet<ISendZcashAdapter>(any(), any()) } returns adapter

        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2, 0)
        every { marketKit.coinPrice(any(), any()) } returns null

        mockkObject(App)
        every { App.currencyManager } returns currencyManager
        every { App.marketKit } returns marketKit
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun sendable_addressInvalidAndAmountValid_isFalse() = runBlocking {
        val validated = CompletableDeferred<Unit>()
        coEvery { adapter.validate(any()) } coAnswers {
            validated.complete(Unit)
            throw IllegalArgumentException("bad address")
        }

        val service = createService()
        service.start(CoroutineScope(Dispatchers.Default))
        service.setSendTransactionData(sendData(amount = BigDecimal("1"), address = "invalid"))
        awaitValidated(validated)

        assertFalse(service.stateFlow.value.sendable)
    }

    @Test
    fun sendable_addressAndAmountBothValid_isTrue() = runBlocking {
        val validated = CompletableDeferred<Unit>()
        coEvery { adapter.validate(any()) } coAnswers {
            validated.complete(Unit)
            ZcashAdapter.ZCashAddressType.Transparent
        }

        val service = createService()
        service.start(CoroutineScope(Dispatchers.Default))
        service.setSendTransactionData(sendData(amount = BigDecimal("1"), address = "t1valid"))
        awaitValidated(validated)

        assertTrue(service.stateFlow.value.sendable)
    }

    @Test
    fun cautions_addressInvalid_surfacesTheAddressError() = runBlocking {
        val validated = CompletableDeferred<Unit>()
        coEvery { adapter.validate(any()) } coAnswers {
            validated.complete(Unit)
            throw IllegalArgumentException("bad address")
        }

        val service = createService()
        service.start(CoroutineScope(Dispatchers.Default))
        service.setSendTransactionData(sendData(amount = BigDecimal("1"), address = "invalid"))
        awaitValidated(validated)

        assertEquals("bad address", service.stateFlow.value.cautions.single().title)
    }

    @Test
    fun cautions_addressValid_areEmpty() = runBlocking {
        val validated = CompletableDeferred<Unit>()
        coEvery { adapter.validate(any()) } coAnswers {
            validated.complete(Unit)
            ZcashAdapter.ZCashAddressType.Transparent
        }

        val service = createService()
        service.start(CoroutineScope(Dispatchers.Default))
        service.setSendTransactionData(sendData(amount = BigDecimal("1"), address = "t1valid"))
        awaitValidated(validated)

        assertTrue(service.stateFlow.value.cautions.isEmpty())
    }

    private fun createService() = SendTransactionServiceZCash(testToken)

    private fun sendData(amount: BigDecimal, address: String) = SendTransactionData.Btc(
        address = address,
        memo = "",
        amount = amount,
        recommendedGasRate = null,
        minimumSendAmount = null,
        changeToFirstInput = false,
        utxoFilters = UtxoFilters(),
        feesMap = emptyMap()
    )

    /**
     * `setSendTransactionData` resolves the address on the base class's real-IO `coroutineScope`
     * field, so waiting for [adapter]'s `validate` call to fire and giving its result a moment to
     * cross back into `stateFlow` is the only deterministic way to observe the outcome here.
     */
    private suspend fun awaitValidated(validated: CompletableDeferred<Unit>) {
        withTimeout(3_000) { validated.await() }
        delay(200)
    }
}
