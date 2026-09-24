package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.DatabaseKeyMismatchException
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.net.URL
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class ThorchainKitManagerTest {

    private val context = mockk<Context>()
    private val kitDatabaseKeys = mockk<KitDatabaseKeys>()
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val databaseKey = ByteArray(KEY_SIZE) { 1 }
    private val kit = mockk<ThorchainKit>(relaxed = true)
    private val account = mnemonicAccount(ACCOUNT_ID)
    private val backgroundStateFlow =
        MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.EnterForeground)
    private val testScope = TestScope(UnconfinedTestDispatcher())

    // Public BIP39 test vector, not a real user mnemonic.
    private val bip39TestVector = AccountType.Mnemonic(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
        "",
    )
    private val thorAddress = ThorchainKit.getAddress(bip39TestVector.seed, Network.Mainnet).toString()
    private val mayaAddress = ThorchainKit.getAddress(bip39TestVector.seed, Network.MayaMainnet).toString()
    private var createdManager: ThorchainKitManager? = null

    @Before
    fun setUp() {
        mockkObject(ThorchainKit.Companion)
        every { ThorchainKit.clear(any(), any(), any()) } returns Unit
        coEvery { kitDatabaseKeys.awaitKey(any()) } returns databaseKey
        every { kit.network } returns Network.MayaMainnet
    }

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            managerScope(manager).cancel()
        }
        unmockkAll()
    }

    @Test
    fun createKit_databaseKeyMismatch_clearsCacheAndRetriesOnce() = runTest {
        every { getInstance() } throws mismatch() andThen kit

        val created = createManager().createKit(account)

        assertSame(kit, created)
        verifyOrder {
            getInstance()
            ThorchainKit.clear(context, Network.MayaMainnet, ACCOUNT_ID)
            getInstance()
        }
    }

    @Test
    fun createKit_databaseKeyMismatchTwice_propagatesAfterOneRetry() = runTest {
        every { getInstance() } throws mismatch()

        assertFailsWith<DatabaseKeyMismatchException> { createManager().createKit(account) }

        verify(exactly = 2) { getInstance() }
        verify(exactly = 1) { ThorchainKit.clear(context, Network.MayaMainnet, ACCOUNT_ID) }
    }

    @Test
    fun createKit_kitCreated_zeroesDatabaseKey() = runTest {
        every { getInstance() } returns kit

        createManager().createKit(account)

        assertTrue(databaseKey.all { it == 0.toByte() })
    }

    @Test
    fun createKit_kitCreationFails_zeroesDatabaseKey() = runTest {
        every { getInstance() } throws IllegalStateException("unavailable")

        assertFailsWith<IllegalStateException> { createManager().createKit(account) }

        assertTrue(databaseKey.all { it == 0.toByte() })
    }

    @Test
    fun createKit_nonMnemonicAccount_throwsUnsupportedAccountBeforeTouchingKey() = runTest {
        val watchAccount = account(AccountType.EvmAddress("0x0000000000000000000000000000000000000000"))

        assertFailsWith<UnsupportedAccountException> { createManager().createKit(watchAccount) }

        coVerify(exactly = 0) { kitDatabaseKeys.awaitKey(any()) }
        verify(exactly = 0) { getInstance() }
    }

    @Test
    fun createKit_thorchainWatchAccountOnThorchainManager_createsKitFromWatchedAddress() = runTest {
        every { getInstance() } returns kit
        val manager = createManager(Network.Mainnet, BlockchainType.Thorchain)

        manager.createKit(account(AccountType.ThorchainAddress(thorAddress)))

        verify(exactly = 1) {
            ThorchainKit.getInstance(
                context, Address.fromString(thorAddress, Network.Mainnet), Network.Mainnet, ACCOUNT_ID,
                any(), any(), any(), any(), any(),
            )
        }
        verify(exactly = 0) { getSeedInstance() }
    }

    @Test
    fun createKit_mayachainWatchAccountOnThorchainManager_throwsUnsupportedBeforeTouchingKey() = runTest {
        val manager = createManager(Network.Mainnet, BlockchainType.Thorchain)

        assertFailsWith<UnsupportedAccountException> {
            manager.createKit(account(AccountType.MayachainAddress(mayaAddress)))
        }

        coVerify(exactly = 0) { kitDatabaseKeys.awaitKey(any()) }
        verify(exactly = 0) { getInstance() }
    }

    @Test
    fun createKit_thorchainWatchAccountOnMayachainManager_throwsUnsupportedBeforeTouchingKey() = runTest {
        assertFailsWith<UnsupportedAccountException> {
            createManager().createKit(account(AccountType.ThorchainAddress(thorAddress)))
        }

        coVerify(exactly = 0) { kitDatabaseKeys.awaitKey(any()) }
        verify(exactly = 0) { getInstance() }
    }

    @Test
    fun getAddress_watchAccount_returnsWatchedAddress() {
        val address = createManager().getAddress(account(AccountType.MayachainAddress(mayaAddress)))

        assertEquals(mayaAddress, address)
    }

    @Test
    fun createKit_mnemonicAccount_createsKitFromSeedDerivedAddress() = runTest {
        every { getInstance() } returns kit

        createManager().createKit(account(bip39TestVector))

        verify(exactly = 1) {
            ThorchainKit.getInstance(
                context, Address.fromString(mayaAddress, Network.MayaMainnet), Network.MayaMainnet, ACCOUNT_ID,
                any(), any(), any(), any(), any(),
            )
        }
    }

    @Test
    fun getThorchainKitWrapper_accountSwitch_stopsPreviousKit() = testScope.runTest {
        val nextKit = mockk<ThorchainKit>(relaxed = true)
        every { getInstance() } returns kit andThen nextKit
        val manager = createManager()
        val nextAccount = mnemonicAccount("next-account-id")

        manager.getThorchainKitWrapper(account)
        val wrapper = manager.getThorchainKitWrapper(nextAccount)

        verify(exactly = 1) { kit.stop() }
        verify(exactly = 0) { nextKit.stop() }
        assertSame(nextKit, wrapper.thorchainKit)
        assertEquals(nextAccount, manager.currentAccount)
    }

    @Test
    fun unlink_kitUsedTwice_stopsKitOnlyAfterLastUnlink() = testScope.runTest {
        every { getInstance() } returns kit
        val manager = createManager()
        manager.getThorchainKitWrapper(account)
        manager.getThorchainKitWrapper(account)

        manager.unlink(account)

        verify(exactly = 0) { kit.stop() }
        assertNotNull(manager.thorchainKitWrapper)

        manager.unlink(account)

        verify(exactly = 1) { kit.stop() }
        assertNull(manager.thorchainKitWrapper)
    }

    @Test
    fun start_backgroundDuringPollingSession_keepsKitUntilSessionStops() = testScope.runTest {
        val manager = createManagerWithKit()
        val wrapper = manager.thorchainKitWrapper
        manager.startForPolling()
        val startJob = launch { invokeStart(manager, account, requireNotNull(wrapper)) }

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        verify(exactly = 0) { kit.stop() }

        manager.stopForPolling()

        verify(exactly = 1) { kit.stop() }
        startJob.cancel()
    }

    @Test
    fun startForPolling_offlinePair_skipsNetworkCalls() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Mayachain)) } returns true
        val manager = createManagerWithKit()

        manager.startForPolling()

        verify(exactly = 0) { kit.start() }
        verify(exactly = 0) { kit.refresh() }
    }

    @Test
    fun startForPolling_onlinePair_startsAndRefreshesKit() = testScope.runTest {
        val manager = createManagerWithKit()

        manager.startForPolling()

        verify(exactly = 1) { kit.start() }
        verify(exactly = 1) { kit.refresh() }
    }

    @Test
    fun start_offlinePair_doesNotStartKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Mayachain)) } returns true
        val manager = createManagerWithKit()

        val startJob = launch { invokeStart(manager, account, requireNotNull(manager.thorchainKitWrapper)) }
        advanceUntilIdle()

        verify(exactly = 0) { kit.start() }
        startJob.cancel()
    }

    @Test
    fun start_onlinePair_startsKit() = testScope.runTest {
        val manager = createManagerWithKit()

        val startJob = launch { invokeStart(manager, account, requireNotNull(manager.thorchainKitWrapper)) }
        advanceUntilIdle()

        verify(atLeast = 1) { kit.start() }
        assertTrue(requireNotNull(manager.thorchainKitWrapper).networkStarted)
        startJob.cancel()
    }

    @Test
    fun pauseNetwork_startedKit_stopsAndResumeRestartsIt() = testScope.runTest {
        val manager = createManagerWithKit()
        val wrapper = requireNotNull(manager.thorchainKitWrapper)
        wrapper.networkStarted = true

        manager.pauseNetwork(account)

        verify(exactly = 1) { kit.stop() }
        assertFalse(wrapper.networkStarted)

        manager.resumeNetwork(account)

        verify(exactly = 1) { kit.start() }
        assertTrue(wrapper.networkStarted)
    }

    @Test
    fun resumeNetwork_feeReported_publishesItOnWrapper() = testScope.runTest {
        coEvery { kit.estimateFee() } returns BigInteger.valueOf(3_000_000_000)
        val manager = createManagerWithKit()
        val wrapper = requireNotNull(manager.thorchainKitWrapper)
        assertEquals(BigInteger.valueOf(2_000_000_000), wrapper.nativeFee.value)

        manager.resumeNetwork(account)

        realTime { wrapper.nativeFee.first { it == BigInteger.valueOf(3_000_000_000) } }
    }

    @Test
    fun pauseNetwork_feeRequestInFlight_cancelsIt() = testScope.runTest {
        val feeRequest = suspendingFeeRequest()
        val manager = createManagerWithKit()
        manager.resumeNetwork(account)
        realTime { feeRequest.started.await() }

        manager.pauseNetwork(account)

        realTime { feeRequest.cancelled.await() }
        verify(exactly = 1) { kit.stop() }
    }

    @Test
    fun start_enterBackgroundWithFeeRequestInFlight_cancelsIt() = testScope.runTest {
        val feeRequest = suspendingFeeRequest()
        val manager = createManagerWithKit()
        val startJob = launch { invokeStart(manager, account, requireNotNull(manager.thorchainKitWrapper)) }
        realTime { feeRequest.started.await() }

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        realTime { feeRequest.cancelled.await() }
        startJob.cancel()
    }

    @Test
    fun start_offlinePairThenForeground_neverEstimatesFee() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Mayachain)) } returns true
        val manager = createManagerWithKit()

        val startJob = launch { invokeStart(manager, account, requireNotNull(manager.thorchainKitWrapper)) }
        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 0) { kit.estimateFee() }
        startJob.cancel()
    }

    @Test
    fun start_pauseDuringForegroundStartDecision_kitEndsStopped() = testScope.runTest {
        val offlineKey = OfflineKey(ACCOUNT_ID, BlockchainType.Mayachain)
        var paused = false
        every { offlineModeManager.isNetworkPaused(offlineKey) } answers { paused }
        val manager = createManagerWithKit()
        val wrapper = requireNotNull(manager.thorchainKitWrapper)
        val startJob = launch { invokeStart(manager, account, wrapper) }
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        // The pause lands right after the collector read "online", as the toggle would from another thread.
        var pauseJob: Job? = null
        every { offlineModeManager.isNetworkPaused(offlineKey) } answers {
            if (pauseJob == null) {
                paused = true
                pauseJob = raceBriefly { manager.pauseNetwork(account) }
                false
            } else {
                paused
            }
        }
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()
        realTime { requireNotNull(pauseJob).join() }

        assertFalse(wrapper.networkStarted)
        startJob.cancel()
    }

    @Test
    fun stopForPolling_foregroundDuringCleanupStop_kitEndsStarted() = testScope.runTest {
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        val manager = createManagerWithKit()
        val wrapper = requireNotNull(manager.thorchainKitWrapper)
        // Foreground arrives after the cleanup decided "background" but before its stop took effect.
        val startedInForeground = CompletableDeferred<Unit>()
        var cleanupStopping = false
        every { kit.start() } answers {
            if (cleanupStopping) startedInForeground.complete(Unit)
        }
        every { kit.stop() } answers {
            if (!cleanupStopping) {
                cleanupStopping = true
                backgroundStateFlow.value = BackgroundManagerState.EnterForeground
                // The cleanup already dropped feeJob, so a new one means a foreground start fully landed.
                raceBriefly { while (wrapper.feeJob == null) delay(POLL_STEP_MS) }
            }
        }
        manager.startForPolling()
        managerScope(manager).launch { invokeStart(manager, account, wrapper) }
        realTime { backgroundStateFlow.subscriptionCount.first { it > 0 } }

        manager.stopForPolling()
        realTime { startedInForeground.await() }
        realTime { lifecycleMutex(manager).withLock { } }

        assertTrue(wrapper.networkStarted)
    }

    private class FeeRequest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
    }

    private fun suspendingFeeRequest() = FeeRequest().also { request ->
        coEvery { kit.estimateFee() } coAnswers {
            request.started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                request.cancelled.complete(Unit)
            }
        }
    }

    // Runs [block] on another thread and gives it a short window before the caller carries on.
    private fun raceBriefly(block: suspend () -> Unit): Job {
        val job = CoroutineScope(Dispatchers.Default).launch { block() }
        runBlocking { withTimeoutOrNull(RACE_WINDOW_MS) { job.join() } }
        return job
    }

    private fun managerScope(manager: ThorchainKitManager) = field(manager, "scope") as CoroutineScope

    private fun lifecycleMutex(manager: ThorchainKitManager) = field(manager, "lifecycleMutex") as Mutex

    // The manager runs its jobs on Dispatchers.Default, outside the test scheduler's virtual time.
    private suspend fun <T> realTime(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(REAL_TIMEOUT_MS) { block() } }

    private fun MockKMatcherScope.getInstance() = ThorchainKit.getInstance(
        any<Context>(), any<Address>(), any(), any(), any(), any(), any(), any(), any(),
    )

    private fun MockKMatcherScope.getSeedInstance() = ThorchainKit.getInstance(
        any<Context>(), any<ByteArray>(), any(), any(), any(), any(), any(), any(), any(),
    )

    private fun mismatch() = DatabaseKeyMismatchException("maya.db", IllegalStateException())

    private fun createManager(
        network: Network = Network.MayaMainnet,
        blockchainType: BlockchainType = BlockchainType.Mayachain,
    ) = ThorchainKitManager(
        network,
        blockchainType,
        listOf(URL("https://node.example/")),
        context,
        kitDatabaseKeys,
        mockk<BackgroundManager>(relaxed = true) {
            every { stateFlow } returns backgroundStateFlow
        },
        mockk(relaxed = true),
        mockk(relaxed = true),
        offlineModeManager,
    ).also { createdManager = it }

    private fun createManagerWithKit() = createManager().also { manager ->
        setField(manager, "thorchainKitWrapper", ThorchainKitWrapper(kit))
        setField(manager, "currentAccount", account)
    }

    private fun field(manager: ThorchainKitManager, name: String): Any? =
        ThorchainKitManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager)

    private fun setField(manager: ThorchainKitManager, name: String, value: Any?) {
        ThorchainKitManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }

    private suspend fun invokeStart(manager: ThorchainKitManager, account: Account, wrapper: ThorchainKitWrapper) {
        ThorchainKitManager::class.declaredMemberFunctions.first { it.name == "start" }
            .apply { isAccessible = true }
            .callSuspend(manager, account, wrapper)
    }

    private fun mnemonicAccount(id: String) = account(
        mockk<AccountType.Mnemonic> { every { seed } returns ByteArray(SEED_SIZE) },
        id,
    )

    private fun account(type: AccountType, id: String = ACCOUNT_ID) = Account(
        id = id,
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val KEY_SIZE = 32
        const val SEED_SIZE = 64
        const val REAL_TIMEOUT_MS = 5_000L
        const val RACE_WINDOW_MS = 200L
        const val POLL_STEP_MS = 5L
    }
}
