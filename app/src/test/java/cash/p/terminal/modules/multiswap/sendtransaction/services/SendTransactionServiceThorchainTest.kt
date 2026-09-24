package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.App
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.thorchain.ThorchainAdapter
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.factories.ThorchainTransactionConverter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.ThorchainKitWrapper
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.transaction.TransactionSource
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.CoinTransfer
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.transaction.TransactionSender.SendError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SendTransactionServiceThorchainTest : KoinTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val kit = mockk<ThorchainKit>(relaxed = true)
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private val rune = token(TokenType.Native, "RUNE")
    private val tcy = token(TokenType.ThorchainAsset("tcy"), "TCY")

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
                single<CurrencyManager> { mockk(relaxed = true) }
                single { locallyCreatedTransactionRepository }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        mockkObject(App)
        every { App.marketKit } returns marketKit
        every { App.currencyManager } returns mockk(relaxed = true)
        every { marketKit.token(any()) } returns rune
        every { marketKit.coinPrice(any(), any()) } returns null
        every { kit.network } returns Network.Mainnet
        every { kit.getDenomBalanceFlow(any()) } returns emptyFlow()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun setSendTransactionData_tokenWithRuneBelowFee_cautionsAndBlocksSending() = runTest(dispatcher) {
        every { kit.getDenomBalance("tcy") } returns BigInteger("500000000")
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000")
        val service = service(tcy)

        service.setSendTransactionData(SendTransactionData.Thorchain.Deposit("THOR.TCY", BigDecimal.ONE, MEMO))

        val state = service.stateFlow.value
        assertEquals(CautionViewItem.Type.Error, state.cautions.single().type)
        assertFalse(state.sendable)
    }

    @Test
    fun setSendTransactionData_runeCoversAmountAndFee_isSendable() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        val service = service(rune)

        service.setSendTransactionData(SendTransactionData.Thorchain.Deposit("THOR.RUNE", BigDecimal.ONE, MEMO))

        val state = service.stateFlow.value
        assertTrue(state.cautions.isEmpty())
        assertTrue(state.sendable)
    }

    @Test
    fun send_depositPossiblyAccepted_returnsSubmittedHash() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.deposit(any(), any(), any(), any()) } throws SendError.PossiblyAccepted("ab12")
        val service = service(rune)
        service.setSendTransactionData(SendTransactionData.Thorchain.Deposit("THOR.RUNE", BigDecimal.ONE, MEMO))

        val result = service.send()

        assertEquals(SendTransactionResult.Thorchain("AB12", "AB12-rune"), result)
        coVerify { locallyCreatedTransactionRepository.markCreated(any<Wallet>(), "AB12") }
    }

    @Test
    fun send_sendData_transfersToInboundVault() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } returns "HASH"
        val service = service(rune)
        service.setSendTransactionData(SendTransactionData.Thorchain.Send(VAULT, BigDecimal.ONE, MEMO))

        assertEquals(SendTransactionResult.Thorchain("HASH", "HASH-rune"), service.send())
        coVerify { kit.send(any(), BigInteger("100000000"), "rune", MEMO, any()) }
    }

    @Test
    fun send_runeDeposit_recordUidMatchesHistoryRecord() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.deposit(any(), any(), any(), any()) } returns HASH
        val service = service(rune)
        service.setSendTransactionData(SendTransactionData.Thorchain.Deposit("THOR.RUNE", BigDecimal.ONE, MEMO))

        assertEquals(historyRecordUid("THOR.RUNE"), service.send().getRecordUid())
    }

    @Test
    fun send_tokenSend_recordUidMatchesHistoryRecord() = runTest(dispatcher) {
        every { kit.getDenomBalance("tcy") } returns BigInteger("500000000")
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } returns HASH
        val service = service(tcy)
        service.setSendTransactionData(SendTransactionData.Thorchain.Send(VAULT, BigDecimal.ONE, MEMO))

        assertEquals(historyRecordUid("TCY"), service.send().getRecordUid())
    }

    @Test
    fun send_sendData_canonicalTxHashIsBareHash() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } returns HASH
        val service = service(rune)
        service.setSendTransactionData(SendTransactionData.Thorchain.Send(VAULT, BigDecimal.ONE, MEMO))

        assertEquals(HASH, service.send().getCanonicalTxHash())
    }

    @Test
    fun start_feeRisesAboveBalance_blocksSendingUntilCoveredAgain() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("110000000")
        val wrapper = ThorchainKitWrapper(kit)
        val service = service(rune, wrapper)
        service.start(backgroundScope)
        service.setSendTransactionData(SendTransactionData.Thorchain.Deposit("THOR.RUNE", BigDecimal.ONE, MEMO))
        assertTrue(service.stateFlow.value.sendable)

        wrapper.nativeFeeState.value = BigInteger("20000000")
        assertEquals(CautionViewItem.Type.Error, service.stateFlow.value.cautions.single().type)
        assertFalse(service.stateFlow.value.sendable)

        wrapper.nativeFeeState.value = BigInteger("2000000")
        assertTrue(service.stateFlow.value.sendable)
    }

    @Test
    fun start_runeBalanceDropsBelowFee_blocksTokenSending() = runTest(dispatcher) {
        val runeBalance = MutableStateFlow(BigInteger("100000000"))
        every { kit.getDenomBalance("tcy") } returns BigInteger("500000000")
        every { kit.getDenomBalance("rune") } answers { runeBalance.value }
        every { kit.getDenomBalanceFlow("rune") } returns runeBalance
        val service = service(tcy)
        service.start(backgroundScope)
        service.setSendTransactionData(SendTransactionData.Thorchain.Deposit("THOR.TCY", BigDecimal.ONE, MEMO))
        assertTrue(service.stateFlow.value.sendable)

        runeBalance.value = BigInteger("1000000")

        assertFalse(service.stateFlow.value.sendable)
    }

    private fun service(
        token: Token,
        wrapper: ThorchainKitWrapper = ThorchainKitWrapper(kit),
    ): SendTransactionServiceThorchain {
        val wallet = checkNotNull(walletFactory.create(token, ACCOUNT, null))
        val adapter = ThorchainAdapter(
            wrapper,
            wallet,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
        coEvery { walletUseCase.createWalletIfNotExists(token) } returns wallet
        coEvery { adapterManager.awaitAdapterForWallet<ThorchainAdapter>(wallet, any()) } returns adapter
        every { adapterManager.getAdjustedBalanceData(wallet) } returns null
        return SendTransactionServiceThorchain(token)
    }

    // The uid the history converter gives the user's spend of `midgardAsset` in transaction HASH.
    private fun historyRecordUid(midgardAsset: String): String {
        val coinManager = mockk<ICoinManager> { every { getToken(any()) } returns null }
        val source = TransactionSource(rune.blockchain, ACCOUNT, null)
        val spend = CoinTransfer(USER, midgardAsset, BigInteger.ONE)
        val transaction = Transaction(HASH, 1, 0, "swap", "success", MEMO, listOf(spend), emptyList())
        return ThorchainTransactionConverter(coinManager, source, USER, rune, Network.Mainnet)
            .convert(transaction).single().uid
    }

    private fun token(type: TokenType, code: String) = Token(
        coin = Coin(uid = code.lowercase(), name = code, code = code),
        blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null),
        type = type,
        decimals = 8,
    )

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })

    private companion object {
        const val MEMO = "=:ETH.ETH:0xabc"
        const val VAULT = "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"
        const val USER = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"
        const val HASH = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        val ACCOUNT = Account(
            id = "account-id",
            name = "Account",
            type = AccountType.Mnemonic(List(11) { "abandon" } + "about", ""),
            origin = AccountOrigin.Created,
            level = 0,
        )
    }
}
