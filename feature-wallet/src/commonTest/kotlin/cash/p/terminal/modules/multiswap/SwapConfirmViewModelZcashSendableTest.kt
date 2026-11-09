package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ZcashFeeEstimate
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceZCash
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.math.BigDecimal

/** The swap button follows the real Zcash service: a pending estimate must hold it on every provider kind. */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapConfirmViewModelZcashSendableTest : SwapConfirmViewModelTestBase() {

    private val balanceUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val adapter = mockk<ISendZcashAdapter>(relaxed = true) {
        every { maxSpendableBalance } returns BigDecimal("100")
        every { fee } returns MutableStateFlow(BigDecimal.ZERO)
        every { balanceUpdatedFlow } returns balanceUpdates
        coEvery { validate(any()) } returns ZcashAdapter.ZCashAddressType.Transparent
        coEvery { maxSendable(any(), any()) } returns BigDecimal("100")
    }

    @Before
    fun stubZcashService() {
        loadKoinModules(
            module {
                single<PendingTransactionRegistrar> { mockk(relaxed = true) }
                single<WalletUseCase> {
                    mockk(relaxed = true) { coEvery { createWalletIfNotExists(any()) } returns previewWallet }
                }
                single<IAdapterManager> {
                    mockk(relaxed = true) {
                        coEvery { awaitAdapterForWallet<ISendZcashAdapter>(any(), any()) } returns adapter
                    }
                }
                single<MarketKitWrapper> {
                    mockk(relaxed = true) { every { coinPrice(any(), any()) } returns null }
                }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
                single<CurrencyManager> {
                    mockk(relaxed = true) { every { baseCurrency } returns Currency("USD", "$", 2) }
                }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Test
    fun validQuote_offChainEstimatePending_isFalseUntilItArrives() = runTest(dispatcher) {
        assertHeldUntilTheEstimateArrives(mockk<OffChainSwapProvider>(relaxed = true))
    }

    @Test
    fun validQuote_onChainEstimatePending_isFalseUntilItArrives() = runTest(dispatcher) {
        assertHeldUntilTheEstimateArrives(mockk<IMultiSwapProvider>(relaxed = true))
    }

    @Test
    fun loading_offChainEstimatePending_isShownInsteadOfTheRefreshButtonWithoutANewQuote() = runTest(dispatcher) {
        assertLoadingUntilTheEstimateArrives(mockk<OffChainSwapProvider>(relaxed = true))
    }

    @Test
    fun loading_onChainEstimatePending_isShownInsteadOfTheRefreshButtonWithoutANewQuote() = runTest(dispatcher) {
        assertLoadingUntilTheEstimateArrives(mockk<IMultiSwapProvider>(relaxed = true))
    }

    @Test
    fun loading_balanceUpdateReplansTheFee_isShownUntilTheNewEstimate() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(ESTIMATE)
        val provider = mockk<OffChainSwapProvider>(relaxed = true)
        val viewModel = confirmViewModel(provider)
        settle()
        assertFalse(viewModel.uiState.loading)
        val estimate = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { estimate.await() }

        balanceUpdates.emit(Unit)
        settle()
        assertTrue(viewModel.uiState.loading)

        estimate.complete(ZcashFeeEstimate.Planned(ESTIMATE))
        settle()
        assertFalse(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.validQuote)
        coVerify(exactly = 1) { provider.fetchFinalQuote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun refresh_sameFinalQuote_finishesLoading() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(ESTIMATE)
        val viewModel = confirmViewModel(mockk<OffChainSwapProvider>(relaxed = true))
        settle()

        viewModel.refresh()
        settle()

        assertFalse(viewModel.uiState.loading)
    }

    @Test
    fun validQuote_offChainEstimateUnavailable_staysOpenOnTheFallback() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returnsMany
            listOf(ZcashFeeEstimate.Unavailable, ZcashFeeEstimate.Planned(ESTIMATE))

        val viewModel = confirmViewModel(mockk<OffChainSwapProvider>(relaxed = true))
        settle()

        assertTrue(viewModel.uiState.validQuote)
        assertFalse(viewModel.uiState.loading)
    }

    private fun TestScope.assertLoadingUntilTheEstimateArrives(provider: IMultiSwapProvider) {
        val estimate = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { estimate.await() }
        val service = SendTransactionServiceZCash(token)

        val viewModel = confirmViewModel(provider, service)
        settle()
        assertTrue(viewModel.uiState.loading)
        assertFalse(viewModel.uiState.validQuote)
        assertFalse(service.stateFlow.value.sendable)

        estimate.complete(ZcashFeeEstimate.Planned(ESTIMATE))
        settle()
        assertFalse(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.validQuote)
        assertTrue(service.stateFlow.value.sendable)
        coVerify(exactly = 1) { provider.fetchFinalQuote(any(), any(), any(), any(), any(), any()) }
    }

    private fun TestScope.assertHeldUntilTheEstimateArrives(provider: IMultiSwapProvider) {
        val estimate = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { estimate.await() }

        val viewModel = confirmViewModel(provider)
        settle()
        assertFalse(viewModel.uiState.validQuote)

        estimate.complete(ZcashFeeEstimate.Planned(ESTIMATE))
        settle()
        assertTrue(viewModel.uiState.validQuote)
    }

    private fun confirmViewModel(
        provider: IMultiSwapProvider,
        service: SendTransactionServiceZCash = SendTransactionServiceZCash(token),
    ): SwapConfirmViewModel {
        every { provider.mevProtectionAvailable } returns false
        coEvery { provider.fetchFinalQuote(any(), any(), any(), any(), any(), any()) } returns SwapFinalQuoteEvm(
            tokenIn = token,
            tokenOut = token,
            amountIn = BigDecimal.ONE,
            amountOut = BigDecimal.ONE,
            amountOutMin = BigDecimal.ONE,
            sendTransactionData = DEPOSIT,
            priceImpact = null,
            fields = emptyList(),
        )
        return createViewModel(provider, serviceOverride = service)
    }

    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private companion object {
        val ESTIMATE: BigDecimal = BigDecimal("0.00015")
        val DEPOSIT = SendTransactionData.Btc(
            address = "t1deposit",
            memo = "",
            amount = BigDecimal.ONE,
            recommendedGasRate = null,
            minimumSendAmount = null,
            changeToFirstInput = false,
            utxoFilters = UtxoFilters(),
            feesMap = emptyMap(),
        )
    }
}
