package cash.p.terminal.core.tor

import kotlinx.coroutines.flow.StateFlow

interface TorSyncState {
    /** True while sync errors come from Tor not carrying traffic yet. */
    val syncErrorsHidden: StateFlow<Boolean>

    /** Never waits long; does nothing unless Tor is connected. */
    suspend fun requestNewCircuits()
}
