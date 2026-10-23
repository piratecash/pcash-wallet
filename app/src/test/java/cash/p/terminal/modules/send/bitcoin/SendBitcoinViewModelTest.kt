package cash.p.terminal.modules.send.bitcoin

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineBitcoinTransaction
import cash.p.terminal.core.adapters.BitcoinFeeInfo
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.send_error_address_unavailable
import cash.p.terminal.resources.send_error_amount_unavailable
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.hodler.LockTimeInterval
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.KoinTestRule
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.math.BigDecimal
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.send.mockConnectivityManager

@OptIn(ExperimentalCoroutinesApi::class)
class SendBitcoinViewModelTest {
    private val mainScheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(mainScheduler)
    private val defaultScheduler = TestCoroutineScheduler()
    private val defaultDispatcher = StandardTestDispatcher(defaultScheduler)
    private val balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val adapter = mockk<SendBalanceAdapter>(relaxed = true)
    private val feeRateService = mockk<SendBitcoinFeeRateService>(relaxed = true)
    private val feeService = mockk<SendBitcoinFeeService>(relaxed = true)
    private val amountService = mockk<SendBitcoinAmountService>(relaxed = true)
    private val addressService = mockk<SendBitcoinAddressService>(relaxed = true)
    private val pluginService = mockk<SendBitcoinPluginService>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val dispatcherProvider = SplitDispatcherProvider(mainDispatcher, defaultDispatcher)
    private val wallet = WalletFactory.previewWallet()
    private lateinit var amountStateFlow: MutableStateFlow<SendBitcoinAmountService.State>
    private lateinit var addressStateFlow: MutableStateFlow<SendBitcoinAddressService.State>
    private lateinit var feeRateStateFlow: MutableStateFlow<SendBitcoinFeeRateService.State>
    private lateinit var pluginStateFlow: MutableStateFlow<SendBitcoinPluginService.State>
    private val feeInfoFlow = MutableStateFlow<BitcoinFeeInfo?>(null)

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IConnectivityManager> { mockConnectivityManager() }
                single { mockk<MarketKitWrapper>(relaxed = true) }
                single<IBalanceHiddenManager> {
                    mockk(relaxed = true) {
                        every { balanceHiddenFlow } returns MutableStateFlow(false)
                    }
                }
                single { mockk<PoisonAddressManager>(relaxed = true) }
                single { mockk<OfflineTransactionPayloadEncoder>(relaxed = true) }
                single { mockk<OfflineSignedTransactionRepository>(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        every { adapter.balanceUpdatedFlow } returns balanceUpdatedFlow
        every { adapter.balanceState } returns AdapterState.Synced
        every { adapter.balanceStateUpdatedFlow } returns emptyFlow()
        every { adapter.blockchainType } returns BlockchainType.Bitcoin
        every { adapterManager.getBalanceAdapterForWallet(wallet) } returns null

        feeRateStateFlow = MutableStateFlow(
            SendBitcoinFeeRateService.State(
                feeRate = null,
                feeRateCaution = null,
                canBeSend = false,
                isRecommended = false
            )
        )
        every { feeRateService.stateFlow } returns feeRateStateFlow
        coEvery { feeRateService.start() } returns Unit

        amountStateFlow = MutableStateFlow(
            SendBitcoinAmountService.State(
                amount = null,
                amountCaution = null,
                availableBalance = null,
                canBeSend = false
            )
        )
        every { amountService.stateFlow } returns amountStateFlow
        addressStateFlow = MutableStateFlow(
            SendBitcoinAddressService.State(
                validAddress = null,
                addressError = null,
                canBeSend = false
            )
        )
        every { addressService.stateFlow } returns addressStateFlow
        pluginStateFlow = MutableStateFlow(
            SendBitcoinPluginService.State(
                lockTimeInterval = null,
                pluginData = null
            )
        )
        every { pluginService.stateFlow } returns pluginStateFlow
        every { pluginService.isLockTimeEnabled } returns false
        every { pluginService.lockTimeIntervals } returns emptyList()
        every { feeService.bitcoinFeeInfoFlow } returns feeInfoFlow
        every { xRateService.getRate(wallet.coin.uid) } returns null
        every { xRateService.getRateFlow(wallet.coin.uid) } returns emptyFlow<CurrencyValue>()
        every { localStorage.utxoExpertModeEnabled } returns false
        every { localStorage.utxoExpertModeEnabledFlow } returns MutableStateFlow(false)
        justRun { addressService.setAddress(null) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun balanceUpdatedFlow_emits_refreshesBalanceAndFeeOnDefaultDispatcher() = runTest(mainDispatcher) {
        createViewModel()
        advanceUntilIdle()

        balanceUpdatedFlow.emit(Unit)
        advanceUntilIdle()

        verify(exactly = 0) { amountService.refreshAvailableBalance() }
        verify(exactly = 0) { feeService.refresh() }

        defaultScheduler.advanceUntilIdle()

        verify(exactly = 1) { amountService.refreshAvailableBalance() }
        verify(exactly = 1) { feeService.refresh() }
    }

    @Test
    fun getConfirmationData_missingAddress_throwsClearError() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val error = assertFailsWith<LocalizedException> {
            viewModel.getConfirmationData()
        }

        assertEquals(Res.string.send_error_address_unavailable, error.errorTextRes)
    }

    @Test
    fun getConfirmationData_missingAmount_throwsClearError() = runTest(mainDispatcher) {
        addressStateFlow.value = SendBitcoinAddressService.State(
            validAddress = Address("bc1qrecipient"),
            addressError = null,
            canBeSend = true
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val error = assertFailsWith<LocalizedException> {
            viewModel.getConfirmationData()
        }

        assertEquals(Res.string.send_error_amount_unavailable, error.errorTextRes)
    }

    @Test
    fun offlineSignSupported_litecoinMwebWallet_returnsTrue() = runTest(mainDispatcher) {
        val offlineAdapter = mockk<SendBalanceOfflineAdapter>(relaxed = true)
        val mwebWallet = litecoinMwebWallet()
        every { offlineAdapter.balanceUpdatedFlow } returns balanceUpdatedFlow
        every { offlineAdapter.balanceState } returns AdapterState.Synced
        every { offlineAdapter.balanceStateUpdatedFlow } returns emptyFlow()
        every { offlineAdapter.blockchainType } returns BlockchainType.Litecoin
        every { adapterManager.getBalanceAdapterForWallet(mwebWallet) } returns null

        val viewModel = createViewModel(adapter = offlineAdapter, wallet = mwebWallet)
        advanceUntilIdle()

        assertEquals(true, viewModel.offlineSignSupported)
    }

    @Test
    fun canBeSend_btcMemo80Bytes_isTrue() = runTest(mainDispatcher) {
        assertEquals(true, canBeSendWithMemo(BlockchainType.Bitcoin, "a".repeat(80)))
    }

    @Test
    fun canBeSend_btcMemo81Bytes_isFalse() = runTest(mainDispatcher) {
        assertEquals(false, canBeSendWithMemo(BlockchainType.Bitcoin, "a".repeat(81)))
    }

    @Test
    fun canBeSend_btcCyrillicMemo41Chars_isFalse() = runTest(mainDispatcher) {
        assertEquals(false, canBeSendWithMemo(BlockchainType.Bitcoin, "ж".repeat(41)))
    }

    @Test
    fun canBeSend_btcBlankMemo81Chars_isTrue() = runTest(mainDispatcher) {
        assertEquals(true, canBeSendWithMemo(BlockchainType.Bitcoin, " ".repeat(81)))
    }

    @Test
    fun canBeSend_bchFamilyMemo220Bytes_isTrue() = runTest(mainDispatcher) {
        assertEquals(true, canBeSendWithMemo(BlockchainType.BitcoinCash, "a".repeat(220)))
        assertEquals(true, canBeSendWithMemo(BlockchainType.ECash, "a".repeat(220)))
    }

    @Test
    fun canBeSend_bchFamilyMemo221Bytes_isFalse() = runTest(mainDispatcher) {
        assertEquals(false, canBeSendWithMemo(BlockchainType.BitcoinCash, "a".repeat(221)))
        assertEquals(false, canBeSendWithMemo(BlockchainType.ECash, "a".repeat(221)))
    }

    @Test
    fun handleUpdatedPluginState_lockTimeWithMemo_clearsMemoBeforePublishingPluginData() = runTest(mainDispatcher) {
        var amountMemo: String? = null
        var feeMemo: String? = null
        every { amountService.setMemo(any(), any()) } answers { amountMemo = firstArg() }
        every { feeService.setMemo(any(), any()) } answers { feeMemo = firstArg() }
        every { amountService.setPluginData(any()) } answers {
            amountStateFlow.value = amountStateFlow.value.copy(availableBalance = balanceFor(amountMemo))
        }
        every { feeService.setPluginData(any()) } answers {
            feeInfoFlow.value = BitcoinFeeInfo(emptyList(), balanceFor(feeMemo), null, null)
        }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEnterMemo("memo")
        advanceUntilIdle()

        pluginStateFlow.value = SendBitcoinPluginService.State(LockTimeInterval.hour, null)
        advanceUntilIdle()

        verifyOrder {
            amountService.setMemo(null, forceEmit = false)
            feeService.setMemo(null, forceEmit = false)
            feeService.setPluginData(any())
            amountService.setPluginData(any())
        }
        assertEquals(false, viewModel.uiState.isMemoAvailable)
        assertEquals(null, viewModel.uiState.memo)
        assertEquals(BALANCE_WITHOUT_MEMO, viewModel.uiState.availableBalance)
        assertEquals(BALANCE_WITHOUT_MEMO, viewModel.uiState.fee)
    }

    @Test
    fun handleUpdatedPluginState_lockTimeRemoved_memoAvailableAgain() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        pluginStateFlow.value = SendBitcoinPluginService.State(LockTimeInterval.hour, null)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.isMemoAvailable)

        pluginStateFlow.value = SendBitcoinPluginService.State(null, null)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.isMemoAvailable)
    }

    private fun balanceFor(memo: String?) = if (memo == null) BALANCE_WITHOUT_MEMO else BigDecimal.ONE

    private fun TestScope.canBeSendWithMemo(blockchainType: BlockchainType, memo: String): Boolean {
        every { adapter.blockchainType } returns blockchainType
        feeRateStateFlow.value = feeRateStateFlow.value.copy(feeRate = 1, canBeSend = true)
        amountStateFlow.value = amountStateFlow.value.copy(amount = BigDecimal.ONE, canBeSend = true)
        addressStateFlow.value = SendBitcoinAddressService.State(Address("bc1qrecipient"), null, true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnterMemo(memo)
        advanceUntilIdle()

        return viewModel.uiState.canBeSend
    }

    private fun createViewModel(
        adapter: ISendBitcoinAdapter = this.adapter,
        wallet: Wallet = this.wallet,
    ) = SendBitcoinViewModel(
        adapter = adapter,
        wallet = wallet,
        feeRateService = feeRateService,
        feeService = feeService,
        amountService = amountService,
        addressService = addressService,
        pluginService = pluginService,
        xRateService = xRateService,
        btcBlockchainManager = mockk<BtcBlockchainManager>(relaxed = true),
        contactsRepo = mockk<ContactsRepository>(relaxed = true),
        showAddressInput = true,
        localStorage = localStorage,
        address = null,
        pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true),
        adapterManager = adapterManager,
        dispatcherProvider = dispatcherProvider
    )

    private interface SendBalanceAdapter : ISendBitcoinAdapter, IBalanceAdapter

    private interface SendBalanceOfflineAdapter :
        ISendBitcoinAdapter,
        IBalanceAdapter,
        OfflineTransactionAdapter<SignedOfflineBitcoinTransaction>

    private fun litecoinMwebWallet(): Wallet {
        return requireNotNull(
            WalletFactory(mockk<HardwareWalletTokenPolicy>(relaxed = true)).create(
                token = Token(
                    coin = Coin("litecoin", "Litecoin", "LTC"),
                    blockchain = Blockchain(BlockchainType.Litecoin, "Litecoin", null),
                    type = TokenType.Mweb,
                    decimals = 8,
                ),
                account = Account(
                    id = "litecoin-account-id",
                    name = "Litecoin",
                    type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
                    origin = AccountOrigin.Created,
                    level = 0,
                ),
                hardwarePublicKey = null,
            )
        )
    }

    private companion object {
        val BALANCE_WITHOUT_MEMO = BigDecimal.TEN
    }

    private class SplitDispatcherProvider(
        override val main: CoroutineDispatcher,
        override val default: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io: CoroutineDispatcher = main
        override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + io)
    }
}
