package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.models.AccountResources
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class TokenBalanceServiceTest {

    private val adapterManager = mockk<IAdapterManager>()
    private val marketKit = mockk<MarketKitWrapper>()
    private val adapter = mockk<ISendZcashAdapter>()
    private val balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val feeFlow = MutableStateFlow(BigDecimal("0.0001"))
    private var adjustedBalanceData = BalanceData(available = BigDecimal("3.9999"))
    private var zcashMaxSpendable = BigDecimal("4.9998")
    private val token = Token(
        coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
        blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
        type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
        decimals = 8
    )

    @Test
    fun setToken_zcashNativeWithLocalPending_usesAuthoritativeAdapterAvailable() {
        stubZcashBalance()

        val service = TokenBalanceService(adapterManager, marketKit)
        service.setToken(token)

        assertBigDecimalEquals("4.9998", service.stateFlow.value.balance)
        assertBigDecimalEquals("3.9999", service.stateFlow.value.displayBalance)
    }

    @Test
    fun feeChanged_zcashNativeWithLocalPending_recalculatesAuthoritativeAdapterAvailable() = runTest {
        stubZcashBalance()

        val service = TokenBalanceService(adapterManager, marketKit)
        val serviceScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            service.start(serviceScope)
            service.setToken(token)

            zcashMaxSpendable = BigDecimal("4.9997")
            feeFlow.value = BigDecimal("0.0002")
            advanceUntilIdle()

            assertBigDecimalEquals("4.9997", service.stateFlow.value.balance)
            assertBigDecimalEquals("3.9999", service.stateFlow.value.displayBalance)
        } finally {
            serviceScope.cancel()
        }
    }

    @Test
    fun balanceUpdated_zcashNativeWithLocalPending_updatesAvailableBalance() = runTest {
        stubZcashBalance()

        val service = TokenBalanceService(adapterManager, marketKit)
        val serviceScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            service.start(serviceScope)
            service.setToken(token)

            adjustedBalanceData = BalanceData(available = BigDecimal("4.9999"))
            balanceUpdatedFlow.emit(Unit)
            advanceUntilIdle()

            assertBigDecimalEquals("4.9998", service.stateFlow.value.balance)
            assertBigDecimalEquals("4.9999", service.stateFlow.value.displayBalance)
        } finally {
            serviceScope.cancel()
        }
    }

    @Test
    fun setAmount_pausedSourceAdapter_setsWalletNotSyncedError() {
        val nativeToken = Token(
            coin = Coin(uid = "native", name = "Native", code = "NATIVE"),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = TokenType.Native,
            decimals = 18,
        )
        val pausedAdapter = mockk<IBalanceAdapter>(relaxed = true) {
            every { balanceState } returns AdapterState.Paused
            every { fee } returns MutableStateFlow(BigDecimal.ZERO)
            every { maxSpendableBalance } returns BigDecimal.TEN
        }
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(nativeToken) } returns pausedAdapter
        every { adapterManager.getAdjustedBalanceDataForToken(nativeToken) } returns
            BalanceData(available = BigDecimal.TEN)
        every { marketKit.token(any()) } returns nativeToken

        val service = TokenBalanceService(adapterManager, marketKit)
        service.setToken(nativeToken)
        service.setAmount(BigDecimal.ONE)

        assertTrue(service.stateFlow.value.error is WalletNotSynced)
    }

    @Test
    fun feeChanged_trxAfterBalance_updatesMaxSpendableFeeAndResources() = runTest {
        val tronFee = MutableStateFlow(BigDecimal.ZERO)
        val resources = MutableStateFlow<AccountResources?>(null)
        val trxAdapter = tronAdapter(tronFee, resources) {
            every { maxSpendableBalance } answers { BigDecimal.TEN - tronFee.value }
        }
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(trxToken) } returns trxAdapter
        every { adapterManager.getAdjustedBalanceDataForToken(trxToken) } returns BalanceData(BigDecimal.TEN)
        every { marketKit.token(any()) } returns trxToken

        val service = TokenBalanceService(adapterManager, marketKit)
        val serviceScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            service.start(serviceScope)
            service.setToken(trxToken)

            tronFee.value = BigDecimal("0.269")
            val accountResources = AccountResources(energy = AccountResources.Resource(limit = 65_000))
            resources.value = accountResources
            advanceUntilIdle()

            assertBigDecimalEquals("9.731", service.stateFlow.value.balance)
            assertBigDecimalEquals("0.269", service.stateFlow.value.fee)
            assertEquals(accountResources, service.stateFlow.value.tronResources)
        } finally {
            serviceScope.cancel()
        }
    }

    @Test
    fun feeChanged_trc20AfterBalance_showsTokenTransferFee() = runTest {
        val trc20Fee = MutableStateFlow(BigDecimal.ZERO)
        val usdtToken = Token(
            coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
            blockchain = tron,
            type = TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
            decimals = 6,
        )
        val trc20Adapter = tronAdapter(trc20Fee, MutableStateFlow(null)) {
            every { maxSpendableBalance } returns BigDecimal("100")
        }
        val trxAdapter = tronAdapter(MutableStateFlow(BigDecimal("0.269")), MutableStateFlow(null)) {}
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(usdtToken) } returns trc20Adapter
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(trxToken) } returns trxAdapter
        every { adapterManager.getAdjustedBalanceDataForToken(any()) } returns BalanceData(BigDecimal("100"))
        every { marketKit.token(any()) } returns trxToken

        val service = TokenBalanceService(adapterManager, marketKit)
        val serviceScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            service.start(serviceScope)
            service.setToken(usdtToken)

            trc20Fee.value = BigDecimal("4.4895")
            advanceUntilIdle()

            assertBigDecimalEquals("4.4895", service.stateFlow.value.fee)
        } finally {
            serviceScope.cancel()
        }
    }

    private fun tronAdapter(
        fee: MutableStateFlow<BigDecimal>,
        resources: MutableStateFlow<AccountResources?>,
        block: ISendTronAdapter.() -> Unit,
    ) = mockk<ISendTronAdapter>(relaxed = true) {
        every { balanceState } returns AdapterState.Synced
        every { balanceUpdatedFlow } returns MutableSharedFlow()
        every { this@mockk.fee } returns fee
        every { accountResourcesFlow } returns resources
        block()
    }

    private val tron = Blockchain(BlockchainType.Tron, "TRON", null)
    private val trxToken = Token(
        coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
        blockchain = tron,
        type = TokenType.Native,
        decimals = 6,
    )

    private fun stubZcashBalance() {
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(token) } returns adapter
        every { adapterManager.getAdjustedBalanceDataForToken(token) } answers { adjustedBalanceData }
        every { adapter.maxSpendableBalance } answers { zcashMaxSpendable }
        every { adapter.balanceUpdatedFlow } returns balanceUpdatedFlow
        every { adapter.fee } returns feeFlow
        every { marketKit.token(any()) } returns token
    }

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal?) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual?.stripTrailingZeros())
    }
}
