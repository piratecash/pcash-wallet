package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.ZcashFeeEstimate
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
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

class SendTransactionServiceZCashTest : KoinTest {

    private lateinit var pendingRegistrar: PendingTransactionRegistrar
    private lateinit var adapter: ISendZcashAdapter
    private lateinit var walletUseCase: WalletUseCase
    private lateinit var adapterManager: IAdapterManager
    private lateinit var marketKit: MarketKitWrapper
    private lateinit var numberFormatter: IAppNumberFormatter
    private lateinit var currencyManager: CurrencyManager

    private val dispatcher = UnconfinedTestDispatcher()

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
                single<DispatcherProvider> { TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)) }
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
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Unavailable
        coEvery { adapter.maxSendable(any(), any()) } returns null

        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns testWallet
        coEvery { adapterManager.awaitAdapterForWallet<ISendZcashAdapter>(any(), any()) } returns adapter

        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2)
        every { marketKit.coinPrice(any(), any()) } returns null

    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun sendable_addressInvalidAndAmountValid_isFalse() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } throws IllegalArgumentException("bad address")

        val service = startedService(address = "invalid")

        assertFalse(service.stateFlow.value.sendable)
    }

    @Test
    fun sendable_addressAndAmountBothValid_isTrue() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent

        val service = startedService(address = "t1valid")

        assertTrue(service.stateFlow.value.sendable)
    }

    @Test
    fun cautions_addressInvalid_surfacesTheAddressError() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } throws IllegalArgumentException("bad address")

        val service = startedService(address = "invalid")

        assertEquals("bad address", service.stateFlow.value.cautions.single().title)
    }

    @Test
    fun cautions_addressValid_areEmpty() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent

        val service = startedService(address = "t1valid")

        assertTrue(service.stateFlow.value.cautions.isEmpty())
    }

    @Test
    fun networkFee_amountAndAddressKnown_isTheEstimateForThem() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent
        coEvery { adapter.estimateFee(BigDecimal("1"), "t1valid", "") } returns ZcashFeeEstimate.Planned(ESTIMATED_FEE)

        val service = startedService(address = "t1valid")

        assertEquals(ESTIMATED_FEE, service.stateFlow.value.networkFee?.primary?.coinValue?.value)
    }

    @Test
    fun availableBalance_addressKnown_isTheMaximumForIt() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent
        coEvery { adapter.maxSendable("t1valid", "") } returns BigDecimal("150")

        val service = startedService(address = "t1valid")

        assertEquals(BigDecimal("150"), service.stateFlow.value.availableBalance)
    }

    @Test
    fun sendable_estimateInFlight_isFalseUntilItArrives() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent
        val estimate = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { estimate.await() }

        val service = startedService(address = "t1valid")
        assertFalse(service.stateFlow.value.sendable)

        estimate.complete(ZcashFeeEstimate.Planned(ESTIMATED_FEE))
        settle()

        assertTrue(service.stateFlow.value.sendable)
    }

    @Test
    fun sendable_noFeasiblePlanForTheAmount_isFalseWithTheInsufficientBalanceError() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.InsufficientBalance

        val service = startedService(address = "t1valid")

        assertFalse(service.stateFlow.value.sendable)
        val caution = service.stateFlow.value.cautions.single()
        assertEquals(CautionViewItem.Type.Error, caution.type)
        assertEquals(TranslatableString.ResString(Res.string.Swap_ErrorInsufficientBalance).toString(), caution.title)
    }

    @Test
    fun sendable_paymentRefused_isFalseWithTheRefusalReason() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent
        coEvery { adapter.estimateFee(any(), any(), any()) } returns
            ZcashFeeEstimate.Refused(IllegalStateException("too large for the device"))

        val service = startedService(address = "t1valid")

        assertFalse(service.stateFlow.value.sendable)
        val caution = service.stateFlow.value.cautions.single()
        assertEquals(CautionViewItem.Type.Error, caution.type)
        assertEquals("too large for the device", caution.title)
    }

    @Test
    fun sendable_otherPlanningFailure_staysOpenWithoutACaution() = runTest(dispatcher) {
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent

        val service = startedService(address = "t1valid")

        assertTrue(service.stateFlow.value.sendable)
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

    private suspend fun TestScope.startedService(address: String) = createService().apply {
        start(backgroundScope)
        setSendTransactionData(sendData(amount = BigDecimal("1"), address = address))
        settle()
    }

    /** The quote runs in backgroundScope, which advanceUntilIdle does not wait for. */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private companion object {
        val ESTIMATED_FEE: BigDecimal = BigDecimal("0.00015")
    }
}
