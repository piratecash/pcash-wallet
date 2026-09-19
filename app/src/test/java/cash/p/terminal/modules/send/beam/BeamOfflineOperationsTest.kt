package cash.p.terminal.modules.send.beam

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamOfflineSendState
import cash.p.beam.BeamOfflineSignResult
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendAmount
import cash.p.beam.BeamSendContext
import cash.p.beam.BeamSendQuote
import cash.p.beam.BeamSendDeliveryMode
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BeamSessionFactory
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Wallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class BeamOfflineOperationsTest {
    private val account = mockk<Account> { every { id } returns "account" }
    private val wallet = mockk<Wallet> { every { this@mockk.account } returns this@BeamOfflineOperationsTest.account }
    private val sdk = mockk<BeamWalletSession>(relaxUnitFun = true)
    private val factory = mockk<BeamSessionFactory>(relaxUnitFun = true)
    private val locallyCreated = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val owner = BeamSessionOwner(factory)
    private val encoder = mockk<OfflineTransactionPayloadEncoder>()
    private val operation = BeamSendOperation(
        "operation", "01".repeat(16), "02".repeat(32), 100, 10, BeamSendResolution.Prepared("01".repeat(16)),
        deliveryMode = BeamSendDeliveryMode.Offline, offlineState = BeamOfflineSendState.Exported,
        contextId = "context", rules = "rules", mainKernelId = "03".repeat(32), observedProofHeight = 0,
    )

    @Before
    fun setUp() {
        coEvery { factory.open(account, any()) } returns sdk
        coEvery { sdk.receiveAddress(any()) } returns
            BeamAddress("fixture", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
        every { sdk.state } returns MutableStateFlow(BeamWalletState.Stopped)
        every { sdk.transactions } returns MutableStateFlow(emptyList())
        coEvery { sdk.sendOperations() } returns listOf(operation)
    }

    @Test
    fun export_stoppedAndReopened_usesSameBytesAndSeparateIdentities() = runTest(UnconfinedTestDispatcher()) {
        coEvery { sdk.exportSignedTransaction(operation.operationId) } returns
            byteArrayOf(0, 0x80.toByte(), 0xff.toByte())
        val draft = slot<OfflineSignedTransactionDraft>()
        every { encoder.encode(capture(draft)) } returns "payload"
        val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), this)
        val source = BeamOfflineOperations(owner, encoder, dispatchers)
        owner.acquire(account)
        assertEquals(listOf(operation), source.observe(account.id).first().operations)
        val first = source.export(wallet, operation.operationId)
        owner.close()
        owner.acquire(account)
        val second = source.export(wallet, operation.operationId)
        assertEquals("0080ff", first.rawHex)
        assertEquals(first.rawHex, second.rawHex)
        assertEquals(operation.mainKernelId, draft.captured.txHash)
        assertEquals(operation.transactionId, draft.captured.beamMetadata?.coreTxId)
        assertEquals("mainnet", draft.captured.beamMetadata?.network)
        assertEquals("", draft.captured.toAddress)
        coVerify(exactly = 0) { sdk.commitSend(any()) }
        coVerify(exactly = 0) { sdk.recoverSendOperations() }
    }

    @Test
    fun sign_stoppedOwner_routesOnlyToOfflineSdk() = runTest {
        val request = BeamQuoteRequest("fixture", BeamSendAmount.Max, BeamSendContext.Offline("context"))
        val quote = BeamSendQuote(100, 10, 110, 10, 0, 0, 1, 0, BeamAddressType.Offline, "version", "context", "rules")
        coEvery { sdk.quoteSend(request) } returns quote
        coEvery { sdk.signOffline(any(), request, "version") } returns
            BeamOfflineSignResult("tx", BeamOfflineSendState.Signed)
        val session = owner.acquire(account)
        val coordinator = BeamSendCoordinator(owner, locallyCreated)
        val proposal = coordinator.quote(session, request)
        coordinator.signOffline(proposal)
        coVerify(exactly = 1) { sdk.signOffline(proposal.operationId, request, "version") }
        coVerify(exactly = 0) { sdk.prepareSend(any(), any(), any()) }
        coVerify(exactly = 0) { sdk.commitSend(any()) }
        coVerify(exactly = 0) { sdk.exportSignedTransaction(any()) }
        // Broadcast happens elsewhere, but the transaction is still this wallet's own.
        coVerify(exactly = 1) { locallyCreated.markCreated("account", BlockchainType.Beam.uid, "tx", any()) }
    }

    @Test
    fun reconcile_onlyOfflineInventory_neverRecoversOrCommits() = runTest {
        every { sdk.state } returns MutableStateFlow(BeamWalletState.Ready(10))
        val session = owner.acquire(account)
        val coordinator = BeamSendCoordinator(owner, locallyCreated)
        assertEquals(listOf(operation), coordinator.reconcile(session, retryPending = true))
        coVerify(exactly = 0) { sdk.recoverSendOperations() }
        coVerify(exactly = 0) { sdk.commitSend(any()) }
    }

    @Test
    fun inventory_onlineAndUnsignedRecords_areExcludedFromOwnSignedList() {
        val online = operation.copy(deliveryMode = BeamSendDeliveryMode.Online)
        val signing = operation.copy(offlineState = BeamOfflineSendState.Signing)
        assertEquals(listOf(operation), BeamOfflineOperations.ownSigned(listOf(online, signing, operation)))
    }

    @Test
    fun inventory_onlyExportedRecords_areInOwnExportedList() {
        val signed = operation.copy(offlineState = BeamOfflineSendState.Signed)
        assertEquals(listOf(operation), BeamOfflineOperations.ownExported(listOf(signed, operation)))
    }

    @Test
    fun observe_sessionOpen_emitsSendOperationsWithCreationTime() = runTest {
        val created = operation.copy(createdAtEpochSeconds = 1_234_567)
        coEvery { sdk.sendOperations() } returns listOf(created)
        val source = BeamOfflineOperations(owner, encoder, TestDispatcherProvider(UnconfinedTestDispatcher(), this))
        owner.acquire(account)

        val inventory = source.observe(account.id).first()

        assertEquals(BeamOfflineOperations.Inventory(listOf(created)), inventory)
    }

    @Test
    fun observe_sendOperationsThrows_returnsEmptyInventory() = runTest {
        coEvery { sdk.sendOperations() } throws IllegalStateException("unavailable")
        val source = BeamOfflineOperations(owner, encoder, TestDispatcherProvider(UnconfinedTestDispatcher(), this))
        owner.acquire(account)

        val inventory = source.observe(account.id).first()

        assertEquals(BeamOfflineOperations.Inventory(emptyList()), inventory)
    }
}
