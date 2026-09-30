package cash.p.terminal.modules.send.offline

import cash.p.beam.BeamInspectedTransaction
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamRelayConfig
import cash.p.beam.BeamRelayOutcome
import cash.p.beam.BeamRelayResult
import cash.p.beam.BeamTransactionHeightRange
import cash.p.beam.BeamTransactionInspector
import cash.p.beam.BeamTransactionRelay
import cash.p.beam.BeamTransactionRules
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.OfflineNetworkController
import cash.p.terminal.core.storage.OfflineModeStorage
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineBeamMetadata
import cash.p.terminal.entities.OfflineTokenMetadata
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class BeamOfflineTransactionRelayTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = mockk<DispatcherProvider>()
    private val storage = mockk<OfflineModeStorage>(relaxed = true)
    private val accounts = mockk<IAccountManager>()
    private val account = mockk<Account> { every { id } returns "relay-context" }
    private val active = MutableStateFlow<ActiveAccountState>(ActiveAccountState.ActiveAccount(account))
    private val rules = BeamTransactionRules(BeamNetwork.Mainnet, "native-supported-rules")
    private val bytes = byteArrayOf(0, 1, 2, 3)
    private val inspected = BeamInspectedTransaction(
        rules, "cd".repeat(32), "ab".repeat(32),
        BeamTransactionHeightRange(1uL, 100uL), BeamTransactionHeightRange(1uL, 100uL),
        0, 0, 1, 3, bytes.size,
    )
    private lateinit var mode: OfflineModeManager
    private lateinit var service: BeamOfflineTransactionRelay

    @Before
    fun setup() {
        every { dispatchers.io } returns dispatcher
        every { dispatchers.default } returns dispatcher
        every { dispatchers.main } returns dispatcher
        every { storage.getAll() } returns emptyList()
        every { accounts.activeAccount } answers { (active.value as? ActiveAccountState.ActiveAccount)?.account }
        every { accounts.activeAccountStateFlow } returns active
        mode = OfflineModeManager(storage, dispatchers)
        service = BeamOfflineTransactionRelay(dispatchers, mode, accounts)
        mockkObject(BeamTransactionInspector, BeamTransactionRelay)
        every { BeamTransactionInspector.supportedRules(BeamNetwork.Mainnet) } returns rules
        every { BeamTransactionInspector.inspect(any(), rules) } returns inspected
        coEvery { BeamTransactionRelay.relay(any(), any()) } returns
            BeamRelayResult(inspected, BeamRelayOutcome.Accepted)
    }

    @After
    fun teardown() = unmockkAll()

    @Test
    fun prepare_validEnvelope_usesSdkRulesExactBytesAndMainKernel() = runTest(dispatcher) {
        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, envelope())
        val result = service.relay(prepared)

        assertEquals(inspected.mainKernelId, result.transaction.mainKernelId)
        verify { BeamTransactionInspector.inspect(match { it.contentEquals(bytes) }, rules) }
        coVerify { BeamTransactionRelay.relay(match { it.contentEquals(bytes) }, BeamRelayConfig(rules)) }
    }

    @Test
    fun prepare_tamperedIdentityNetworkRulesOrHash_rejectsBeforeRelay() = runTest(dispatcher) {
        val original = envelope()
        val metadata = requireNotNull(original.beamMetadata)
        val variants = listOf(
            original.copy(blockchainUid = "beam-2"),
            original.copy(token = original.token.copy(tokenQueryId = "beam|eip20|asset")),
            original.copy(token = original.token.copy(coinUid = "beam-2")),
            original.copy(token = original.token.copy(decimals = 9)),
            original.copy(beamMetadata = metadata.copy(network = "testnet")),
            original.copy(beamMetadata = metadata.copy(rulesSignature = "foreign-rules")),
            original.copy(txHash = "ef".repeat(32)),
            original.copy(txHash = "ef".repeat(32), beamMetadata = metadata.copy(mainKernelId = "ef".repeat(32))),
        )
        for (invalid in variants) {
            assertTrue(runCatching {
                service.prepare("00010203", BeamNetwork.Mainnet, invalid)
            }.exceptionOrNull() is IllegalArgumentException)
        }
        coVerify(exactly = 0) { BeamTransactionRelay.relay(any(), any()) }
    }

    @Test
    fun prepare_noncanonicalBytes_delegatesRejectionToSdkInspector() = runTest(dispatcher) {
        every { BeamTransactionInspector.inspect(any(), rules) } throws IllegalArgumentException("Noncanonical")
        assertTrue(runCatching {
            service.prepare("00010203", BeamNetwork.Mainnet, null)
        }.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { BeamTransactionRelay.relay(any(), any()) }
    }

    @Test
    fun prepare_oversizedHex_rejectsBeforeNativeAllocation() = runTest(dispatcher) {
        assertTrue(runCatching {
            service.prepare("ab".repeat(BeamTransactionInspector.MAX_TRANSACTION_BYTES + 1), BeamNetwork.Mainnet, null)
        }.exceptionOrNull() is IllegalArgumentException)
        verify(exactly = 0) { BeamTransactionInspector.inspect(any(), any()) }
    }

    @Test
    fun prepare_rawTestnetSelection_rejectsWithoutInspectingOrRelaying() = runTest(dispatcher) {
        assertTrue(runCatching {
            service.prepare("00010203", BeamNetwork.Testnet, null)
        }.exceptionOrNull() is IllegalArgumentException)
        verify(exactly = 0) { BeamTransactionInspector.inspect(any(), any()) }
        coVerify(exactly = 0) { BeamTransactionRelay.relay(any(), any()) }
    }

    @Test
    fun relay_calledFromUiContext_dispatchesSdkWorkToIo() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        every { dispatchers.io } returns io
        coEvery { BeamTransactionRelay.relay(any(), any()) } coAnswers {
            assertSame(io, currentCoroutineContext()[ContinuationInterceptor])
            BeamRelayResult(inspected, BeamRelayOutcome.Accepted)
        }

        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, null)
        service.relay(prepared)
    }

    @Test
    fun relay_manualOfflineBeforeStart_neverCallsSdkEvenWithTemporaryOnlineOverride() = runTest(dispatcher) {
        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, null)
        val key = OfflineKey(account.id, BlockchainType.Beam)
        mode.persistAndPublish(account.id, BlockchainType.Beam, true)
        mode.enterTemporaryOnline(key, 1)

        val error = runCatching { service.relay(prepared) }.exceptionOrNull()

        assertTrue(error is BeamOfflineTransactionRelay.Offline)
        assertFalse((error as BeamOfflineTransactionRelay.Offline).acceptanceUnknown)
        coVerify(exactly = 0) { BeamTransactionRelay.relay(any(), any()) }
    }

    @Test
    fun relay_manualOfflineDuringSend_cancelsAndWaitsForNativeDrain() = runTest(dispatcher) {
        every { dispatchers.applicationScope } returns backgroundScope
        val wallets = mockk<IWalletManager> { every { activeWallets } returns emptyList() }
        val useCase = OfflineModeUseCase(mode, mockk<OfflineNetworkController>(), wallets, dispatchers)
        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, null)
        val cancelled = CompletableDeferred<Unit>()
        val drain = CompletableDeferred<Unit>()
        coEvery { BeamTransactionRelay.relay(any(), any()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
                withContext(NonCancellable) { drain.await() }
            }
        }
        val attempt = async { runCatching { service.relay(prepared) } }
        useCase.setChainOffline(account, BlockchainType.Beam, true)
        cancelled.await()
        assertFalse(attempt.isCompleted)
        drain.complete(Unit)
        val error = attempt.await().exceptionOrNull()
        assertTrue(error is BeamOfflineTransactionRelay.Offline)
        assertTrue((error as BeamOfflineTransactionRelay.Offline).acceptanceUnknown)
    }

    @Test
    fun relay_accountChanges_cancelsOldAttemptWithoutReturningAcceptance() = runTest(dispatcher) {
        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, null)
        coEvery { BeamTransactionRelay.relay(any(), any()) } coAnswers { awaitCancellation() }
        val attempt = async { runCatching { service.relay(prepared) } }
        active.value = ActiveAccountState.ActiveAccount(null)
        advanceUntilIdle()
        assertTrue(attempt.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun relay_noAccountAndOtherAccountOffline_stillRelaysWithoutWallet() = runTest(dispatcher) {
        mode.persistAndPublish(account.id, BlockchainType.Beam, true)
        active.value = ActiveAccountState.ActiveAccount(null)
        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, null)

        assertEquals(BeamRelayOutcome.Accepted, service.relay(prepared).outcome)
    }

    @Test
    fun relay_retryAfterUnknownAcceptance_keepsPrivateCanonicalBytes() = runTest(dispatcher) {
        val prepared = service.prepare("00010203", BeamNetwork.Mainnet, null)
        val attempts = mutableListOf<ByteArray>()
        coEvery { BeamTransactionRelay.relay(any(), any()) } coAnswers {
            val sent = firstArg<ByteArray>()
            attempts += sent.copyOf()
            sent.fill(9)
            BeamRelayResult(inspected, BeamRelayOutcome.UnknownAcceptance)
        }

        repeat(2) { service.relay(prepared) }

        assertEquals(2, attempts.size)
        attempts.forEach { assertArrayEquals(bytes, it) }
    }

    private fun envelope() = DecodedOfflineTransaction(
        blockchainUid = "beam", rawHex = "00010203", txHash = inspected.mainKernelId,
        token = OfflineTokenMetadata("beam|native", "beam", "BEAM", "Beam", 8),
        amountAtomic = "100", fee = null, toAddress = "", createdAt = 123,
        inputOutpoints = emptyList(),
        beamMetadata = OfflineBeamMetadata(1, "mainnet", rules.signature, inspected.mainKernelId),
    )
}
