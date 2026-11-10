package cash.p.terminal.modules.nft.burn

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.modules.nft.NftOwnerWalletResolver
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.register
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.insufficient_balance_required_available
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.tonkit.FriendlyAddress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.ton.block.AddrStd
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class NftBurnViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private val tonToken = Token(
        coin = Coin(uid = "the-open-network", name = "Toncoin", code = "TON"),
        blockchain = Blockchain(BlockchainType.Ton, "TON", null),
        type = TokenType.Native,
        decimals = 9,
    )
    private val item = NftItemRecord(
        accountId = "account",
        blockchainType = BlockchainType.Ton,
        ownerAddress = "owner",
        address = NFT_ADDRESS,
        position = 0,
        name = "WIN 5000 USDT #4471",
        description = null,
        realOwnerAddress = null,
        collectionAddress = null,
        collectionName = null,
        collectionDescription = null,
        trust = NftTrust.None,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )
    private val wallet = mockk<Wallet> {
        every { token } returns tonToken
        every { coin } returns tonToken.coin
    }
    private val adapter = mockk<ISendTonAdapter> {
        every { balanceData } returns BalanceData(BigDecimal("1"))
        every { balanceState } returns AdapterState.Synced
        every { balanceUpdatedFlow } returns emptyFlow()
        every { balanceStateUpdatedFlow } returns emptyFlow()
        coEvery { estimateNftTransferFee(any(), any(), any()) } returns
            TonFeeEstimate(BigDecimal("0.01"), BigDecimal("0.05"))
        coEvery { sendNftTransfer(any(), any(), any()) } returns Unit
    }
    private val resolver = mockk<NftOwnerWalletResolver> {
        coEvery { wallet(NftOwner("account", BlockchainType.Ton, "owner")) } returns wallet
    }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true) {
        every { getAdapterForWallet<ISendTonAdapter>(wallet) } returns adapter
        every { getAdjustedBalanceData(any()) } returns null
    }
    private val balanceHiddenManager = mockk<IBalanceHiddenManager> {
        every { anyWalletVisibilityChangedFlow } returns emptyFlow()
        every { balanceHidden } returns false
        every { isWalletBalanceHidden(any()) } returns false
    }
    private val offlineGate = mockk<OfflineOperationGate> {
        every { isBlocked(any<Wallet>()) } returns false
        every { blockedFlow(any()) } returns flowOf(false)
    }
    private val xRateService = mockk<XRateService> {
        every { getRate(any()) } returns CurrencyValue(Currency("USD", "$", 2), BigDecimal("2"))
        every { getRateFlow(any()) } returns emptyFlow()
    }
    private val numberFormatter = mockk<IAppNumberFormatter> {
        every { formatCoinFull(any(), any(), any()) } answers {
            "${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()} ${secondArg<String>()}"
        }
        every { formatFiatFull(any(), any()) } answers {
            "${secondArg<String>()}${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()}"
        }
    }

    private val cancellation = IllegalStateException("cancelled on device")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(HardwareWalletSupport)
        every { HardwareWalletSupport.isUserCancellation(any()) } answers { firstArg<Throwable>() === cancellation }
        startKoin { modules(module { single { numberFormatter } }) }
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
    fun tonBurnAddress_parsed_zeroAccountOfBasechain() {
        assertEquals(AddrStd(0, ByteArray(32)), FriendlyAddress.parse(TON_BURN_ADDRESS).addrStd)
    }

    @Test
    fun uiState_feeEstimated_feeAndShortBurnAddressShown() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertEquals("≈ 0.01 TON · $0.02", viewModel.uiState.fee)
        assertEquals("UQAA…AJKZ", viewModel.uiState.burnAddress)
        assertTrue(viewModel.uiState.canBurn)
        coVerify(exactly = 1) { adapter.estimateNftTransferFee(NFT_ADDRESS, match(::isBurnAddress), null) }
    }

    @Test
    fun uiState_balanceBelowRequired_insufficientAndBurnBlocked() = runTest(dispatcher) {
        every { adapter.balanceData } returns BalanceData(BigDecimal("0.03"))
        val viewModel = createViewModel()

        val text = viewModel.uiState.insufficientBalance as TranslatableString.ResString
        assertEquals(Res.string.insufficient_balance_required_available, text.id)
        assertFalse(viewModel.uiState.canBurn)

        viewModel.onBurn()
        advanceUntilIdle()

        coVerify(exactly = 0) { adapter.sendNftTransfer(any(), any(), any()) }
    }

    @Test
    fun uiState_domainBalanceHidden_nameMasked() = runTest(dispatcher) {
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns true
        val viewModel = createViewModel(domain = "piratecash.ton")

        assertEquals("****", viewModel.uiState.name)
        assertTrue(viewModel.uiState.isDomain)
    }

    @Test
    fun onBurn_ready_sentOnceToBurnAddressWithoutComment() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onBurn()
        advanceUntilIdle()

        assertEquals(NftOperationOutcome.Done(sent = 1), viewModel.uiState.outcome)
        coVerify(exactly = 1) { adapter.sendNftTransfer(any(), any(), any()) }
        coVerify(exactly = 1) { adapter.sendNftTransfer(NFT_ADDRESS, match(::isBurnAddress), null) }
    }

    @Test
    fun onBurn_calledAgainAfterStart_sentOnce() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onBurn()
        viewModel.onBurn()
        advanceUntilIdle()
        viewModel.onBurn()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.sendNftTransfer(any(), any(), any()) }
    }

    @Test
    fun onBurn_sendFails_endedWithCaution() = runTest(dispatcher) {
        coEvery { adapter.sendNftTransfer(any(), any(), any()) } throws IllegalStateException("boom")
        val viewModel = createViewModel()

        viewModel.onBurn()
        advanceUntilIdle()

        val outcome = viewModel.uiState.outcome as NftOperationOutcome.Ended
        assertEquals(0, outcome.sent)
        assertEquals(1, outcome.total)
        assertEquals("boom", (outcome.caution.s as TranslatableString.PlainString).text)
        assertFalse(viewModel.uiState.canBurn)
    }

    @Test
    fun onBurn_deviceCancel_idleAndBurnAllowedAgain() = runTest(dispatcher) {
        coEvery { adapter.sendNftTransfer(any(), any(), any()) } throws cancellation
        val viewModel = createViewModel()

        viewModel.onBurn()
        advanceUntilIdle()

        assertEquals(NftOperationOutcome.Idle, viewModel.uiState.outcome)
        assertTrue(viewModel.uiState.canBurn)
    }

    private fun isBurnAddress(address: FriendlyAddress): Boolean =
        address.addrStd.toString(userFriendly = true, bounceable = false) == TON_BURN_ADDRESS

    private fun TestScope.createViewModel(domain: String? = null): NftBurnViewModel {
        val viewModel = NftBurnViewModel(
            item = item,
            domain = domain,
            namesRevealed = false,
            walletResolver = resolver,
            adapterManager = adapterManager,
            balanceHiddenManager = balanceHiddenManager,
            offlineOperationGate = offlineGate,
            xRateService = xRateService,
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
        viewModelStore.register(viewModel)
        runCurrent()
        return viewModel
    }

    private companion object {
        const val NFT_ADDRESS = "nft-1"
    }
}
