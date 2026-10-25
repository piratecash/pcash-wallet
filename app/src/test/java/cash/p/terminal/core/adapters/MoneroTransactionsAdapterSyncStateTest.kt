package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.MoneroKitWrapper
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneroTransactionsAdapterSyncStateTest {

    private val syncState = MutableStateFlow<AdapterState>(AdapterState.Syncing(progress = 10.0))
    private val effectiveOffline = MutableStateFlow<Set<OfflineKey>>(emptySet())
    private val key = OfflineKey(ACCOUNT_ID, BlockchainType.Monero)
    private var networkPaused = false

    private val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true) {
        every { syncState } returns this@MoneroTransactionsAdapterSyncStateTest.syncState
        every { transactionsStateUpdatedFlow } returns MutableSharedFlow()
    }
    private val offlineModeManager = mockk<OfflineModeManager> {
        every { isNetworkPaused(key) } answers { networkPaused }
        every { effectiveFlow } returns effectiveOffline
    }
    private val adapter = MoneroTransactionsAdapter(
        moneroKitWrapper = moneroKitWrapper,
        source = TransactionSource(
            blockchain = mockk<Blockchain> { every { type } returns BlockchainType.Monero },
            account = mockk<Account> { every { id } returns ACCOUNT_ID },
            meta = null,
        ),
        offlineModeManager = offlineModeManager,
    )

    @Test
    fun transactionsState_online_returnsWalletSyncState() {
        val state = adapter.transactionsState

        assertEquals(AdapterState.Syncing(progress = 10.0), state)
    }

    @Test
    fun transactionsState_networkPaused_returnsSynced() {
        networkPaused = true

        assertEquals(AdapterState.Synced, adapter.transactionsState)
    }

    @Test
    fun transactionsStateUpdatedFlowable_offlineModeToggled_emits() {
        val subscriber = adapter.transactionsStateUpdatedFlowable.test()
        val emissionsBeforeToggle = subscriber.valueCount()

        effectiveOffline.value = setOf(key)

        assertEquals(emissionsBeforeToggle + 1, subscriber.valueCount())
        subscriber.cancel()
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
