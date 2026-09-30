package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.beam.BeamFailure
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.core.managers.BeamSendTestFixture
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.beam.BeamRecipient
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertFailsWith

class SendTransactionServiceBeamTest : BeamSendTestFixture(), KoinTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val token = Token(
        coin = Coin(uid = "beam", name = "Beam", code = "BEAM"),
        blockchain = Blockchain(BlockchainType.Beam, "Beam", null),
        type = TokenType.Native,
        decimals = 8,
    )
    private val wallet = mockk<Wallet> {
        every { account.id } returns "account"
        every { account.type } returns mockk<AccountType.Mnemonic>()
        every { coin } returns this@SendTransactionServiceBeamTest.token.coin
        every { token } returns this@SendTransactionServiceBeamTest.token
    }
    private val adapter = mockk<BeamAdapter>(relaxUnitFun = true) {
        every { accountId } returns "account"
        coEvery { withCriticalOperation<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true) {
        coEvery { awaitAdapterForWallet<BeamAdapter>(wallet, any()) } returns adapter
        every { getAdjustedBalanceData(wallet) } returns BalanceData(BigDecimal.TEN)
    }
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true) {
        every { token(any()) } returns token
        every { coinPrice(any(), any()) } returns null
    }
    private val currencyManager = mockk<CurrencyManager> {
        every { baseCurrency } returns Currency("USD", "$", 2, 0)
    }
    private val quoted = mutableListOf<BeamQuoteRequest>()
    private var version = "v1"

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(module {
            single<WalletUseCase> { mockk { coEvery { createWalletIfNotExists(token) } returns wallet } }
            single<IAdapterManager> { adapterManager }
            single<MarketKitWrapper> { marketKit }
            single<IAppNumberFormatter> { mockk(relaxed = true) }
            single<CurrencyManager> { currencyManager }
            single<LocallyCreatedTransactionRepository> { mockk(relaxed = true) }
            single<OfflineOperationGate> { mockk(relaxed = true) }
            single<BeamSessionOwner> { owner }
            single<BeamSendCoordinator> { coordinator }
            single<OfflineModeManager> { mockk(relaxed = true) }
            single<DispatcherProvider> { TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)) }
        })
    }

    @Before
    fun setUp() {
        mockkObject(App, BeamRecipient)
        every { App.marketKit } returns marketKit
        every { App.currencyManager } returns currencyManager
        every { BeamRecipient.isSupported(any()) } answers { firstArg<String>() != "malformed" }
        sdk.quotes = { request -> quoted += request; onlineQuote(request, version) }
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    private suspend fun quotedService(memo: String? = null) = SendTransactionServiceBeam(token).also {
        openSession()
        it.setSendTransactionData(SendTransactionData.Beam("deposit", BigDecimal("0.000001"), memo))
    }

    private fun uid(transactionId: String) = "beam:7:account:$transactionId"

    @Test
    fun quote_showsTheFeeAndAllowsTheSwap() = runTest(dispatcher) {
        val state = quotedService().stateFlow.value

        assertTrue(state.sendable)
        assertFalse(state.loading)
        assertEquals(BigDecimal("0.00000010"), state.networkFee?.primary?.value)
    }

    @Test
    fun memo_isQuotedAsTheTransactionComment() = runTest(dispatcher) {
        quotedService(memo = "extra-id")

        assertEquals("extra-id", quoted.single().comment)
    }

    @Test
    fun send_submitted_isSentWithTheHistoryRecordUid() = runTest(dispatcher) {
        val result = quotedService().send()

        assertEquals(SendTransactionResult.Beam(SendResult.Sent(uid(TX_ID)), TX_ID), result)
        assertEquals(1, sdk.prepares.size)
    }

    @Test
    fun send_admissionDeferredAfterPrepare_isQueuedWithTheRecordUid() = runTest(dispatcher) {
        val service = quotedService()
        sdk.seedUnrelatedPrepared(txId = "33333333333333333333333333333333")

        val result = service.send()

        assertEquals(SendTransactionResult.Beam(SendResult.SentButQueued(uid(TX_ID)), TX_ID), result)
    }

    @Test
    fun send_failureBeforePrepare_failsAndCreatesNoOperation() = runTest(dispatcher) {
        val service = quotedService()
        sdk.beforePrepare = { throw BeamFailure.InsufficientFunds("secret") }

        val error = assertFailsWith<LocalizedException> { service.send() }

        assertEquals(R.string.Swap_ErrorInsufficientBalance, error.errorTextRes)
        assertTrue(sdk.operations.isEmpty())
        assertFalse(service.stateFlow.value.sendable)
    }

    @Test
    fun send_retryAfterFailureBeforePrepare_preparesOneOperation() = runTest(dispatcher) {
        val service = quotedService()
        sdk.beforePrepare = { throw BeamFailure.InsufficientFunds("secret") }
        assertFailsWith<LocalizedException> { service.send() }
        sdk.beforePrepare = {}

        assertTrue(service.send() is SendTransactionResult.Beam)

        assertEquals(1, sdk.operations.size)
        assertEquals(1, sdk.commits.size)
    }

    @Test
    fun send_completedOperationFoundAfterAnError_isSent() = runTest(dispatcher) {
        val service = quotedService()
        sdk.afterCommit = {
            sdk.resolutions[sdk.operations.keys.single()] =
                BeamSendResolution.Terminal(TX_ID, BeamTransactionStatus.Completed)
            throw secretFailure()
        }

        val result = service.send()

        assertEquals(SendTransactionResult.Beam(SendResult.Sent(uid(TX_ID)), TX_ID), result)
    }

    @Test
    fun send_quoteChanged_requotesSoTheNextTapSendsOnce() = runTest(dispatcher) {
        val service = quotedService()
        version = "v2"

        val error = assertFailsWith<SendException> { service.send() }
        assertEquals(Reason.QuoteChanged, error.reason)
        assertTrue(sdk.operations.isEmpty())
        assertTrue(service.stateFlow.value.sendable)

        assertTrue(service.send() is SendTransactionResult.Beam)
        assertEquals(1, sdk.prepares.size)
    }

    @Test
    fun send_outcomeUnknown_staysUnsendableForThisScreen() = runTest(dispatcher) {
        val service = quotedService()
        sdk.afterPrepare = { sdk.beforeInventory = { throw secretFailure() } }

        val error = assertFailsWith<LocalizedException> { service.send() }
        assertEquals(R.string.beam_send_uncertain, error.errorTextRes)
        val quotesBefore = quoted.size

        sdk.beforeInventory = {}
        service.setSendTransactionData(SendTransactionData.Beam("deposit", BigDecimal("0.000002"), null))
        assertFalse(service.stateFlow.value.sendable)
        assertEquals(1, service.stateFlow.value.cautions.size)
        assertEquals(quotesBefore, quoted.size)
        assertEquals(1, sdk.prepares.size)
    }

    @Test
    fun send_terminalFailure_failsAndIsNeverQueued() = runTest(dispatcher) {
        val service = quotedService()
        sdk.afterCommit = {
            sdk.resolutions[sdk.operations.keys.single()] =
                BeamSendResolution.Terminal(TX_ID, BeamTransactionStatus.Failed)
        }

        val error = assertFailsWith<LocalizedException> { service.send() }

        assertEquals(R.string.beam_send_preview_error, error.errorTextRes)
    }

    @Test
    fun send_terminalFailureFoundAfterAnError_failsAndIsNeverQueued() = runTest(dispatcher) {
        val service = quotedService()
        sdk.afterCommit = {
            sdk.resolutions[sdk.operations.keys.single()] =
                BeamSendResolution.Terminal(TX_ID, BeamTransactionStatus.Canceled)
            throw secretFailure()
        }

        val error = assertFailsWith<LocalizedException> { service.send() }

        assertEquals(R.string.beam_send_preview_error, error.errorTextRes)
    }

    @Test
    fun malformedDepositAddress_isUnsendable() = runTest(dispatcher) {
        val service = SendTransactionServiceBeam(token)
        openSession()

        service.setSendTransactionData(SendTransactionData.Beam("malformed", BigDecimal.ONE, null))

        assertFalse(service.stateFlow.value.sendable)
        assertEquals(1, service.stateFlow.value.cautions.size)
        assertTrue(quoted.isEmpty())
    }

    @Test
    fun sessionOfAnotherAccount_isUnsendable() = runTest(dispatcher) {
        every { adapter.accountId } returns "other"

        val state = quotedService().stateFlow.value

        assertFalse(state.sendable)
        assertEquals(1, state.cautions.size)
    }

    @Test
    fun placeholderOfAnotherChain_isUnsendableWithoutAnError() = runTest(dispatcher) {
        val service = quotedService()

        service.setSendTransactionData(SendTransactionData.Monero("deposit", BigDecimal.ONE))

        val state = service.stateFlow.value
        assertFalse(state.sendable)
        assertFalse(state.loading)
        assertTrue(state.cautions.isEmpty())
    }
}
