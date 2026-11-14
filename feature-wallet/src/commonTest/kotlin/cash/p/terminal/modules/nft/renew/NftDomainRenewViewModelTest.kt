package cash.p.terminal.modules.nft.renew

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.NftBroadcastResult
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.adapters.DomainRenewalTimeoutException
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.modules.nft.NftOwnerWalletResolver
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.nft.operation.PREPARE_RETRY_INTERVAL
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.register
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.insufficient_balance_required_available
import cash.p.terminal.resources.swap_insufficient_fee_balance
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.core.helpers.DateHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NftDomainRenewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private val tonToken = Token(
        coin = Coin(uid = "the-open-network", name = "Toncoin", code = "TON"),
        blockchain = Blockchain(BlockchainType.Ton, "TON", null),
        type = TokenType.Native,
        decimals = 9,
    )
    private val ownerA = NftOwner("account", BlockchainType.Ton, "owner-a")
    private val ownerB = NftOwner("account", BlockchainType.Ton, "owner-b")
    private val walletA = wallet()
    private val walletB = wallet()
    private val adapterA = adapter(available = "1")
    private val adapterB = adapter(available = "1")
    private val resolver = mockk<NftOwnerWalletResolver> {
        coEvery { wallet(ownerA) } returns walletA
        coEvery { wallet(ownerB) } returns walletB
    }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true) {
        every { getAdapterForWallet<ISendTonAdapter>(walletA) } returns adapterA
        every { getAdapterForWallet<ISendTonAdapter>(walletB) } returns adapterB
        every { getAdjustedBalanceData(any()) } returns null
    }
    private val visibilityChanges = MutableSharedFlow<Unit>()
    private val balanceHiddenManager = mockk<IBalanceHiddenManager> {
        every { anyWalletVisibilityChangedFlow } returns visibilityChanges
        every { balanceHidden } returns false
        every { isWalletBalanceHidden(any()) } returns false
    }
    private val offlineGate = mockk<OfflineOperationGate> {
        every { isBlocked(any<Wallet>()) } returns false
        every { blockedFlow(any()) } returns flowOf(false)
    }
    private var rate: CurrencyValue? = null
    private val xRateService = mockk<XRateService> {
        every { getRate(any()) } answers { rate }
        every { getRateFlow(any()) } returns emptyFlow()
    }
    private val numberFormatter = mockk<IAppNumberFormatter> {
        every { formatCoinFull(any(), any(), any()) } answers {
            "${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()} ${secondArg<String>()}"
        }
        every { formatFiatFull(any(), any()) } answers {
            "${secondArg<String>()}${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()}"
        }
    }

    private val cancellation = IllegalStateException("cancelled on device")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(DateHelper, HardwareWalletSupport)
        every { DateHelper.formatDate(any(), any()) } returns RENEW_UNTIL
        every { HardwareWalletSupport.isUserCancellation(any()) } answers { firstArg<Throwable>() === cancellation }
        givenEstimate(adapterA, fee = "0.01", required = "0.05")
        givenEstimate(adapterB, fee = "0.02", required = "0.04")
        coEvery { adapterA.renewDomains(any()) } answers { NftBroadcastResult(firstArg(), error = null) }
        coEvery { adapterB.renewDomains(any()) } answers { NftBroadcastResult(firstArg(), error = null) }
        startKoin { modules(module { single { numberFormatter } }) }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun uiState_twoOwners_costForAllDomainsAndFeeSummedWithFiat() = runTest(dispatcher) {
        rate = CurrencyValue(Currency("USD", "$", 2), BigDecimal("2"))
        val viewModel = createViewModel(twoOwnerDomains)

        assertEquals("0.06 TON", viewModel.uiState.cost)
        assertEquals("≈ 0.03 TON · $0.06", viewModel.uiState.fee)
        assertEquals(listOf("a1.ton", "a2.ton", "b1.ton"), viewModel.uiState.domainNames)
        assertTrue(viewModel.uiState.canRenew)
    }

    @Test
    fun uiState_created_renewUntilIsOneYearAhead() = runTest(dispatcher) {
        val date = slot<Date>()
        every { DateHelper.formatDate(capture(date), "d MMMM yyyy") } returns RENEW_UNTIL

        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        val expected = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }
        val actual = Calendar.getInstance().apply { time = date.captured }
        assertEquals(RENEW_UNTIL, viewModel.uiState.renewUntil)
        assertEquals(expected.get(Calendar.YEAR), actual.get(Calendar.YEAR))
        assertEquals(expected.get(Calendar.DAY_OF_YEAR), actual.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun uiState_secondOwnerBalanceBelowRequired_insufficientAndRenewBlocked() = runTest(dispatcher) {
        every { adapterB.balanceData } returns BalanceData(BigDecimal("0.03"))
        val viewModel = createViewModel(twoOwnerDomains)

        val text = viewModel.uiState.insufficientBalance as TranslatableString.ResString
        assertEquals(Res.string.insufficient_balance_required_available, text.id)
        assertFalse(viewModel.uiState.canRenew)

        viewModel.onRenew()
        advanceUntilIdle()

        coVerify(exactly = 0) { adapterA.renewDomains(any()) }
    }

    @Test
    fun uiState_balanceHidden_costAndFeeMaskedAndGenericInsufficientText() = runTest(dispatcher) {
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns true
        every { adapterA.balanceData } returns BalanceData(BigDecimal("0.01"))
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        assertEquals("*****", viewModel.uiState.cost)
        assertEquals("*****", viewModel.uiState.fee)
        val text = viewModel.uiState.insufficientBalance as TranslatableString.ResString
        assertEquals(Res.string.swap_insufficient_fee_balance, text.id)
    }

    @Test
    fun canRenew_feeEstimateFails_falseWithoutFee() = runTest(dispatcher) {
        coEvery { adapterA.estimateDomainRenewalFee(any()) } throws IllegalStateException("network")
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        assertEquals("---", viewModel.uiState.fee)
        assertFalse(viewModel.uiState.canRenew)
        // Stops the endless retry before runTest drains the scheduler.
        viewModelStore.clear()
    }

    @Test
    fun canRenew_feeEstimateFailedOnIdleWallet_retriedAfterIntervalAndAllowed() = runTest(dispatcher) {
        val balanceUpdates = MutableSharedFlow<Unit>()
        every { adapterA.balanceUpdatedFlow } returns balanceUpdates
        coEvery { adapterA.estimateDomainRenewalFee(any()) } throws IllegalStateException("network") andThen
            TonFeeEstimate(BigDecimal("0.01"), BigDecimal("0.05"))
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        balanceUpdates.emit(Unit)
        advanceTimeBy(PREPARE_RETRY_INTERVAL - 1.milliseconds)
        runCurrent()
        assertFalse(viewModel.uiState.canRenew)
        coVerify(exactly = 1) { adapterA.estimateDomainRenewalFee(any()) }

        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertTrue(viewModel.uiState.canRenew)
        assertEquals("≈ 0.01 TON", viewModel.uiState.fee)
        coVerify(exactly = 2) { adapterA.estimateDomainRenewalFee(any()) }
    }

    @Test
    fun init_networkPaused_estimatesOnlyAfterResume() = runTest(dispatcher) {
        every { offlineGate.isBlocked(walletA) } returns true
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))
        coVerify(exactly = 0) { adapterA.estimateDomainRenewalFee(any()) }

        every { offlineGate.isBlocked(walletA) } returns false
        advanceTimeBy(PREPARE_RETRY_INTERVAL)
        runCurrent()

        coVerify(exactly = 1) { adapterA.estimateDomainRenewalFee(any()) }
        assertTrue(viewModel.uiState.canRenew)
    }

    @Test
    fun canRenew_adapterPublishedAfterFirstAttempt_preparedAfterInterval() = runTest(dispatcher) {
        every { adapterManager.getAdapterForWallet<ISendTonAdapter>(walletA) } returns null andThen adapterA
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        assertFalse(viewModel.uiState.canRenew)
        assertEquals("---", viewModel.uiState.fee)

        advanceTimeBy(PREPARE_RETRY_INTERVAL)
        runCurrent()

        assertTrue(viewModel.uiState.canRenew)
        assertEquals("0.02 TON", viewModel.uiState.cost)
        assertEquals("≈ 0.01 TON", viewModel.uiState.fee)
    }

    @Test
    fun domainNames_balanceHiddenNotRevealed_masked() = runTest(dispatcher) {
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns true
        val viewModel = createViewModel(listOf(domain(ownerA, "a1"), domain(ownerA, "a2")))

        assertEquals(listOf("****", "****"), viewModel.uiState.domainNames)
    }

    @Test
    fun domainNames_balanceHiddenRevealedByCaller_shown() = runTest(dispatcher) {
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns true
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")), namesRevealed = true)

        assertEquals(listOf("a1.ton"), viewModel.uiState.domainNames)
        assertEquals("*****", viewModel.uiState.cost)
    }

    @Test
    fun domainNames_balanceHiddenWhileOpen_masked() = runTest(dispatcher) {
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))
        assertEquals(listOf("a1.ton"), viewModel.uiState.domainNames)

        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns true
        visibilityChanges.emit(Unit)
        runCurrent()

        assertEquals(listOf("****"), viewModel.uiState.domainNames)
    }

    @Test
    fun domainNames_ownersNotResolvedAndGlobalBalanceHidden_masked() = runTest(dispatcher) {
        every { balanceHiddenManager.balanceHidden } returns true
        coEvery { resolver.wallet(ownerA) } returns null
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        assertEquals(listOf("****"), viewModel.uiState.domainNames)
        viewModelStore.clear()
    }

    @Test
    fun canRenew_ownerAdapterNotSynced_false() = runTest(dispatcher) {
        every { adapterB.balanceState } returns AdapterState.Syncing()
        val viewModel = createViewModel(twoOwnerDomains)

        assertFalse(viewModel.uiState.canRenew)
    }

    @Test
    fun canRenew_ownerWalletNotActive_false() = runTest(dispatcher) {
        coEvery { resolver.wallet(ownerB) } returns null
        val viewModel = createViewModel(twoOwnerDomains)

        assertFalse(viewModel.uiState.canRenew)
        viewModelStore.clear()
    }

    @Test
    fun onRenew_singleOwnerAllSent_done() = runTest(dispatcher) {
        val viewModel = createViewModel(listOf(domain(ownerA, "a1"), domain(ownerA, "a2")))

        viewModel.onRenew()
        advanceUntilIdle()

        assertEquals(NftOperationOutcome.Done(sent = 2), viewModel.uiState.outcome)
        coVerify(exactly = 1) { adapterA.renewDomains(listOf("a1", "a2")) }
    }

    @Test
    fun onRenew_secondOwnerFails_endedWithSentCountAndCaution() = runTest(dispatcher) {
        coEvery { adapterB.renewDomains(any()) } returns NftBroadcastResult(emptyList(), IllegalStateException("boom"))
        val viewModel = createViewModel(twoOwnerDomains)

        viewModel.onRenew()
        advanceUntilIdle()

        val outcome = viewModel.uiState.outcome as NftOperationOutcome.Ended
        assertEquals(2, outcome.sent)
        assertEquals(3, outcome.total)
        assertEquals("boom", (outcome.caution.s as TranslatableString.PlainString).text)
    }

    @Test
    fun onRenew_firstOwnerFails_secondOwnerNotSent() = runTest(dispatcher) {
        coEvery { adapterA.renewDomains(any()) } returns NftBroadcastResult(emptyList(), IllegalStateException("boom"))
        val viewModel = createViewModel(twoOwnerDomains)

        viewModel.onRenew()
        advanceUntilIdle()

        assertEquals(0, (viewModel.uiState.outcome as NftOperationOutcome.Ended).sent)
        coVerify(exactly = 0) { adapterB.renewDomains(any()) }
    }

    @Test
    fun onRenew_seqnoTimeoutAfterFirstChunk_endedWithBroadcastPart() = runTest(dispatcher) {
        coEvery { adapterA.renewDomains(any()) } returns
            NftBroadcastResult(listOf("a1"), DomainRenewalTimeoutException())
        val viewModel = createViewModel(twoOwnerDomains)

        viewModel.onRenew()
        advanceUntilIdle()

        val outcome = viewModel.uiState.outcome as NftOperationOutcome.Ended
        assertEquals(1, outcome.sent)
        assertEquals(3, outcome.total)
        coVerify(exactly = 0) { adapterB.renewDomains(any()) }
    }

    @Test
    fun onRenew_deviceCancelOnFirstChunk_idleAndRenewAllowedAgain() = runTest(dispatcher) {
        coEvery { adapterA.renewDomains(any()) } returns NftBroadcastResult(emptyList(), cancellation)
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        viewModel.onRenew()
        advanceUntilIdle()

        assertEquals(NftOperationOutcome.Idle, viewModel.uiState.outcome)
        assertTrue(viewModel.uiState.canRenew)

        coEvery { adapterA.renewDomains(any()) } answers { NftBroadcastResult(firstArg(), error = null) }
        viewModel.onRenew()
        advanceUntilIdle()

        assertEquals(NftOperationOutcome.Done(sent = 1), viewModel.uiState.outcome)
        coVerify(exactly = 2) { adapterA.renewDomains(any()) }
    }

    @Test
    fun onRenew_deviceCancelAfterBroadcast_endedWithoutRetry() = runTest(dispatcher) {
        coEvery { adapterB.renewDomains(any()) } returns NftBroadcastResult(emptyList(), cancellation)
        val viewModel = createViewModel(twoOwnerDomains)

        viewModel.onRenew()
        advanceUntilIdle()
        viewModel.onRenew()
        advanceUntilIdle()

        assertEquals(2, (viewModel.uiState.outcome as NftOperationOutcome.Ended).sent)
        assertFalse(viewModel.uiState.canRenew)
        coVerify(exactly = 1) { adapterA.renewDomains(any()) }
    }

    @Test
    fun onRenew_calledTwice_renewsOnce() = runTest(dispatcher) {
        val viewModel = createViewModel(listOf(domain(ownerA, "a1")))

        viewModel.onRenew()
        viewModel.onRenew()
        advanceUntilIdle()
        viewModel.onRenew()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapterA.renewDomains(any()) }
    }

    @Test
    fun onRenew_onSaleDomainInInput_excluded() = runTest(dispatcher) {
        val viewModel = createViewModel(listOf(domain(ownerA, "a1", onSale = true), domain(ownerA, "a2")))

        viewModel.onRenew()
        advanceUntilIdle()

        assertEquals(listOf("a2.ton"), viewModel.uiState.domainNames)
        assertEquals(NftOperationOutcome.Done(sent = 1), viewModel.uiState.outcome)
        coVerify(exactly = 1) { adapterA.renewDomains(listOf("a2")) }
    }

    @Test
    fun uiState_onlyOnSaleDomains_cannotRenew() = runTest(dispatcher) {
        val viewModel = createViewModel(listOf(domain(ownerA, "a1", onSale = true)))

        assertFalse(viewModel.uiState.canRenew)
        assertNull(viewModel.uiState.insufficientBalance)
    }

    private val twoOwnerDomains
        get() = listOf(domain(ownerA, "a1"), domain(ownerA, "a2"), domain(ownerB, "b1"))

    private fun TestScope.createViewModel(
        domains: List<NftDomainExpiry>,
        namesRevealed: Boolean = false,
    ): NftDomainRenewViewModel {
        val viewModel = NftDomainRenewViewModel(
            domains = domains,
            namesRevealed = namesRevealed,
            walletResolver = resolver,
            adapterManager = adapterManager,
            balanceHiddenManager = balanceHiddenManager,
            offlineOperationGate = offlineGate,
            xRateService = xRateService,
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
        viewModelStore.register(viewModel)
        runCurrent()
        return viewModel
    }

    private fun givenEstimate(adapter: ISendTonAdapter, fee: String, required: String) {
        coEvery { adapter.estimateDomainRenewalFee(any()) } returns
            TonFeeEstimate(BigDecimal(fee), BigDecimal(required))
    }

    private fun wallet(): Wallet = mockk {
        every { token } returns tonToken
        every { coin } returns tonToken.coin
    }

    private fun adapter(available: String) = mockk<ISendTonAdapter> {
        every { balanceData } returns BalanceData(BigDecimal(available))
        every { balanceState } returns AdapterState.Synced
        every { balanceUpdatedFlow } returns emptyFlow()
        every { balanceStateUpdatedFlow } returns emptyFlow()
    }

    private fun domain(owner: NftOwner, address: String, onSale: Boolean = false) =
        NftDomainExpiry(owner, address, "$address.ton", expiresAt = 0, isOnSale = onSale)

    private companion object {
        const val RENEW_UNTIL = "14 March 2027"
    }
}
