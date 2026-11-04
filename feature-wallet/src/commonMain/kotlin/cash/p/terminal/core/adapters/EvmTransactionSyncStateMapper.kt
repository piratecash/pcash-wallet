package cash.p.terminal.core.adapters

import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.AdapterState
import io.horizontalsystems.ethereumkit.core.EthereumKit

internal val EvmTransactionRepository.transactionHistoryAdapterState: AdapterState
    get() = pausedState() ?: forwardSyncingState() ?: transactionSyncErrorOrSynced()

// Checked before forward sync: a stopped kit still recomputes ForwardSyncState from the replayed
// stored height (attachLocalState in offline mode), which would report Syncing.
private fun EvmTransactionRepository.pausedState(): AdapterState.Paused? {
    val txSync = transactionsSyncState as? EthereumKit.SyncState.NotSynced ?: return null
    return AdapterState.Paused.takeIf { txSync.error is EthereumKit.SyncError.NotStarted }
}

private fun EvmTransactionRepository.forwardSyncingState(): AdapterState.Syncing? {
    val state = forwardSyncState.value as? EthereumKit.ForwardSyncState.Syncing ?: return null
    return AdapterState.Syncing(progress = 0.0, blocksRemained = state.blocksRemaining)
}

// Background polling cycle (BSC re-runs TransactionSyncManager every ~15s on each new
// block height) is intentionally suppressed: only error states are surfaced. Otherwise
// the UI would flash a parameter-less spinner on every poll.
private fun EvmTransactionRepository.transactionSyncErrorOrSynced(): AdapterState =
    when (val txSync = transactionsSyncState) {
        is EthereumKit.SyncState.NotSynced -> AdapterState.NotSynced(txSync.error)
        else -> AdapterState.Synced
    }
