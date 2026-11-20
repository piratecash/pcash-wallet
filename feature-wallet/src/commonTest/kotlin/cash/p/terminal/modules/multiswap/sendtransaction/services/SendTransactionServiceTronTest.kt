package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.tron_activation_caution
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.tronkit.models.AccountResources
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.transaction.Fee
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The fee service estimates on Dispatchers.Default, so these tests wait in real time.
class SendTransactionServiceTronTest : KoinTest {

    private val adapter = mockk<ISendTronAdapter>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val currencyManager = mockk<CurrencyManager>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)

    private val trxToken = Token(
        coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
        blockchain = Blockchain(BlockchainType.Tron, "TRON", null),
        type = TokenType.Native,
        decimals = 6,
    )
    // Collectors and calls share one thread, like viewModelScope: the service state is not thread-safe.
    private val serviceDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serviceDispatcher)

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
                single<CurrencyManager> { currencyManager }
                single<IBalanceHiddenManager> { balanceHiddenManager }
                single<OfflineOperationGate> { mockk(relaxed = true) }
                single<LocallyCreatedTransactionRepository> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        val wallet = mockk<Wallet>(relaxed = true) {
            every { token } returns trxToken
            every { coin } returns trxToken.coin
        }
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2)
        every { marketKit.token(any()) } returns trxToken
        every { marketKit.coinPrice(any(), any()) } returns null
        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns wallet
        coEvery { adapterManager.awaitAdapterForWallet<ISendTronAdapter>(any(), any()) } returns adapter
        every { adapterManager.getAdjustedBalanceData(any()) } returns BalanceData(BigDecimal.TEN)
        every { adapter.trxBalanceData } returns BalanceData(BigDecimal.TEN)
        every { adapter.isOwnAddress(any()) } returns false
        every { adapter.accountResourcesFlow } returns MutableStateFlow<AccountResources?>(null)
        every { balanceHiddenManager.anyWalletVisibilityChangedFlow } returns emptyFlow()
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "translated"
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkAll()
        stopKoin()
    }

    @Test
    fun availableBalance_unactivatedDepositAddress_excludesActivationAndWarns() = runOnServiceThread {
        coEvery { adapter.estimateFee(any(), any()) } returns listOf(
            Fee.Bandwidth(points = 269, price = 1_000, burnInSuns = 100_000),
            Fee.AccountActivation(amount = 1_000_000),
        )
        val service = startService()

        service.setSendTransactionData(SendTransactionData.Tron.Regular(TRON_ADDRESS, BigDecimal.TEN))
        val state = service.awaitState { it.networkFee != null }

        assertEquals(0, BigDecimal("8.9").compareTo(state.availableBalance))
        assertTrue(state.cautions.any { it.type == CautionViewItem.Type.Warning })
        verify { Translator.getString(Res.string.tron_activation_caution, *anyVararg()) }
    }

    @Test
    fun cautions_unactivatedDepositWithBalanceHidden_hidesActivationFee() = runOnServiceThread {
        coEvery { adapter.estimateFee(any(), any()) } returns listOf(Fee.AccountActivation(amount = 1_000_000))
        every { balanceHiddenManager.isWalletBalanceHidden(trxToken.tokenQuery.id) } returns true
        val service = startService()

        service.setSendTransactionData(SendTransactionData.Tron.Regular(TRON_ADDRESS, BigDecimal.TEN))
        service.awaitState { it.networkFee != null }

        verify { Translator.getString(Res.string.tron_activation_caution, "*****") }
    }

    @Test
    fun availableBalance_activatedDepositFreeBandwidth_keepsFullBalanceWithoutWarning() = runOnServiceThread {
        coEvery { adapter.estimateFee(any(), any()) } returns
            listOf(Fee.Bandwidth(points = 269, price = 1_000, source = Fee.Bandwidth.Source.Free))
        val service = startService()

        service.setSendTransactionData(SendTransactionData.Tron.Regular(TRON_ADDRESS, BigDecimal.TEN))
        val state = service.awaitState { it.networkFee != null }

        assertEquals(0, BigDecimal.TEN.compareTo(state.availableBalance))
        assertTrue(state.cautions.none { it.type == CautionViewItem.Type.Warning })
    }

    @Test
    fun send_contractCoveredByOwnEnergy_signsFullEnergyCostAsFeeLimit() = runOnServiceThread {
        val contract = mockk<TriggerSmartContract>(relaxed = true)
        coEvery { adapter.estimateFee(contract) } returns listOf(
            Fee.Energy(required = 64_895, price = 100, fromAccount = 64_895),
            Fee.Bandwidth(points = 345, price = 1_000, source = Fee.Bandwidth.Source.Staked),
        )
        coEvery { adapter.send(contract, any()) } returns "txid"
        val service = startService()

        service.setSendTransactionData(SendTransactionData.Tron.WithContract(contract))
        service.awaitState { it.networkFee != null }
        service.send()

        coVerify { adapter.send(contract, 6_489_500L) }
    }

    @Test
    fun setSendTransactionData_feeStillEstimating_notSendableUntilFeeKnown() = runOnServiceThread {
        val contract = mockk<TriggerSmartContract>(relaxed = true)
        val estimate = CompletableDeferred<List<Fee>>()
        coEvery { adapter.estimateFee(contract) } coAnswers { estimate.await() }
        val service = startService()

        val setting = launch { service.setSendTransactionData(SendTransactionData.Tron.WithContract(contract)) }
        val estimating = service.awaitState { it.loading }
        estimate.complete(listOf(Fee.Bandwidth(points = 345, price = 1_000, source = Fee.Bandwidth.Source.Staked)))
        setting.join()
        val ready = service.awaitState { it.networkFee != null }

        assertFalse(estimating.sendable)
        assertTrue(ready.sendable)
        assertFalse(ready.loading)
    }

    private fun runOnServiceThread(block: suspend CoroutineScope.() -> Unit) = runBlocking(serviceDispatcher, block)

    private fun startService() = SendTransactionServiceTron(trxToken).also { service ->
        service.start(scope)
    }

    private suspend fun SendTransactionServiceTron.awaitState(
        predicate: (SendTransactionServiceState) -> Boolean,
    ) = withTimeout(TIMEOUT_MS) { stateFlow.first(predicate) }

    private companion object {
        const val TRON_ADDRESS = "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7"
        const val TIMEOUT_MS = 10_000L
    }
}
