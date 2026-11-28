package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.IBalanceFetchState
import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ITokenTransferCostEstimator
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.TokenTransferCost
import cash.p.terminal.core.managers.TokenTransferCostCache
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.models.AccountResources
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class TokenBalanceServiceTest {

    private var serviceScope: CoroutineScope? = null
    private val timeSource = TestTimeSource()

    @After
    fun tearDown() {
        serviceScope?.cancel()
    }

    private val adaptersReady = PublishSubject.create<Map<Wallet, IAdapter>>()
    private val adapterManager = mockk<IAdapterManager> {
        every { adaptersReadyObservable } returns adaptersReady.toFlowable(BackpressureStrategy.BUFFER)
    }
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

        val service = createService()
        service.setToken(token)

        assertBigDecimalEquals("4.9998", service.stateFlow.value.balance)
        assertBigDecimalEquals("3.9999", service.stateFlow.value.displayBalance)
    }

    @Test
    fun feeChanged_zcashNativeWithLocalPending_recalculatesAuthoritativeAdapterAvailable() = runTest {
        stubZcashBalance()

        val service = createService()
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

        val service = createService()
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

        val service = createService()
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

        val service = createService()
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

        val service = createService()
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

    // Jetton transfer: the network keeps 0.24 TON, the wallet must hold 0.301 TON to send.
    @Test
    fun setAmount_jettonKnownZeroNativeBalance_blocksFeeBalance() = runTest {
        jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)

        val service = startedService(usdtTon)

        assertTrue(service.stateFlow.value.insufficientFeeBalance)
        assertNull(service.stateFlow.value.feeBalanceRequired)
        assertBigDecimalEquals("0.24", service.stateFlow.value.fee)
    }

    @Test
    fun setAmount_jettonKnownZeroEstimatePending_blocksAlready() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } coAnswers { awaitCancellation() }

        val service = startedService(usdtTon)

        assertTrue(service.stateFlow.value.feeLoading)
        assertTrue(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setAmount_jettonNativeDustBelowFee_blocksFeeBalance() = runTest {
        jettonAdapter(nativeBalance = BigDecimal("0.000000001"), nativeLoaded = true)

        val service = startedService(usdtTon)

        assertTrue(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setAmount_jettonNativeDustEstimatePending_doesNotBlock() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal("0.000000001"), nativeLoaded = true)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } coAnswers { awaitCancellation() }

        val service = startedService(usdtTon)

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setAmount_jettonNativeBalanceNotFetched_doesNotBlock() = runTest {
        jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = false)

        val service = startedService(usdtTon)

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setAmount_jettonNativeBelowRequired_warnsWithoutBlocking() = runTest {
        jettonAdapter(nativeBalance = BigDecimal("0.25"), nativeLoaded = true)

        val service = startedService(usdtTon)

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
        assertBigDecimalEquals("0.301", service.stateFlow.value.feeBalanceRequired)
    }

    @Test
    fun setAmount_jettonSyncingNativeBelowRequired_noWarning() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal("0.25"), nativeLoaded = true)
        every { jetton.balanceState } returns AdapterState.Syncing()

        val service = startedService(usdtTon)

        assertNull(service.stateFlow.value.feeBalanceRequired)
    }

    @Test
    fun balanceUpdated_afterFailedEstimate_estimatesAgain() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal("0.1"), nativeLoaded = true)
        val balanceUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        every { jetton.balanceUpdatedFlow } returns balanceUpdated
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } throws IllegalStateException()
        val service = startedService(usdtTon)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } returns
            TokenTransferCost(fee = BigDecimal("0.24"), requiredBalance = BigDecimal("0.301"))

        balanceUpdated.emit(Unit)
        advanceUntilIdle()

        assertTrue(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setAmount_jettonNativeCoversRequired_noWarning() = runTest {
        jettonAdapter(nativeBalance = BigDecimal("0.5"), nativeLoaded = true)

        val service = startedService(usdtTon)

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
        assertNull(service.stateFlow.value.feeBalanceRequired)
    }

    @Test
    fun nativeZeroFetchedLater_jetton_blocksAfterFetch() = runTest {
        jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)
        val fetched = tonAdapterWithZero(fetched = false)
        val service = startedService(usdtTon)
        assertFalse(service.stateFlow.value.insufficientFeeBalance)

        fetched.value = true
        advanceUntilIdle()

        assertTrue(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun adaptersReady_nativeAdapterReplacedBeforeFetch_unblocks() = runTest {
        jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)
        tonAdapterWithZero(fetched = true)
        val service = startedService(usdtTon)
        assertTrue(service.stateFlow.value.insufficientFeeBalance)

        tonAdapterWithZero(fetched = false)
        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
    }

    // The amount comes from the TON adapter, so the jetton's own "loaded" must not vouch for it.
    @Test
    fun setAmount_nativeAdapterZeroNotFetched_doesNotBlock() = runTest {
        jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)
        tonAdapterWithZero(fetched = false)

        val service = startedService(usdtTon)

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun nativeBalanceUpdated_jettonToppedUp_unblocks() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)
        val nativeUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        every { (jetton as INativeBalanceProvider).nativeBalanceUpdatedFlow } returns nativeUpdated
        val service = startedService(usdtTon)
        assertTrue(service.stateFlow.value.insufficientFeeBalance)

        every { (jetton as INativeBalanceProvider).nativeBalanceData } returns BalanceData(BigDecimal.ONE)
        nativeUpdated.emit(Unit)
        advanceUntilIdle()

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setAmount_amountOverBalanceAndKnownZeroNative_keepsFeeBlock() = runTest {
        jettonAdapter(nativeBalance = BigDecimal.ZERO, nativeLoaded = true)
        val service = startedService(usdtTon)

        service.setAmount(BigDecimal("1000"))

        assertEquals(SwapError.InsufficientBalanceFrom, service.stateFlow.value.error)
        assertTrue(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun adaptersReady_adapterPublishedAfterTokenPicked_estimates() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal.ONE, nativeLoaded = true)
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(usdtTon) } returns null
        val service = startedService(usdtTon)

        every { adapterManager.getAdapterForToken<IBalanceAdapter>(usdtTon) } returns jetton
        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        assertBigDecimalEquals("0.24", service.stateFlow.value.fee)
    }

    @Test
    fun adaptersReady_unchangedAdapters_keepsNativeBalanceSubscription() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal.ONE, nativeLoaded = true)
        val nativeUpdated = MutableSharedFlow<Unit>()
        every { (jetton as INativeBalanceProvider).nativeBalanceUpdatedFlow } returns nativeUpdated
        startedService(usdtTon)
        val subscriberCounts = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            nativeUpdated.subscriptionCount.collect { subscriberCounts += it }
        }

        adaptersReady.onNext(emptyMap())
        advanceUntilIdle()

        assertEquals(listOf(1), subscriberCounts)
    }

    @Test
    fun setToken_offline_doesNotEstimate() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal.ONE, nativeLoaded = true)

        startedService(usdtTon, offline = true)

        coVerify(exactly = 0) { (jetton as ITokenTransferCostEstimator).estimateTransferCost() }
    }

    @Test
    fun setToken_sameAdapterAgain_doesNotReestimate() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal.ONE, nativeLoaded = true)

        startedService(usdtTon).setToken(usdtTon)
        advanceUntilIdle()

        coVerify(exactly = 1) { (jetton as ITokenTransferCostEstimator).estimateTransferCost() }
    }

    @Test
    fun setToken_sameAdapterAfterFailedEstimate_estimatesAgain() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal("0.1"), nativeLoaded = true)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } throws IllegalStateException()
        val service = startedService(usdtTon)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } returns
            TokenTransferCost(fee = BigDecimal("0.24"), requiredBalance = BigDecimal("0.301"))

        service.setToken(usdtTon)
        advanceUntilIdle()

        assertTrue(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun balanceUpdated_estimateExpiredAndReestimating_dropsOldFeeBlock() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal("0.1"), nativeLoaded = true)
        val balanceUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        every { jetton.balanceUpdatedFlow } returns balanceUpdated
        val service = startedService(usdtTon)
        assertTrue(service.stateFlow.value.insufficientFeeBalance)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } coAnswers { awaitCancellation() }
        timeSource += 11.minutes

        balanceUpdated.emit(Unit)
        advanceUntilIdle()

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
    }

    @Test
    fun setToken_sameAdapterAfterEstimateExpired_reestimatesWithCurrentFee() = runTest {
        val jetton = jettonAdapter(nativeBalance = BigDecimal("0.1"), nativeLoaded = true)
        val service = startedService(usdtTon)
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } returns
            TokenTransferCost(fee = BigDecimal("0.05"), requiredBalance = BigDecimal("0.06"))

        timeSource += 11.minutes
        service.setToken(usdtTon)
        advanceUntilIdle()

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
        assertBigDecimalEquals("0.05", service.stateFlow.value.fee)
    }

    @Test
    fun setAmount_trc20ZeroTrxTransferCanBeFree_doesNotBlock() = runTest {
        val trc20 = mockk<ISendTronAdapter>(
            relaxed = true,
            moreInterfaces = arrayOf(ITokenTransferCostEstimator::class, INativeBalanceProvider::class),
        ) {
            every { balanceState } returns AdapterState.Synced
            every { balanceUpdatedFlow } returns MutableSharedFlow()
            every { maxSpendableBalance } returns BigDecimal("100")
            every { accountResourcesFlow } returns MutableStateFlow(null)
        }
        every { (trc20 as ITokenTransferCostEstimator).transferAlwaysCostsNative } returns false
        every { (trc20 as INativeBalanceProvider).nativeBalanceLoaded } returns true
        coEvery { (trc20 as ITokenTransferCostEstimator).estimateTransferCost() } returns
            TokenTransferCost(BigDecimal("13.4"), BigDecimal("13.4"))
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(usdtTron) } returns trc20
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(trxToken) } returns null
        every { adapterManager.getAdjustedBalanceDataForToken(usdtTron) } returns BalanceData(BigDecimal("100"))
        every { adapterManager.getAdjustedBalanceDataForToken(trxToken) } returns BalanceData(BigDecimal.ZERO)
        every { marketKit.token(any()) } returns trxToken

        val service = startedService(usdtTron)

        assertFalse(service.stateFlow.value.insufficientFeeBalance)
        assertBigDecimalEquals("13.4", service.stateFlow.value.feeBalanceRequired)
    }

    private fun tonAdapterWithZero(fetched: Boolean): MutableStateFlow<Boolean> {
        val fetchedFlow = MutableStateFlow(fetched)
        val tonAdapter = mockk<IBalanceAdapter>(relaxed = true, moreInterfaces = arrayOf(IBalanceFetchState::class)) {
            every { balanceUpdatedFlow } returns MutableSharedFlow()
            every { balanceStateUpdatedFlow } returns fetchedFlow.map { }
        }
        every { (tonAdapter as IBalanceFetchState).balanceFetched } answers { fetchedFlow.value }
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(tonToken) } returns tonAdapter
        every { adapterManager.getAdjustedBalanceDataForToken(tonToken) } returns BalanceData(BigDecimal.ZERO)
        return fetchedFlow
    }

    private fun jettonAdapter(nativeBalance: BigDecimal, nativeLoaded: Boolean): IBalanceAdapter {
        val jetton = mockk<IBalanceAdapter>(
            relaxed = true,
            moreInterfaces = arrayOf(ITokenTransferCostEstimator::class, INativeBalanceProvider::class),
        ) {
            every { balanceState } returns AdapterState.Synced
            every { balanceUpdatedFlow } returns MutableSharedFlow()
            every { maxSpendableBalance } returns BigDecimal("100")
        }
        every { (jetton as INativeBalanceProvider).nativeBalanceData } returns BalanceData(nativeBalance)
        every { (jetton as INativeBalanceProvider).nativeBalanceUpdatedFlow } returns MutableSharedFlow()
        every { (jetton as INativeBalanceProvider).nativeBalanceLoaded } returns nativeLoaded
        every { (jetton as ITokenTransferCostEstimator).transferAlwaysCostsNative } returns true
        coEvery { (jetton as ITokenTransferCostEstimator).estimateTransferCost() } returns
            TokenTransferCost(fee = BigDecimal("0.24"), requiredBalance = BigDecimal("0.301"))
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(usdtTon) } returns jetton
        every { adapterManager.getAdapterForToken<IBalanceAdapter>(tonToken) } returns null
        every { adapterManager.getAdjustedBalanceDataForToken(usdtTon) } returns BalanceData(BigDecimal("100"))
        every { adapterManager.getAdjustedBalanceDataForToken(tonToken) } returns null
        every { marketKit.token(any()) } returns tonToken
        return jetton
    }

    // advanceUntilIdle does not wait for backgroundScope, so the service gets its own scope.
    private fun TestScope.startedService(token: Token, offline: Boolean = false): TokenBalanceService {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher).also { serviceScope = it }
        val cache = TokenTransferCostCache(TestDispatcherProvider(dispatcher, scope), timeSource)
        val service = createService(cache, offline)
        service.start(scope)
        service.setToken(token)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()
        return service
    }

    private fun createService(
        cache: TokenTransferCostCache = mockk(relaxed = true),
        offline: Boolean = false,
    ) = TokenBalanceService(
        adapterManager = adapterManager,
        marketKit = marketKit,
        transferCostCache = cache,
        accountManager = mockk<IAccountManager> {
            every { activeAccount } returns mockk<Account> { every { id } returns "account" }
        },
        offlineOperationGate = mockk<OfflineOperationGate> { every { isBlocked(any<Account>(), any()) } returns offline },
    )

    private val ton = Blockchain(BlockchainType.Ton, "TON", null)
    private val tron = Blockchain(BlockchainType.Tron, "TRON", null)
    private val tonToken = Token(
        coin = Coin(uid = "the-open-network", name = "Toncoin", code = "TON"),
        blockchain = ton,
        type = TokenType.Native,
        decimals = 9,
    )
    private val usdtTon = Token(
        coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
        blockchain = ton,
        type = TokenType.Jetton("EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"),
        decimals = 6,
    )
    private val usdtTron = Token(
        coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
        blockchain = tron,
        type = TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
        decimals = 6,
    )

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
