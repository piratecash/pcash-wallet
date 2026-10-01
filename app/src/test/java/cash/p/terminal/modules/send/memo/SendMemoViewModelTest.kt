package cash.p.terminal.modules.send.memo

import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.OfflineStellarSignRequest
import cash.p.terminal.core.OfflineThorchainSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineStellarTransaction
import cash.p.terminal.core.SignedOfflineThorchainTransaction
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.SendErrorInsufficientBalance
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.wallet.Account
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
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.send.mockConnectivityManager

@OptIn(ExperimentalCoroutinesApi::class)
class SendMemoViewModelTest : KoinTest {

    private interface TestSendMemoAdapter : ISendMemoAdapter,
        OfflineTransactionAdapter<SignedOfflineStellarTransaction>

    private interface TestSendThorchainAdapter : ISendMemoAdapter,
        INativeBalanceProvider,
        OfflineTransactionAdapter<SignedOfflineThorchainTransaction>

    private val dispatcher = UnconfinedTestDispatcher()
    private val adapter = mockk<TestSendMemoAdapter>(relaxed = true)
    private val thorchainAdapter = mockk<TestSendThorchainAdapter>(relaxed = true)
    private val xRateService = mockk<XRateService>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val offlineSignedTransactionRepository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val recentAddressManager = mockk<RecentAddressManager>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val amountValidator = mockk<AmountValidator>(relaxed = true)
    private val pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true)
    private val runeFee = MutableStateFlow(RUNE_FEE)
    private val runeBalanceUpdates = MutableSharedFlow<Unit>()
    private val sendTokenBalanceUpdates = MutableSharedFlow<Unit>()
    private var runeBalance = BigDecimal.TEN

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.StellarSecretKey("secret"),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )
    private val stellar = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val stellarToken = Token(
        coin = Coin(uid = "stellar", name = "Stellar", code = "XLM"),
        blockchain = stellar,
        type = TokenType.Native,
        decimals = 7,
    )
    private val wallet = createWallet(stellarToken)
    private val mnemonicAccount = account(AccountType.Mnemonic(listOf("word"), ""))
    private val thorchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val runeToken = Token(
        coin = Coin(uid = "thorchain", name = "THORChain", code = "RUNE"),
        blockchain = thorchain,
        type = TokenType.Native,
        decimals = 8,
    )
    private val tcyToken = Token(
        coin = Coin(uid = "tcy", name = "TCY", code = "TCY"),
        blockchain = thorchain,
        type = TokenType.ThorchainAsset("tcy"),
        decimals = 8,
    )
    private val amount = BigDecimal("1.2")
    private val address = Address("GAZXDMWYHMPM2WF6FCWEBIMJITKKTU6MLHYLCFRVB3WMXTNPVEHBOXRE")

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IConnectivityManager> { mockConnectivityManager() }
                single<IBalanceHiddenManager> { balanceHiddenManager }
                single<MarketKitWrapper> { marketKit }
                single { poisonAddressManager }
                single { locallyCreatedTransactionRepository }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { adapter.sendFee } returns SEND_FEE
        coEvery { adapter.getMinimumSendAmount(any()) } returns null
        every { contactsRepository.getContactsFiltered(any(), any()) } returns emptyList()
        every { xRateService.getRate(any()) } returns null
        every { xRateService.getRateFlow(any()) } returns flowOf<CurrencyValue>()
        every { balanceHiddenManager.balanceHiddenFlow } returns MutableStateFlow(false)
        every { poisonAddressManager.isAddressSuspicious(any(), any(), any()) } returns false
        every { amountValidator.validate(any(), any(), any(), any(), any()) } returns null
        every { payloadEncoder.encode(any()) } returns "payload"
        coEvery { offlineSignedTransactionRepository.save(any(), any()) } returns Unit
        coEvery { adapter.signOffline(any()) } returns signedTransaction()
        every { thorchainAdapter.sendFee } answers { runeFee.value }
        every { thorchainAdapter.sendFeeUpdatedFlow } returns runeFee.map { }
        every { thorchainAdapter.maxSpendableBalance } answers { BigDecimal.TEN - runeFee.value }
        every { thorchainAdapter.nativeBalanceUpdatedFlow } returns runeBalanceUpdates
        every { thorchainAdapter.balanceUpdatedFlow } returns sendTokenBalanceUpdates
        every { thorchainAdapter.nativeBalanceData } answers { BalanceData(runeBalance) }
        every { adapterManager.getAdjustedBalanceData(any()) } returns null
        every { adapterManager.getAdjustedBalanceDataForToken(runeToken) } answers { BalanceData(runeBalance) }
        every { thorchainAdapter.balanceData } returns BalanceData(BigDecimal.TEN)
        coEvery { thorchainAdapter.getMinimumSendAmount(any()) } returns null
        coEvery { pendingRegistrar.register(any()) } returns DRAFT_ID
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onClickSignOffline_nativeTransaction_savesStellarRetryMetadata() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        viewModel.onEnterMemo("memo")
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(stellarToken, draft.wallet.token)
        assertEquals(stellarToken, draft.feeToken)
        assertEquals("deadbeef", draft.rawHex)
        assertEquals(STELLAR_TX_HASH, draft.txHash)
        assertEquals(amount, draft.amount)
        assertEquals(SEND_FEE, draft.fee)
        assertEquals(address.hex, draft.toAddress)
        assertTrue(draft.inputOutpoints.isEmpty())
        assertEquals("GSource", draft.stellarRetryMetadata?.sourceAccountId)
        assertEquals(123_456_789L, draft.stellarRetryMetadata?.sequenceNumber)
        assertEquals(1_700_000_180L, draft.stellarRetryMetadata?.validUntil)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineStellarSignRequest &&
                            it.amount == amount &&
                            it.address == address.hex &&
                            it.memo == "memo"
                }
            )
        }
        coVerify { offlineSignedTransactionRepository.save(draft, "payload") }
    }

    @Test
    fun onClickSignOffline_assetTransaction_savesAssetWalletAndXlmFeeToken() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        val assetToken = assetToken()
        val assetWallet = createWallet(assetToken)
        val viewModel = createViewModel(
            wallet = assetWallet,
            feeToken = stellarToken,
        )
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals(assetToken, draft.wallet.token)
        assertEquals(stellarToken, draft.feeToken)
        assertEquals(SEND_FEE, draft.fee)
        assertEquals(amount, draft.amount)
        assertEquals(address.hex, draft.toAddress)
        coVerify {
            adapter.signOffline(
                match {
                    it is OfflineStellarSignRequest &&
                            it.amount == amount &&
                            it.address == address.hex
                }
            )
        }
    }

    @Test
    fun onClickSignOffline_userCancelled_resetsStateSilently() = runTest(dispatcher) {
        coEvery { adapter.signOffline(any()) } throws TrezorCancelledException()
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        assertEquals(OfflineSignState.Idle, viewModel.offlineSignState)
        coVerify(exactly = 0) { offlineSignedTransactionRepository.save(any(), any()) }
    }

    @Test
    fun offlineSignSupported_watchAccount_returnsFalse() {
        val watchAccount = account(AccountType.StellarAddress(address.hex))
        val watchWallet = createWallet(stellarToken, watchAccount)

        val viewModel = createViewModel(wallet = watchWallet)

        assertFalse(viewModel.offlineSignSupported)
    }

    @Test
    fun onEnterAmount_thorchainTokenWithRuneBelowFee_blocksSendWithFeeCaution() = runTest(dispatcher) {
        runeBalance = BigDecimal("0.01")
        val viewModel = createThorchainViewModel(tcyToken)

        enterThorchainSend(viewModel)

        assertFalse(viewModel.uiState.canBeSend)
        assertTrue(viewModel.uiState.amountCaution is SendErrorInsufficientBalance)
    }

    @Test
    fun onEnterAmount_thorchainTokenWithRuneCoveringFee_allowsSend() = runTest(dispatcher) {
        runeBalance = BigDecimal("0.05")
        val viewModel = createThorchainViewModel(tcyToken)

        enterThorchainSend(viewModel)

        assertTrue(viewModel.uiState.canBeSend)
        assertEquals(null, viewModel.uiState.amountCaution)
    }

    @Test
    fun onEnterMemo_thorchainMemoOver250Bytes_blocksSend() = runTest(dispatcher) {
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        viewModel.onEnterMemo("é".repeat(126))

        assertFalse(viewModel.uiState.canBeSend)
    }

    @Test
    fun onEnterMemo_thorchainMemoOf250Bytes_allowsSend() = runTest(dispatcher) {
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        viewModel.onEnterMemo("é".repeat(125))

        assertTrue(viewModel.uiState.canBeSend)
    }

    @Test
    fun onClickSend_thorchainHashReturned_keepsDraftAndMarksCreated() = runTest(dispatcher) {
        coEvery { thorchainAdapter.send(any(), any(), any()) } returns THORCHAIN_TX_HASH
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)
        viewModel.onEnterMemo("memo")

        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(SendResult.Sent(THORCHAIN_TX_HASH), viewModel.sendResult)
        coVerifyOrder {
            pendingRegistrar.register(
                match { it.amount == amount && it.toAddress == THORCHAIN_ADDRESS && it.memo == "memo" }
            )
            thorchainAdapter.send(amount, THORCHAIN_ADDRESS, "memo")
            pendingRegistrar.updateTxId(DRAFT_ID, THORCHAIN_TX_HASH)
        }
        coVerify { locallyCreatedTransactionRepository.markCreated(any<Wallet>(), THORCHAIN_TX_HASH) }
        coVerify(exactly = 0) { pendingRegistrar.deleteFailed(any()) }
    }

    @Test
    fun onClickSend_thorchainRejected_deletesDraft() = runTest(dispatcher) {
        coEvery { thorchainAdapter.send(any(), any(), any()) } throws IllegalStateException("rejected")
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        viewModel.onClickSend()
        advanceUntilIdle()

        assertTrue(viewModel.sendResult is SendResult.Failed)
        coVerify { pendingRegistrar.deleteFailed(DRAFT_ID) }
    }

    @Test
    fun onClickSend_thorchainCancelled_keepsDraft() = runTest(dispatcher) {
        coEvery { thorchainAdapter.send(any(), any(), any()) } throws CancellationException("left the screen")
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        viewModel.onClickSend()
        advanceUntilIdle()

        coVerify { pendingRegistrar.register(any()) }
        coVerify(exactly = 0) { pendingRegistrar.deleteFailed(any()) }
        assertFalse(viewModel.sendResult is SendResult.Failed)
    }

    @Test
    fun onClickSend_stellar_sendsWithoutPendingDraft() = runTest(dispatcher) {
        coEvery { adapter.send(any(), any(), any()) } returns STELLAR_TX_HASH
        val viewModel = createViewModel()
        viewModel.onEnterAddress(address)
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()

        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(SendResult.Sent(STELLAR_TX_HASH), viewModel.sendResult)
        coVerify { locallyCreatedTransactionRepository.markCreated(any<Wallet>(), STELLAR_TX_HASH) }
        coVerify(exactly = 0) { pendingRegistrar.register(any()) }
    }

    @Test
    fun onClickSignOffline_thorchain_savesDraftWithoutStellarMetadata() = runTest(dispatcher) {
        val draftSlot = slot<OfflineSignedTransactionDraft>()
        every { payloadEncoder.encode(capture(draftSlot)) } returns "payload"
        coEvery { thorchainAdapter.signOffline(any()) } returns
            SignedOfflineThorchainTransaction("0a1b", THORCHAIN_TX_HASH, RUNE_FEE)
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)
        viewModel.onEnterMemo("memo")

        viewModel.onClickSignOffline(OfflineTransactionFormat.Pcash)
        advanceUntilIdle()

        val draft = draftSlot.captured
        assertEquals("0a1b", draft.rawHex)
        assertEquals(THORCHAIN_TX_HASH, draft.txHash)
        assertEquals(RUNE_FEE, draft.fee)
        assertEquals(runeToken, draft.feeToken)
        assertEquals(null, draft.stellarRetryMetadata)
        coVerify {
            thorchainAdapter.signOffline(
                OfflineThorchainSignRequest(amount, THORCHAIN_ADDRESS, "memo")
            )
        }
    }

    @Test
    fun onEnterAddress_stellarAssetValidAddress_skipsAdapterTrustlineCheck() = runTest(dispatcher) {
        every { adapter.validate(any()) } throws IllegalStateException("trustline lookup")
        val viewModel = createViewModel(wallet = createWallet(assetToken()))

        viewModel.onEnterAddress(address)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.addressError)
        verify(exactly = 0) { adapter.validate(any()) }
    }

    @Test
    fun onClickSend_updateTxIdFailsAfterBroadcast_reportsSentAndKeepsDraft() = runTest(dispatcher) {
        coEvery { thorchainAdapter.send(any(), any(), any()) } returns THORCHAIN_TX_HASH
        coEvery { pendingRegistrar.updateTxId(any(), any()) } throws IllegalStateException("db")
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(SendResult.Sent(THORCHAIN_TX_HASH), viewModel.sendResult)
        coVerify(exactly = 0) { pendingRegistrar.deleteFailed(any()) }
    }

    @Test
    fun onClickSend_recentAddressFailsAfterBroadcast_reportsSentAndKeepsDraft() = runTest(dispatcher) {
        coEvery { thorchainAdapter.send(any(), any(), any()) } returns THORCHAIN_TX_HASH
        every { recentAddressManager.setRecentAddress(any(), any()) } throws IllegalStateException("db")
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(SendResult.Sent(THORCHAIN_TX_HASH), viewModel.sendResult)
        coVerify(exactly = 0) { pendingRegistrar.deleteFailed(any()) }
    }

    @Test
    fun sendFee_risesAfterCreation_confirmationAndDraftUseNewFee() = runTest(dispatcher) {
        coEvery { thorchainAdapter.send(any(), any(), any()) } returns THORCHAIN_TX_HASH
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        runeFee.value = RAISED_RUNE_FEE
        viewModel.onClickSend()
        advanceUntilIdle()

        assertEquals(RAISED_RUNE_FEE, viewModel.getConfirmationData().fee)
        assertEquals(RAISED_RUNE_FEE, viewModel.uiState.fee)
        coVerify { pendingRegistrar.register(match { it.fee == RAISED_RUNE_FEE }) }
    }

    @Test
    fun sendFee_risesAfterCreation_nativeAvailableBalanceFollows() = runTest(dispatcher) {
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        runeFee.value = RAISED_RUNE_FEE

        assertEquals(BigDecimal.TEN - RAISED_RUNE_FEE, viewModel.uiState.availableBalance)
    }

    @Test
    fun balanceUpdated_spendableRisesWithoutFeeChange_availableBalanceFollows() = runTest(dispatcher) {
        val viewModel = createThorchainViewModel()
        enterThorchainSend(viewModel)

        every { thorchainAdapter.maxSpendableBalance } returns RAISED_SPENDABLE
        sendTokenBalanceUpdates.emit(Unit)

        assertEquals(RAISED_SPENDABLE, viewModel.uiState.availableBalance)
    }

    @Test
    fun sendFee_risesAboveRuneBalance_blocksTokenSend() = runTest(dispatcher) {
        runeBalance = BigDecimal("0.025")
        val viewModel = createThorchainViewModel(tcyToken)
        enterThorchainSend(viewModel)

        runeFee.value = RAISED_RUNE_FEE

        assertFalse(viewModel.uiState.canBeSend)
    }

    @Test
    fun runeBalance_risesAboveFee_enablesTokenSendWithoutInput() = runTest(dispatcher) {
        runeBalance = BigDecimal("0.01")
        val viewModel = createThorchainViewModel(tcyToken)
        enterThorchainSend(viewModel)

        runeBalance = BigDecimal("0.05")
        runeBalanceUpdates.emit(Unit)

        assertTrue(viewModel.uiState.canBeSend)
    }

    @Test
    fun runeBalance_dropsBelowFee_blocksTokenSendWithoutInput() = runTest(dispatcher) {
        runeBalance = BigDecimal("0.05")
        val viewModel = createThorchainViewModel(tcyToken)
        enterThorchainSend(viewModel)

        runeBalance = BigDecimal("0.01")
        runeBalanceUpdates.emit(Unit)

        assertFalse(viewModel.uiState.canBeSend)
    }

    @Test
    fun runeBalance_adapterAheadOfNativeWallet_gateFollowsAdapter() = runTest(dispatcher) {
        runeBalance = BigDecimal("0.01")
        every { adapterManager.getAdjustedBalanceDataForToken(runeToken) } returns BalanceData(BigDecimal("0.01"))
        val viewModel = createThorchainViewModel(tcyToken)
        enterThorchainSend(viewModel)

        runeBalance = BigDecimal("0.05")
        runeBalanceUpdates.emit(Unit)

        assertTrue(viewModel.uiState.canBeSend)
    }

    private fun createThorchainViewModel(token: Token = runeToken) = createViewModel(
        wallet = createWallet(token, mnemonicAccount),
        feeToken = runeToken,
        adapter = thorchainAdapter,
        chain = SendMemoChain.Thorchain,
    )

    private fun TestScope.enterThorchainSend(viewModel: SendMemoViewModel) {
        viewModel.onEnterAddress(Address(THORCHAIN_ADDRESS))
        viewModel.onEnterAmount(amount)
        advanceUntilIdle()
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        feeToken: Token = stellarToken,
        adapter: ISendMemoAdapter = this.adapter,
        chain: SendMemoChain = SendMemoChain.Stellar,
    ) = SendMemoViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        adapter = adapter,
        coinMaxAllowedDecimals = wallet.token.decimals,
        xRateService = xRateService,
        address = null,
        showAddressInput = true,
        amountService = SendAmountService(amountValidator, wallet.coin.code, BigDecimal.TEN),
        addressService = SendMemoAddressService(adapter, chain),
        contactsRepo = contactsRepository,
        minimumAmountService = SendMemoMinimumAmountService(adapter),
        adapterManager = adapterManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        recentAddressManager = recentAddressManager,
        offlineTransactionPayloadEncoder = payloadEncoder,
        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
        chain = chain,
        pendingRegistrar = pendingRegistrar,
    )

    private fun signedTransaction() = SignedOfflineStellarTransaction(
        rawHex = "deadbeef",
        txHash = STELLAR_TX_HASH,
        fee = SEND_FEE,
        sourceAccountId = "GSource",
        sequenceNumber = 123_456_789L,
        validUntil = 1_700_000_180L,
    )

    private fun assetToken() = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = stellar,
        type = TokenType.Asset("USDC", "GIssuer"),
        decimals = 7,
    )

    private fun createWallet(token: Token, account: Account = this.account) =
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
        val SEND_FEE: BigDecimal = BigDecimal("0.00001")
        const val STELLAR_TX_HASH = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        val RUNE_FEE: BigDecimal = BigDecimal("0.02")
        val RAISED_RUNE_FEE: BigDecimal = BigDecimal("0.03")
        val RAISED_SPENDABLE: BigDecimal = BigDecimal("25")
        const val THORCHAIN_TX_HASH = "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890"
        const val THORCHAIN_ADDRESS = "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"
        const val DRAFT_ID = "draft-id"
    }
}
