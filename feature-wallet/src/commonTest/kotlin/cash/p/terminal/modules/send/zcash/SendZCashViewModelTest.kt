package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.SignedOfflineZcashTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.ZcashFeeEstimate
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.mockConnectivityManager
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule

@OptIn(ExperimentalCoroutinesApi::class)
class SendZCashViewModelTest : KoinTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<ISendZcashAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private val feeFlow = MutableStateFlow(FEE)
    private val balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val zcash = Blockchain(BlockchainType.Zcash, "Zcash", null)
    private val zcashToken = Token(
        coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
        blockchain = zcash,
        type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
        decimals = 8,
    )
    private val account = account(AccountType.Mnemonic(List(12) { "word$it" }, ""))
    private val wallet = createWallet(zcashToken, account)
    private val amount = BigDecimal("1.2")
    private val address = Address(ZCASH_ADDRESS)

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IConnectivityManager> { mockConnectivityManager() }
                single<UptimeProvider> { mockk(relaxed = true) }
                single<IBalanceHiddenManager> { balanceHiddenManager }
                single<MarketKitWrapper> { marketKit }
                single { poisonAddressManager }
                single { locallyCreatedTransactionRepository }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { adapter.fee } returns feeFlow
        every { adapter.balanceData } returns BalanceData(BigDecimal.TEN)
        every { adapter.balanceUpdatedFlow } returns balanceUpdatedFlow
        every { adapter.balanceState } returns AdapterState.Synced
        // runTest drains the scheduler at the end: a plan unavailable forever would keep it from ever finishing.
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { ZcashFeeEstimate.Planned(feeFlow.value) }
        coEvery { adapter.maxSendable(any(), any()) } coAnswers { adapter.maxSpendableBalance }
        coEvery { adapter.validate(any()) } returns ZcashAdapter.ZCashAddressType.Shielded
        coEvery { adapter.signOffline(any()) } returns signedTransaction()
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { adapterManager.getAdjustedBalanceData(any()) } returns null
        every { adapterManager.getBalanceAdapterForWallet(wallet) } returns adapter
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onClickSignOffline_validTransaction_savesDraft() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        viewModel.onEnterMemo("memo")
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertSigned(viewModel)
        val draft = draftSlot.captured
        assertEquals(zcashToken, draft.wallet.token)
        assertEquals(zcashToken, draft.feeToken)
        assertEquals("deadbeefdeadbeefdead", draft.rawHex)
        assertEquals(ZCASH_TX_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(FEE, draft.fee)
        assertEquals(address.hex, draft.toAddress)
        assertTrue(draft.inputOutpoints.isEmpty())
        assertEquals(null, draft.solanaRetryMetadata)
        assertEquals(null, draft.tonRetryMetadata)
        assertEquals(null, draft.tronRetryMetadata)
        assertEquals(null, draft.stellarRetryMetadata)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineZcashSignRequest &&
                        it.amount == amount &&
                        it.address == ZCASH_ADDRESS &&
                        it.memo == "memo"
                }
            )
        }
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun fee_recalculatedWhileTheMaximumIsUnchanged_isPublishedToTheScreen() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        val viewModel = createViewModel()
        advanceUntilIdle()

        feeFlow.value = RECALCULATED_FEE
        advanceUntilIdle()

        assertEquals(RECALCULATED_FEE, viewModel.uiState.fee)
    }

    @Test
    fun init_maximumChangedBeforeSubscription_isReReadFromTheAdapter() = runTest(dispatcher) {
        // balanceUpdatedFlow is hot: a snapshot published between the factory's read and the
        // collector's subscription is dropped, so the seeded value is already stale here.
        every { adapter.maxSpendableBalance } returns FRESH_MAXIMUM

        val viewModel = createViewModel(seededAvailableBalance = STALE_MAXIMUM)
        advanceUntilIdle()

        assertEquals(FRESH_MAXIMUM, viewModel.uiState.availableBalance)
    }

    @Test
    fun availableBalance_threeNotesRecipientKnown_lets0_00055Through() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal("0.00045")
        coEvery { adapter.maxSendable(ZCASH_ADDRESS, "") } returns BigDecimal("0.00065")
        val viewModel = createViewModel(seededAvailableBalance = BigDecimal("0.00045"))

        viewModel.onEnterAmount(BigDecimal("0.00055"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.amountCaution != null)

        viewModel.onEnterAddress(address)
        advanceUntilIdle()

        assertEquals(BigDecimal("0.00065"), viewModel.uiState.availableBalance)
        assertEquals(null, viewModel.uiState.amountCaution)
        assertTrue(viewModel.uiState.canBeSend)
    }

    @Test
    fun availableBalance_noAddress_isTheLowerEstimate() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal("0.00045")
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(BigDecimal("0.00045"), viewModel.uiState.availableBalance)
    }

    @Test
    fun fee_amountAndAddressEntered_isTheEstimateForThem() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.estimateFee(amount, ZCASH_ADDRESS, "memo") } returns ZcashFeeEstimate.Planned(ESTIMATED_FEE)
        val viewModel = enteredViewModel()

        assertEquals(ESTIMATED_FEE, viewModel.uiState.fee)
        assertEquals(ESTIMATED_FEE, viewModel.getConfirmationData().fee)
    }

    @Test
    fun canBeSend_estimateInFlight_isFalseUntilItArrives() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        val estimate = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { estimate.await() }
        val viewModel = enteredViewModel()

        assertFalse(viewModel.uiState.canBeSend)

        estimate.complete(ZcashFeeEstimate.Planned(ESTIMATED_FEE))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.canBeSend)
        assertEquals(ESTIMATED_FEE, viewModel.uiState.fee)
    }

    @Test
    fun canBeSend_noFeasiblePlanForTheAmount_isFalseWithTheInsufficientBalanceError() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.InsufficientBalance
        val viewModel = enteredViewModel()

        assertFalse(viewModel.uiState.canBeSend)
        val caution = (viewModel.uiState.amountCaution?.s as? TranslatableString.ResString)?.id
        assertEquals(Res.string.Swap_ErrorInsufficientBalance, caution)
    }

    @Test
    fun canBeSend_otherPlanningFailure_staysOpenOnTheAdapterFee() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.estimateFee(any(), any(), any()) } returnsMany
            listOf(ZcashFeeEstimate.Unavailable, ZcashFeeEstimate.Planned(ESTIMATED_FEE))
        val viewModel = createViewModel().apply {
            onEnterAddress(address)
            onEnterAmount(amount)
        }
        settle()

        assertTrue(viewModel.uiState.canBeSend)
        assertEquals(null, viewModel.uiState.amountCaution)
        assertEquals(FEE, viewModel.uiState.fee)
    }

    @Test
    fun fee_planningFailedThenRecovered_isTheEstimateAndStaysOpenThroughTheWait() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.estimateFee(any(), any(), any()) } returnsMany
            listOf(ZcashFeeEstimate.Unavailable, ZcashFeeEstimate.Planned(ESTIMATED_FEE))
        val viewModel = createViewModel().apply {
            onEnterAddress(address)
            onEnterAmount(amount)
        }
        settle()

        advanceTimeBy(RETRY_MS - SETTLE_MS)
        assertEquals(FEE, viewModel.uiState.fee)
        assertTrue(viewModel.uiState.canBeSend)

        settle()
        assertEquals(ESTIMATED_FEE, viewModel.uiState.fee)
        assertTrue(viewModel.uiState.canBeSend)
    }

    @Test
    fun availableBalance_planningFailedThenRecovered_unblocksTheAmount() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal("0.00045")
        coEvery { adapter.maxSendable(any(), any()) } returnsMany listOf(null, BigDecimal("0.00065"))
        val viewModel = createViewModel(seededAvailableBalance = BigDecimal("0.00045")).apply {
            onEnterAddress(address)
            onEnterAmount(BigDecimal("0.00055"))
        }
        settle()
        assertTrue(viewModel.uiState.amountCaution != null)
        assertFalse(viewModel.uiState.canBeSend)

        advanceTimeBy(RETRY_MS)
        settle()

        assertEquals(BigDecimal("0.00065"), viewModel.uiState.availableBalance)
        assertEquals(null, viewModel.uiState.amountCaution)
        assertTrue(viewModel.uiState.canBeSend)
    }

    @Test
    fun confirmationSendEnabled_retryFindsNoFeasiblePlan_closesWithTheInsufficientBalanceCaution() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.estimateFee(any(), any(), any()) } returnsMany
            listOf(ZcashFeeEstimate.Unavailable, ZcashFeeEstimate.InsufficientBalance)
        val viewModel = createViewModel().apply {
            onEnterAddress(address)
            onEnterAmount(amount)
        }
        settle()
        assertTrue(viewModel.confirmationSendEnabled)
        assertEquals(null, viewModel.confirmationCaution)

        advanceTimeBy(RETRY_MS)
        runCurrent()

        assertFalse(viewModel.confirmationSendEnabled)
        val caution = (viewModel.confirmationCaution?.s as? TranslatableString.ResString)?.id
        assertEquals(Res.string.Swap_ErrorInsufficientBalance, caution)
    }

    @Test
    fun confirmationSendEnabled_estimateInFlight_isClosedUntilItArrives() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        val estimate = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { estimate.await() }
        val viewModel = enteredViewModel()
        assertFalse(viewModel.confirmationSendEnabled)

        estimate.complete(ZcashFeeEstimate.Planned(ESTIMATED_FEE))
        advanceUntilIdle()

        assertTrue(viewModel.confirmationSendEnabled)
    }

    @Test
    fun confirmationCaution_balanceSpentByTheSentTransaction_isHidden() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.send(any(), any(), any()) } returns ZCASH_TX_HASH
        coEvery { pendingRegistrar.register(any()) } returns "draft"
        val viewModel = enteredViewModel()
        viewModel.onClickSend()
        advanceUntilIdle()

        every { adapter.maxSpendableBalance } returns BigDecimal("0.1")
        balanceUpdatedFlow.emit(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.amountCaution != null)
        assertEquals(null, viewModel.confirmationCaution)
    }

    @Test
    fun onClickSend_estimatedFee_registersThePendingRowWithIt() = runTest(dispatcher) {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(ESTIMATED_FEE)
        coEvery { adapter.send(any(), any(), any()) } returns ZCASH_TX_HASH
        val draftSlot = slot<PendingTransactionDraft>()
        coEvery { pendingRegistrar.register(capture(draftSlot)) } returns "draft"
        val viewModel = enteredViewModel()

        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(ESTIMATED_FEE, draftSlot.captured.fee)
    }

    @Test
    fun offlineSignSupported_mnemonicAccount_returnsTrue() {
        val viewModel = createViewModel()

        assertTrue(viewModel.offlineSignSupported)
    }

    @Test
    fun offlineSignSupported_ufvkAccount_returnsFalse() {
        val ufvkWallet = createWallet(zcashToken, account(AccountType.ZCashUfvKey("ufvk")))

        val viewModel = createViewModel(wallet = ufvkWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    @Test
    fun offlineSignSupported_ledgerAccount_returnsFalse() {
        val ledgerWallet = createWallet(zcashToken, account(AccountType.LedgerDevice("device", "nanoSP", "key")))

        assertFalse(createViewModel(wallet = ledgerWallet).offlineSignSupported)
    }

    @Test
    fun onClickSend_rejectedOnDevice_clearsResultWithoutCaution() = runTest(dispatcher) {
        coEvery { adapter.send(any(), any(), any()) } throws NotBroadcastException(LedgerRejectedException())
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSend()
        advanceUntilIdle()

        assertNull(viewModel.sendResult)
        coVerify { pendingRegistrar.deleteFailed(any()) }
    }

    @Test
    fun getConfirmationData_deviceShowsAnotherForm_showsItButSendsToTheEnteredAddress() = runTest(dispatcher) {
        every { adapter.addressShownOnDevice(ZCASH_ADDRESS) } returns DEVICE_FORM
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        assertEquals(DEVICE_FORM, viewModel.getConfirmationData().address.hex)
        viewModel.onClickSend()
        advanceUntilIdle()

        coVerify { adapter.send(amount, ZCASH_ADDRESS, any()) }
    }

    @Test
    fun onClickSend_deviceOutOfKeyDerivations_asksToReopenTheApp() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } answers { firstArg<StringResource>().key }
        coEvery { adapter.send(any(), any(), any()) } throws NotBroadcastException(LedgerException.KeyDerivationLimit())
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSend()
        advanceUntilIdle()

        val caution = (viewModel.sendResult as SendResult.Failed).caution.s
        assertEquals("ledger_zcash_reopen_app", (caution as TranslatableString.PlainString).text)
    }

    @Test
    fun canBeSend_multiByteMemo512Bytes_isTrue() = runTest(dispatcher) {
        assertTrue(canBeSendWithMemo(address, "ж".repeat(256)))
    }

    @Test
    fun canBeSend_multiByteMemo513Bytes_isFalse() = runTest(dispatcher) {
        assertFalse(canBeSendWithMemo(address, "ж".repeat(256) + "a"))
    }

    @Test
    fun canBeSend_overLimitMemoOnTransparentAddress_isTrue() = runTest(dispatcher) {
        assertTrue(canBeSendWithMemo(Address(TRANSPARENT_ADDRESS), "ж".repeat(257)))
    }

    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    private fun TestScope.enteredViewModel() = createViewModel().also {
        it.onEnterAddress(address)
        it.onEnterAmount(amount)
        it.onEnterMemo("memo")
        advanceUntilIdle()
    }

    private fun TestScope.canBeSendWithMemo(address: Address, memo: String): Boolean {
        every { adapter.maxSpendableBalance } returns BigDecimal.TEN
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        viewModel.onEnterMemo(memo)
        advanceUntilIdle()
        return viewModel.uiState.canBeSend
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        seededAvailableBalance: BigDecimal = BigDecimal.TEN,
    ) = SendZCashViewModel(
        adapter = adapter,
        wallet = wallet,
        xRateService = xRateService,
        amountService = SendAmountService(AmountValidator(), wallet.coin.code, seededAvailableBalance),
        addressService = SendZCashAddressService(adapter),
        memoService = SendZCashMemoService(memoSupportedByAccount = true),
        contactsRepo = contactsRepository,
        address = null,
        showAddressInput = true,
        pendingRegistrar = pendingRegistrar,
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
    )

    private fun signedTransaction() = SignedOfflineZcashTransaction(
        rawHex = "deadbeefdeadbeefdead",
        txHash = ZCASH_TX_HASH,
        fee = FEE,
    )

    private fun assertSigned(viewModel: SendZCashViewModel) {
        when (val state = viewModel.offlineSignState) {
            is OfflineSignState.Signed -> Unit
            is OfflineSignState.Failed -> fail(state.caution.s.toString())
            else -> fail(state.toString())
        }
    }

    private fun createWallet(token: Token, account: Account) =
        checkNotNull(walletFactory.create(token, account, null))

    private fun account(type: AccountType) = Account(
        id = "account-${type::class.simpleName}",
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        const val SETTLE_MS = 5_000L
        const val RETRY_MS = 15_000L
        const val ZCASH_ADDRESS = "zs1recipient000000000000000000000000000000000000000000000000000000000000"
        const val DEVICE_FORM = "u1orchardonly"
        const val TRANSPARENT_ADDRESS = "t1recipient0000000000000000000000000"
        const val ZCASH_TX_HASH = "456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123"
        val FEE: BigDecimal = BigDecimal("0.0001")
        val RECALCULATED_FEE: BigDecimal = BigDecimal("0.0003")
        val ESTIMATED_FEE: BigDecimal = BigDecimal("0.00015")
        val STALE_MAXIMUM: BigDecimal = BigDecimal("0.5")
        val FRESH_MAXIMUM: BigDecimal = BigDecimal("7.25")
    }
}
