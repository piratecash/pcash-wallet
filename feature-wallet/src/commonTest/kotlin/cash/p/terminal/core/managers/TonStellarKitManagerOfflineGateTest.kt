package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.tonkit.core.TonKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Coverage for the offline-mode network gate in TonKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 *
 * TonKit's stop()/refresh() are suspend functions, unlike Solana/Tron, so this uses
 * coEvery/coVerify instead of the plain every/verify used by the non-suspend kits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TonKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Ton",
        type = AccountType.Mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
            "",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val offlineKey = OfflineKey(account.id, BlockchainType.Ton)

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockTonKit = mockk<TonKit>(relaxed = true)
    private val w5TonKit = mockk<TonKit>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: TonKitManager? = null

    // Unknown never reaches the foreground/background branches, so a kit's lifecycle job stays idle.
    private val backgroundStateFlow =
        MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)

    @Before
    fun setUp() {
        mockkObject(TonKit.Companion)
        coEvery { TonKit.migrateDatabase(any(), any(), any(), any()) } returns DatabaseMigrationResult(0, 1)
        every { TonKit.createHttpClient(any(), any()) } returns mockk()
        coEvery { TonKit.getInstance(any(), any(), any(), account.id, any(), any()) } returns mockTonKit
        coEvery { TonKit.getInstance(any(), any(), any(), "${account.id}-w5", any(), any()) } returns w5TonKit
    }

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = TonKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
        unmockkAll()
    }

    private fun createManager() = TonKitManager(
        backgroundManager = mockk<IBackgroundManager>(relaxed = true) {
            every { stateFlow } returns backgroundStateFlow
        },
        hardwarePublicKeyStorage = mockk(relaxed = true),
        platformContexts = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = offlineModeManager,
        tonKitDatabaseKeyProvider = mockk(relaxed = true) {
            coEvery { awaitKey(any()) } returns ByteArray(32)
        },
        deviceSigners = mockk(relaxed = true),
    ).also { createdManager = it }

    // Opens the kits while the pair is offline and waits for their lifecycle jobs to go idle, so later
    // kit calls come only from the operation under test.
    private suspend fun createManagerWithKits(vararg versions: TonWalletVersion): TonKitManager {
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns true
        val manager = createManager()
        versions.forEach { manager.getTonKitWrapper(account, it, BlockchainType.Ton) }
        verify(timeout = 5_000, atLeast = versions.size) { offlineModeManager.isNetworkPaused(offlineKey) }
        return manager
    }

    private fun pollingSessionCount(manager: TonKitManager): Int =
        (TonKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockTonKit.startListener() }
        coVerify(exactly = 0) { mockTonKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_startsAndRefreshesKit() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns false

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockTonKit.startListener() }
        coVerify(exactly = 1) { mockTonKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_bothVersions_startsBothKits() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2, TonWalletVersion.W5)
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns false

        manager.startForPolling()

        coVerify(exactly = 1) { mockTonKit.startListener() }
        coVerify(exactly = 1) { w5TonKit.startListener() }
        assertTrue(manager.isNetworkStarted(account))
    }

    @Test
    fun pauseNetwork_bothVersionsStarted_stopsBothKits() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2, TonWalletVersion.W5)
        manager.resumeNetwork(account)

        manager.pauseNetwork(account)

        coVerify(exactly = 1) { mockTonKit.stop() }
        coVerify(exactly = 1) { w5TonKit.stop() }
        assertFalse(manager.isNetworkStarted(account))
    }

    @Test
    fun resumeNetwork_bothVersionsPaused_startsAndRefreshesBothKits() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2, TonWalletVersion.W5)

        manager.resumeNetwork(account)

        coVerify(exactly = 1) { mockTonKit.startListener() }
        coVerify(exactly = 1) { mockTonKit.refresh() }
        coVerify(exactly = 1) { w5TonKit.startListener() }
        coVerify(exactly = 1) { w5TonKit.refresh() }
    }

    @Test
    fun refresh_onlineKit_refreshesKit() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns false

        manager.refresh()

        coVerify(exactly = 1) { mockTonKit.refresh() }
    }

    @Test
    fun refresh_bothVersionsOnline_refreshesBothKits() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2, TonWalletVersion.W5)
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns false

        manager.refresh()

        coVerify(exactly = 1) { mockTonKit.refresh() }
        coVerify(exactly = 1) { w5TonKit.refresh() }
    }

    @Test
    fun refresh_networkPaused_skipsKit() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)

        manager.refresh()

        coVerify(exactly = 0) { mockTonKit.refresh() }
    }

    @Test
    fun refresh_noKit_doesNothing() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns false
        val manager = createManager()

        manager.refresh()

        coVerify(exactly = 0) { mockTonKit.refresh() }
    }

    @Test
    fun resumeNetwork_pauseCallerCancelledDuringStop_startsListenerOnlyAfterStopCompletes() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)
        manager.startedKitsFlow.value.getValue(TonWalletVersion.V4R2).networkStarted = true
        val stopGate = CompletableDeferred<Unit>()
        coEvery { mockTonKit.stop() } coAnswers { stopGate.await() }

        val pause = launch { manager.pauseNetwork(account) }
        launch { manager.resumeNetwork(account) }
        pause.cancel()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockTonKit.startListener() }
        stopGate.complete(Unit)
        advanceUntilIdle()

        coVerifyOrder {
            mockTonKit.stop()
            mockTonKit.startListener()
        }
    }

    @Test
    fun getTonKitWrapper_accountSwitch_backgroundStopsOnlyNewAccountKit() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)
        val nextAccount = account.copy(id = "next-account-id")
        val nextKit = mockk<TonKit>(relaxed = true)
        coEvery { TonKit.getInstance(any(), any(), any(), nextAccount.id, any(), any()) } returns nextKit
        every { offlineModeManager.isNetworkPaused(OfflineKey(nextAccount.id, BlockchainType.Ton)) } returns true
        manager.getTonKitWrapper(nextAccount, TonWalletVersion.V4R2, BlockchainType.Ton)
        verify(timeout = 5_000) { offlineModeManager.isNetworkPaused(OfflineKey(nextAccount.id, BlockchainType.Ton)) }

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground

        coVerify(timeout = 5_000, exactly = 1) { nextKit.stop() }
        coVerify(exactly = 1) { mockTonKit.stop() }
    }

    @Test
    fun getTonKitWrapper_switchCancelledDuringTeardown_doesNotCreateAbandonedAccountKit() = testScope.runTest {
        val manager = createManagerWithKits(TonWalletVersion.V4R2)
        val stopGate = CompletableDeferred<Unit>()
        coEvery { mockTonKit.stop() } coAnswers { stopGate.await() }
        val nextAccount = account.copy(id = "next-account-id")

        val caller = launch { manager.getTonKitWrapper(nextAccount, TonWalletVersion.V4R2, BlockchainType.Ton) }
        caller.cancel()
        advanceUntilIdle()

        assertTrue(manager.startedKitsFlow.value.isEmpty())
        stopGate.complete(Unit)
        caller.join()

        coVerify(exactly = 1) { mockTonKit.stop() }
        coVerify(exactly = 0) { TonKit.getInstance(any(), any(), any(), nextAccount.id, any(), any()) }
    }
}

/**
 * Coverage for the offline-mode network gate in StellarKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 *
 * StellarKit's start()/refresh() are suspend functions, unlike Solana/Tron, so this uses
 * coEvery/coVerify instead of the plain every/verify used by the non-suspend kits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StellarKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Stellar",
        type = AccountType.StellarAddress("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockStellarKit = mockk<StellarKit>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: StellarKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = StellarKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private val backgroundStateFlow =
        MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.EnterForeground)

    private fun createManager(): StellarKitManager {
        val manager = StellarKitManager(
            backgroundManager = mockk<IBackgroundManager>(relaxed = true) {
                every { stateFlow } returns backgroundStateFlow
            },
            hardwarePublicKeyStorage = mockk(relaxed = true),
            hardwareSigners = mockk(relaxed = true),
            deviceSigners = DeviceSignerFactory(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
            platformContexts = mockk(relaxed = true),
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            stellarKitDatabaseKeyProvider = mockk(relaxed = true),
        )
        setField(manager, "stellarKitWrapper", StellarKitWrapper(mockStellarKit))
        setField(manager, "currentAccount", account)
        createdManager = manager
        return manager
    }

    private fun setField(manager: StellarKitManager, name: String, value: Any?) {
        StellarKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun pollingSessionCount(manager: StellarKitManager): Int =
        (StellarKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Stellar)) } returns true
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockStellarKit.start() }
        coVerify(exactly = 0) { mockStellarKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_startsAndRefreshesKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Stellar)) } returns false
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockStellarKit.start() }
        coVerify(exactly = 1) { mockStellarKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun start_wrapperReplacedByAccountSwitch_keepsDrivingItsOwnKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Stellar)) } returns false
        val manager = createManager()
        val startJob = launch { invokeStart(manager, account, StellarKitWrapper(mockStellarKit)) }
        advanceUntilIdle()

        val nextKit = mockk<StellarKit>(relaxed = true)
        setField(manager, "stellarKitWrapper", StellarKitWrapper(nextKit))
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        verify(exactly = 1) { mockStellarKit.stop() }
        verify(exactly = 0) { nextKit.stop() }
        startJob.cancel()
    }

    @Test
    fun stop_callerCancelledDuringTeardown_destroysKitAndDoesNotResume() = testScope.runTest {
        val manager = createManager()
        // The lifecycle job hangs in NonCancellable cleanup, so stop() is parked inside cancelAndJoin
        // when the caller is cancelled — the interleaving the teardown has to survive.
        val cleanupGate = CompletableDeferred<Unit>()
        setField(manager, "job", backgroundScope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanupGate.await() }
            }
        })

        var resumedAfterStop = false
        val caller = launch {
            invokeStop(manager)
            resumedAfterStop = true
        }
        caller.cancel()
        advanceUntilIdle()

        assertNull(manager.stellarKitWrapper)
        cleanupGate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockStellarKit.destroy() }
        assertFalse(resumedAfterStop)
    }

    private suspend fun invokeStart(
        manager: StellarKitManager,
        account: Account,
        wrapper: StellarKitWrapper,
    ) {
        StellarKitManager::class.declaredMemberFunctions.first { it.name == "start" }
            .apply { isAccessible = true }
            .callSuspend(manager, account, wrapper)
    }

    private suspend fun invokeStop(manager: StellarKitManager) {
        StellarKitManager::class.declaredMemberFunctions.first { it.name == "stop" }
            .apply { isAccessible = true }
            .callSuspend(manager)
    }
}
