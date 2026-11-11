package cash.p.terminal.modules.nft.transfer

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.storage.PoisonAddressDao
import cash.p.terminal.core.storage.SingleDatabaseStorageRouter
import cash.p.terminal.core.storage.StaticAppDatabaseProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.PoisonAddress
import cash.p.terminal.entities.PoisonAddressType
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.mockConnectivityManager
import cash.p.terminal.modules.send.register
import cash.p.terminal.modules.send.ton.FeeStatus
import cash.p.terminal.modules.send.ton.SendTonFeeService
import cash.p.terminal.modules.send.ton.TonFeeQuote
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.insufficient_balance_required_available
import cash.p.terminal.resources.not_enough_ton_for_fee
import cash.p.terminal.resources.swap_insufficient_fee_balance
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.FriendlyAddress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.ton.block.AddrStd
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class NftTransferViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val wallet = WalletFactory.previewWallet()
    private val item = previewNftItem("nft-address")
    private val adapter = mockk<ISendTonAdapter>(relaxed = true) {
        every { balanceUpdatedFlow } returns emptyFlow()
    }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val feeFlow = MutableStateFlow(SendTonFeeService.State(feeStatus = null, inProgress = false))
    private val feeService = mockk<SendTonFeeService>(relaxed = true) {
        every { stateFlow } returns feeFlow
    }
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val recentAddressManager = mockk<RecentAddressManager>(relaxed = true)
    private val offlineGate = mockk<OfflineOperationGate>(relaxed = true) {
        every { isBlocked(any()) } returns false
    }
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true) {
        every { formatCoinFull(any(), any(), any()) } returns "0.01 TON"
    }
    private val viewModelStore = ViewModelStore()

    private val tonAddress = FriendlyAddress(AddrStd(0, ByteArray(32) { 1 }), isBounceable = false)
    private val recipient = Address(tonAddress.addrStd.toString(userFriendly = true, bounceable = false))
    private val bounceableRecipient = tonAddress.addrStd.toString(userFriendly = true, bounceable = true)
    private val knownRaw = "0:" + "01".repeat(32)
    private val lookalikeRaw = "0:01" + "02".repeat(29) + "0101"
    private val lookalike = FriendlyAddress(AddrStd.parseRaw(lookalikeRaw), isBounceable = false)
    private val poisonDao = mockk<PoisonAddressDao>(relaxed = true) {
        every { get(any(), any(), any()) } returns null
        every { getWhitelisted(any(), any(), any()) } returns emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { adapterManager.getBalanceAdapterForWallet(wallet) } returns null
        setBalance("1")
        startKoin {
            modules(module {
                single { mockk<MarketKitWrapper>(relaxed = true) }
                single<IBalanceHiddenManager> { balanceHiddenManager }
                single { poisonAddressManager }
                single { mockk<LocallyCreatedTransactionRepository>(relaxed = true) }
                single { mockConnectivityManager() }
                single { offlineGate }
                single<UptimeProvider> { mockk(relaxed = true) }
                single { numberFormatter }
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

    @Test
    fun canContinue_quoteForExactInputsAndEnoughBalance_true() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        assertTrue(viewModel.canContinue)
    }

    @Test
    fun canContinue_noRecipient_false() = runTest(dispatcher) {
        val viewModel = createViewModel()
        publishQuote()
        advanceUntilIdle()

        assertFalse(viewModel.canContinue)
    }

    @Test
    fun canContinue_quoteForPreviousComment_false() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        viewModel.onEnterComment("new comment")
        advanceUntilIdle()

        assertFalse(viewModel.canContinue)
    }

    @Test
    fun canContinue_commentMatchesQuote_true() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        viewModel.onEnterComment("hello")
        publishQuote(memo = "hello")
        advanceUntilIdle()

        assertTrue(viewModel.canContinue)
    }

    @Test
    fun canContinue_balanceBelowRequired_falseWithInlineError() = runTest(dispatcher) {
        setBalance("0.05")
        val viewModel = readyViewModel()

        assertFalse(viewModel.canContinue)
        assertNotNull(viewModel.uiState.insufficientBalance)
    }

    @Test
    fun canContinue_balanceEqualsRequired_true() = runTest(dispatcher) {
        setBalance("0.06")
        val viewModel = readyViewModel()

        assertTrue(viewModel.canContinue)
        assertNull(viewModel.uiState.insufficientBalance)
    }

    @Test
    fun canContinue_poisonAddress_requiresRiskAccepted() = runTest(dispatcher) {
        every { poisonAddressManager.isAddressSuspicious(recipient.hex, any(), any()) } returns true
        val viewModel = readyViewModel()

        assertFalse(viewModel.canContinue)

        viewModel.onRiskAcceptedChange(true)

        assertTrue(viewModel.canContinue)
    }

    @Test
    fun onEnterAddress_changedRecipient_resetsRiskAccepted() = runTest(dispatcher) {
        every { poisonAddressManager.isAddressSuspicious(recipient.hex, any(), any()) } returns true
        val viewModel = readyViewModel()
        viewModel.onRiskAcceptedChange(true)

        viewModel.onEnterAddress(recipient)
        advanceUntilIdle()

        assertFalse(viewModel.riskAccepted)
        assertFalse(viewModel.canContinue)
    }

    @Test
    fun canContinue_offlineBlocked_false() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        every { offlineGate.isBlocked(any()) } returns true

        assertFalse(viewModel.canContinue)
    }

    @Test
    fun onEnterComment_oversized_neverReachesFeeServiceAndBlocksContinue() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        val oversized = "x".repeat(NFT_COMMENT_MAX_BYTES + 1)

        viewModel.onEnterComment(oversized)
        advanceUntilIdle()

        verify(exactly = 0) { feeService.setMemo(oversized) }
        verify { feeService.setTonAddress(null) }
        assertFalse(viewModel.canContinue)
        assertNotNull(viewModel.uiState.commentError)
    }

    @Test
    fun onEnterComment_multibyteAtLimit_allowed() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        val atLimit = "я".repeat(NFT_COMMENT_MAX_BYTES / 2)

        viewModel.onEnterComment(atLimit)
        publishQuote(memo = atLimit)
        advanceUntilIdle()

        verify { feeService.setMemo(atLimit) }
        assertTrue(viewModel.canContinue)
    }

    @Test
    fun onClickSend_doubleTap_transfersOnce() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.sendNftTransfer(any(), any(), any()) }
        assertTrue(viewModel.sendResult is SendResult.Sent)
    }

    @Test
    fun onClickSend_afterSent_doesNotTransferAgain() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.sendNftTransfer(any(), any(), any()) }
    }

    @Test
    fun onClickSend_success_transfersQuotedInputsAndRemembersRecipient() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        viewModel.onEnterComment("hello")
        publishQuote(memo = "hello")
        advanceUntilIdle()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        coVerify { adapter.sendNftTransfer(item.address, match { it === tonAddress }, "hello") }
        verify { poisonAddressManager.saveKnownAddress(recipient.hex, any(), any()) }
        verify { recentAddressManager.setRecentAddress(recipient, BlockchainType.Ton) }
    }

    @Test
    fun onClickSend_notReady_doesNotTransfer() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        coVerify(exactly = 0) { adapter.sendNftTransfer(any(), any(), any()) }
        assertNull(viewModel.sendResult)
    }

    @Test
    fun onClickSend_failure_reportsFailedAndAllowsRetry() = runTest(dispatcher) {
        coEvery { adapter.sendNftTransfer(any(), any(), any()) } throws IllegalStateException("boom")
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()
        assertTrue(viewModel.sendResult is SendResult.Failed)

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        coVerify(exactly = 2) { adapter.sendNftTransfer(any(), any(), any()) }
    }

    @Test
    fun canContinue_scamStoredBounceableRecipientNonBounceable_requiresRiskAccepted() = runTest(dispatcher) {
        every { poisonAddressManager.isAddressSuspicious(bounceableRecipient, any(), any()) } returns true

        val viewModel = readyViewModel()

        assertTrue(viewModel.uiState.isPoisonAddress)
        assertFalse(viewModel.canContinue)
    }

    @Test
    fun onEnterAddress_scamStoredNonBounceableRecipientBounceable_flagsPoison() = runTest(dispatcher) {
        every { poisonAddressManager.isAddressSuspicious(recipient.hex, any(), any()) } returns true
        val viewModel = createViewModel()

        viewModel.onEnterAddress(Address(bounceableRecipient))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.isPoisonAddress)
    }

    @Test
    fun fee_balanceHiddenWithQuote_showsNoAmount() = runTest(dispatcher) {
        hideBalance()

        val viewModel = readyViewModel()

        assertEquals("*****", viewModel.uiState.fee)
    }

    @Test
    fun fee_balanceVisibleWithQuote_keepsAmount() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        assertEquals("≈ 0.01 TON", viewModel.uiState.fee)
    }

    @Test
    fun fee_balanceHiddenWithoutQuote_keepsPlaceholder() = runTest(dispatcher) {
        hideBalance()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("---", viewModel.uiState.fee)
    }

    @Test
    fun insufficientBalance_hiddenAndNoEnoughBalance_showsNoAmount() = runTest(dispatcher) {
        hideBalance()
        val viewModel = readyViewModel()
        publishNoEnoughBalance()
        advanceUntilIdle()

        assertHiddenBalanceText(viewModel.uiState.insufficientBalance)
    }

    @Test
    fun insufficientBalance_hiddenAndRequiredOverAvailable_showsNoAmount() = runTest(dispatcher) {
        hideBalance()
        setBalance("0.05")

        val viewModel = readyViewModel()

        assertHiddenBalanceText(viewModel.uiState.insufficientBalance)
        assertFalse(viewModel.canContinue)
    }

    @Test
    fun insufficientBalance_visibleAndNoEnoughBalance_showsAmount() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        publishNoEnoughBalance()
        advanceUntilIdle()

        val text = viewModel.uiState.insufficientBalance as TranslatableString.ResString
        assertEquals(Res.string.not_enough_ton_for_fee, text.id)
        assertEquals(listOf<Any>("1"), text.formatArgs.toList())
    }

    @Test
    fun insufficientBalance_visibleAndRequiredOverAvailable_showsAmount() = runTest(dispatcher) {
        setBalance("0.05")

        val viewModel = readyViewModel()

        val text = viewModel.uiState.insufficientBalance as TranslatableString.ResString
        assertEquals(Res.string.insufficient_balance_required_available, text.id)
    }

    @Test
    fun canContinue_rawRecipientWithRawScamEntry_requiresRiskAccepted() = runTest(dispatcher) {
        routePoisonLookupToRealManager()
        storeRawScamEntry()

        assertRequiresRiskAccepted(readyViewModel(entered = Address(knownRaw)))
    }

    @Test
    fun canContinue_friendlyRecipientWithRawScamEntry_requiresRiskAccepted() = runTest(dispatcher) {
        routePoisonLookupToRealManager()
        storeRawScamEntry()

        assertRequiresRiskAccepted(readyViewModel())
    }

    @Test
    fun canContinue_rawRecipientSimilarToKnownRaw_requiresRiskAccepted() = runTest(dispatcher) {
        routePoisonLookupToRealManager()
        storeKnownRawEntry()

        assertRequiresRiskAccepted(readyViewModel(entered = Address(lookalikeRaw), quoted = lookalike))
    }

    @Test
    fun canContinue_friendlyRecipientSimilarToKnownRaw_requiresRiskAccepted() = runTest(dispatcher) {
        routePoisonLookupToRealManager()
        storeKnownRawEntry()
        val friendly = lookalike.addrStd.toString(userFriendly = true, bounceable = false)

        assertRequiresRiskAccepted(readyViewModel(entered = Address(friendly), quoted = lookalike))
    }

    @Test
    fun canContinue_friendlyRecipientIsKnownRawAccount_true() = runTest(dispatcher) {
        routePoisonLookupToRealManager()
        storeKnownRawEntry()

        val viewModel = readyViewModel()

        assertFalse(viewModel.uiState.isPoisonAddress)
        assertTrue(viewModel.canContinue)
    }

    @Test
    fun canContinue_sendInFlight_false() = runTest(dispatcher) {
        val broadcast = CompletableDeferred<Unit>()
        coEvery { adapter.sendNftTransfer(any(), any(), any()) } coAnswers { broadcast.await() }
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        runCurrent()

        assertTrue(viewModel.sendResult is SendResult.Sending)
        assertFalse(viewModel.canContinue)
        broadcast.complete(Unit)
    }

    @Test
    fun canContinue_afterSent_false() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Sent)
        assertFalse(viewModel.canContinue)
    }

    @Test
    fun canContinue_afterFailed_true() = runTest(dispatcher) {
        coEvery { adapter.sendNftTransfer(any(), any(), any()) } throws IllegalStateException("boom")
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Failed)
        assertTrue(viewModel.canContinue)
    }

    @Test
    fun onClickSend_knownAddressWriteFailsAfterBroadcast_staysSentWithoutRetry() = runTest(dispatcher) {
        every { poisonAddressManager.saveKnownAddress(any(), any(), any()) } throws IllegalStateException("disk")
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()
        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Sent)
        coVerify(exactly = 1) { adapter.sendNftTransfer(any(), any(), any()) }
    }

    @Test
    fun onClickSend_recentAddressWriteFailsAfterBroadcast_staysSent() = runTest(dispatcher) {
        every { recentAddressManager.setRecentAddress(any(), any()) } throws IllegalStateException("disk")
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Sent)
    }

    @Test
    fun onClickSend_cancelledDuringSend_doesNotOfferRetry() = runTest(dispatcher) {
        coEvery { adapter.sendNftTransfer(any(), any(), any()) } throws CancellationException("cleared")
        val viewModel = readyViewModel()

        viewModel.onClickSendWithWarningCheck()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Sending)
        assertFalse(viewModel.canContinue)
    }

    @Test
    fun confirmationData_stateReady_carriesQuotedFeeAndComment() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        viewModel.onEnterComment("hello")
        publishQuote(memo = "hello")
        advanceUntilIdle()

        val data = viewModel.confirmationData()

        assertEquals("hello", data?.comment)
        assertEquals(BigDecimal("0.01"), data?.fee)
        assertEquals(recipient.hex, data?.address?.hex)
    }

    private fun TestScope.readyViewModel(
        entered: Address = recipient,
        quoted: FriendlyAddress = tonAddress,
    ): NftTransferViewModel {
        val viewModel = createViewModel()
        viewModel.onEnterAddress(entered)
        publishQuote(address = quoted)
        advanceUntilIdle()
        return viewModel
    }

    private fun hideBalance() {
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns true
    }

    private fun publishNoEnoughBalance() {
        feeFlow.value = SendTonFeeService.State(feeStatus = FeeStatus.NoEnoughBalance, inProgress = false)
    }

    private fun assertHiddenBalanceText(text: TranslatableString?) {
        val resString = text as TranslatableString.ResString
        assertEquals(Res.string.swap_insufficient_fee_balance, resString.id)
        assertEquals(listOf<Any>(wallet.coin.code), resString.formatArgs.toList())
    }

    private fun routePoisonLookupToRealManager() {
        val manager = PoisonAddressManager(
            SingleDatabaseStorageRouter(StaticAppDatabaseProvider(mockk { every { poisonAddressDao() } returns poisonDao })),
            mockk<ContactsRepository> { every { getContactsFiltered(any(), any(), any()) } returns emptyList() },
            mockk(relaxed = true),
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } answers {
            manager.isAddressSuspicious(firstArg(), secondArg(), thirdArg())
        }
    }

    private fun storeRawScamEntry() {
        every { poisonDao.get(knownRaw, any(), any()) } returns
            PoisonAddress(knownRaw, "uid", "account", PoisonAddressType.SCAM)
    }

    private fun storeKnownRawEntry() {
        every { poisonDao.getWhitelisted(any(), any(), any()) } returns
            listOf(PoisonAddress(knownRaw, "uid", "account", PoisonAddressType.KNOWN))
    }

    private fun assertRequiresRiskAccepted(viewModel: NftTransferViewModel) {
        assertTrue(viewModel.uiState.isPoisonAddress)
        assertFalse(viewModel.canContinue)
        viewModel.onRiskAcceptedChange(true)
        assertTrue(viewModel.canContinue)
    }

    private fun publishQuote(memo: String? = null, address: FriendlyAddress = tonAddress) {
        feeFlow.value = SendTonFeeService.State(
            feeStatus = FeeStatus.Success(BigDecimal("0.01"), BigDecimal("0.06")),
            inProgress = false,
            quote = TonFeeQuote(BigDecimal("0.01"), NFT_TRANSFER_ATTACHED, address, memo),
        )
    }

    private fun setBalance(value: String) {
        every { adapterManager.getAdjustedBalanceDataForToken(wallet.token) } returns BalanceData(BigDecimal(value))
    }

    private fun createViewModel(): NftTransferViewModel {
        val viewModel = NftTransferViewModel(
            wallet = wallet,
            item = item,
            adapter = adapter,
            feeService = feeService,
            xRateService = mockk<XRateService>(relaxed = true) {
                every { getRate(any()) } returns null
                every { getRateFlow(any()) } returns emptyFlow()
            },
            contactsRepository = mockk<ContactsRepository>(relaxed = true) {
                every { getContactsFiltered(any(), any(), any()) } returns emptyList()
            },
            recentAddressManager = recentAddressManager,
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            adapterManager = adapterManager,
        )
        viewModelStore.register(viewModel)
        return viewModel
    }
}
