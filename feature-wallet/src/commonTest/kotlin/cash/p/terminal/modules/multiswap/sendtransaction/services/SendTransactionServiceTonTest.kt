package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.insufficient_balance_required_available
import cash.p.terminal.resources.not_enough_ton_for_fee
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.tonapi.infrastructure.ClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

// The fee service debounces on Dispatchers.Default with real delays, so these tests wait in real time.
class SendTransactionServiceTonTest : KoinTest {

    private val adapter = mockk<ISendTonAdapter>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val currencyManager = mockk<CurrencyManager>(relaxed = true)

    private val blockchain = Blockchain(BlockchainType.Ton, "TON", null)
    private val tonToken = Token(
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        blockchain = blockchain,
        type = TokenType.Native,
        decimals = 9,
    )
    private val jettonToken = Token(
        coin = Coin(uid = "jetton", name = "Jetton", code = "JET"),
        blockchain = blockchain,
        type = TokenType.Jetton("EQJetton"),
        decimals = 6,
    )
    private val estimate = TonFeeEstimate(fee = BigDecimal("0.01"), requiredTon = BigDecimal("0.075"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<PendingTransactionRegistrar> { mockk(relaxed = true) }
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
                single<CurrencyManager> { currencyManager }
                single<IAccountManager> { mockk(relaxed = true) }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        val wallet = mockk<Wallet>(relaxed = true) {
            every { token } returns jettonToken
            every { coin } returns jettonToken.coin
        }
        every { adapter.maxSpendableBalance } returns BigDecimal("100")
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2)
        every { marketKit.token(any()) } returns tonToken
        every { marketKit.coinPrice(any(), any()) } returns null
        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns wallet
        coEvery { adapterManager.awaitAdapterForWallet<ISendTonAdapter>(any(), any()) } returns adapter
        every { adapterManager.getAdjustedBalanceData(any()) } returns BalanceData(BigDecimal("100"))
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
    fun feeState_jettonTonBalanceBelowRequiredTon_notSendableWithError() = runBlocking<Unit> {
        setTonBalance(BigDecimal("0.05"))
        coEvery { adapter.estimateFee(any(), any(), any()) } returns estimate
        val service = startService()

        service.setSendTransactionData(tonData(BigDecimal.ONE))
        val state = service.awaitState { it.networkFee != null }

        assertFalse(state.sendable)
        assertEquals(CautionViewItem.Type.Error, state.feeCaution?.type)
        verify {
            Translator.getString(
                Res.string.insufficient_balance_required_available,
                "TON",
                "0.075 TON",
                "0.05 TON",
            )
        }
    }

    @Test
    fun feeState_successThenNoEnoughBalance_staysNotSendable() = runBlocking<Unit> {
        setTonBalance(BigDecimal.ONE)
        coEvery { adapter.estimateFee(any(), any(), any()) } returns estimate
        val service = startService()
        service.setSendTransactionData(tonData(BigDecimal.ONE))
        assertTrue(service.awaitState { it.sendable }.sendable)

        coEvery { adapter.estimateFee(any(), any(), any()) } throws ClientException("Too Many Requests", 429)
        service.setSendTransactionData(tonData(BigDecimal.TEN))
        val state = service.awaitState { it.feeCaution != null }

        assertFalse(state.sendable)
        verify { Translator.getString(Res.string.not_enough_ton_for_fee, *anyVararg()) }
        // Let the fee service's retry loop finish instead of outliving the test.
        coEvery { adapter.estimateFee(any(), any(), any()) } returns estimate
    }

    private fun setTonBalance(balance: BigDecimal) {
        every { adapterManager.getAdjustedBalanceDataForToken(tonToken) } returns BalanceData(balance)
    }

    private fun startService() = SendTransactionServiceTon(jettonToken).also { service ->
        service.start(scope)
    }

    private fun tonData(amount: BigDecimal) = SendTransactionData.Ton(
        address = VALID_TON_ADDRESS,
        amount = amount,
        memo = null,
    )

    private suspend fun SendTransactionServiceTon.awaitState(
        predicate: (SendTransactionServiceState) -> Boolean,
    ) = withTimeout(TIMEOUT_MS) { stateFlow.first(predicate) }

    private companion object {
        const val VALID_TON_ADDRESS = "UQCYTBH7n8OnQ6BgOfdkNRWF7socLJb9U-JMRcoz3UpL_0V6"
        const val TIMEOUT_MS = 10_000L
    }
}
