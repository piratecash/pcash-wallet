package cash.p.terminal.modules.multiswap

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ServiceStateFlow
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.send.mockConnectivityManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
abstract class SwapConfirmViewModelTestBase {

    protected val dispatcher = UnconfinedTestDispatcher()
    private val pendingMultiSwapStorage = mockk<PendingMultiSwapStorage>(relaxed = true)
    protected val localStorage = mockk<ILocalStorage>(relaxed = true)
    protected val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    protected val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)

    protected val previewWallet = WalletFactory.previewWallet()
    protected val token: Token = previewWallet.token
    protected val swapQuote = mockk<ISwapQuote>(relaxed = true) {
        every { tokenIn } returns token
        every { tokenOut } returns token
        every { amountIn } returns BigDecimal.ONE
    }

    protected val sendTransactionServiceState = SendTransactionServiceState(
        availableBalance = null,
        networkFee = null,
        cautions = emptyList(),
        sendable = true,
        loading = false,
        fields = emptyList(),
        extraFees = emptyMap(),
    )

    protected val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(module {
                single<IConnectivityManager> { mockConnectivityManager() }
                single<ILocalStorage> { localStorage }
                single<PendingMultiSwapStorage> { pendingMultiSwapStorage }
                single<SwapProviderTransactionsStorage> { swapProviderTransactionsStorage }
                single<PoisonAddressManager> { poisonAddressManager }
                single<MarketKitWrapper> { mockk(relaxed = true) }
                single<IBalanceHiddenManager> {
                    mockk(relaxed = true) {
                        every { balanceHiddenFlow } returns MutableStateFlow(false)
                    }
                }
                single<DispatcherProvider> { TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)) }
            })
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    protected fun createViewModel(
        provider: IMultiSwapProvider,
        transactionState: SendTransactionServiceState = sendTransactionServiceState,
        executionMode: SwapExecutionMode = SwapExecutionMode.ExactIn,
        direction: SwapAmountDirection = SwapAmountDirection.In,
        requestedAmountOut: BigDecimal? = null,
        serviceOverride: ISendTransactionService<*>? = null,
        timerService: TimerService = TimerService(),
        quote: ISwapQuote = swapQuote,
        wallet: Wallet = previewWallet,
    ): SwapConfirmViewModel {
        val sendTransactionService = serviceOverride ?: mockk<ISendTransactionService<Nothing>>(relaxed = true) {
            every { hasSettings() } returns false
            every { mevProtectionAvailable } returns false
            every { stateFlow } returns ServiceStateFlow(
                MutableSharedFlow<SendTransactionServiceState>(
                    replay = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                ).also { it.tryEmit(transactionState) }.asSharedFlow()
            )
            every { sendTransactionSettingsFlow } returns MutableStateFlow(SendTransactionSettings.Common)
        }
        val adapterManager = mockk<IAdapterManager>(relaxed = true)
        val assetFiatRateService = mockk<AssetFiatRateService>(relaxed = true)
        val currencyManager = mockk<CurrencyManager> {
            every { baseCurrency } returns Currency("USD", "$", 2, 0)
        }
        val vm = SwapConfirmViewModel(
            request = SwapConfirmRequest(
                provider = provider,
                quote = quote,
                settings = emptyMap(),
                executionMode = executionMode,
                direction = direction,
                requestedAmountOut = requestedAmountOut,
            ),
            currencyManager = currencyManager,
            fiatServices = SwapConfirmFiatServices(
                input = FiatService(assetFiatRateService),
                output = FiatService(assetFiatRateService),
                outputMinimum = FiatService(assetFiatRateService),
            ),
            sendTransactionService = sendTransactionService,
            timerService = timerService,
            priceImpactService = PriceImpactService(),
            wallet = wallet,
            adapterManager = adapterManager,
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
        viewModelStore.put("test-vm", vm)
        return vm
    }

    protected fun finalQuote(
        amountIn: BigDecimal = BigDecimal.ONE,
        amountOut: BigDecimal = BigDecimal.ONE,
        amountInMax: BigDecimal? = null,
        cautions: List<HSCaution> = emptyList(),
        validUntilMillis: Long? = null,
    ): ISwapFinalQuote = SwapFinalQuoteEvm(
        tokenIn = token,
        tokenOut = token,
        amountIn = amountIn,
        amountOut = amountOut,
        amountOutMin = amountOut,
        sendTransactionData = SendTransactionData.Unsupported,
        priceImpact = null,
        fields = emptyList(),
        amountInMax = amountInMax,
        cautions = cautions,
        validUntilMillis = validUntilMillis,
    )
}
