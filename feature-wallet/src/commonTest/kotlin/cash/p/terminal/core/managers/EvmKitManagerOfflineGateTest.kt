package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coverage for the offline-mode network gate in EvmKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvmKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Evm",
        type = AccountType.EvmAddress("0x0000000000000000000000000000000000dEaD"),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val blockchainType = BlockchainType.Ethereum

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockEvmKit = mockk<EthereumKit>(relaxed = true)
    private val backgroundState = MutableStateFlow(BackgroundManagerState.Unknown)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: EvmKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = EvmKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private fun createManager(): EvmKitManager {
        val syncSourceManager = mockk<EvmSyncSourceManager>(relaxed = true) {
            every { syncSourceObservable } returns PublishSubject.create()
        }
        val manager = EvmKitManager(
            chain = Chain.Ethereum,
            backgroundManager = mockk<IBackgroundManager>(relaxed = true) {
                every { stateFlow } returns backgroundState
            },
            syncSourceManager = syncSourceManager,
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            databaseKeys = mockk(),
            platformContexts = mockk(),
        )
        setField(
            manager, "evmKitWrapper", EvmKitWrapper(
                evmKit = mockEvmKit,
                nftKit = null,
                blockchainType = blockchainType,
                signer = null,
                merkleTransactionAdapter = null,
                databaseKey = ByteArray(32),
                hardwareSigners = mockk(),
            )
        )
        setField(manager, "currentAccount", account)
        createdManager = manager
        return manager
    }

    private fun setField(manager: EvmKitManager, name: String, value: Any?) {
        EvmKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun pollingSessionCount(manager: EvmKitManager): Int =
        (EvmKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun subscribeToEvents_backgroundDuringRestartDelay_doesNotRestartKit() = runBlocking {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, blockchainType)) } returns false
        val manager = createManager()
        EvmKitManager::class.java.getDeclaredMethod("subscribeToEvents").apply { isAccessible = true }.invoke(manager)

        backgroundState.value = BackgroundManagerState.EnterForeground
        delay(BEFORE_RESTART_MS)
        backgroundState.value = BackgroundManagerState.EnterBackground
        delay(PAST_RESTART_MS)

        verify(exactly = 1) { mockEvmKit.stop() }
        verify(exactly = 0) { mockEvmKit.start() }
    }

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, blockchainType)) } returns true
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        verify(exactly = 0) { mockEvmKit.start() }
        verify(exactly = 0) { mockEvmKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_startsAndRefreshesKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, blockchainType)) } returns false
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        verify(exactly = 1) { mockEvmKit.start() }
        verify(exactly = 1) { mockEvmKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    private companion object {
        // The manager's scope runs on Dispatchers.Default, so its 1 s restart delay is real time.
        const val BEFORE_RESTART_MS = 200L
        const val PAST_RESTART_MS = 1_500L
    }
}
