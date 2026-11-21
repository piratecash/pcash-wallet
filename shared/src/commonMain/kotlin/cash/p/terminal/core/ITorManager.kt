package cash.p.terminal.core

import cash.p.terminal.modules.settings.security.tor.TorStatus
import kotlinx.coroutines.flow.StateFlow

interface ITorManager {
    fun start()
    suspend fun stop()
    fun setTorAsEnabled()
    fun setTorAsDisabled()
    val isTorEnabled: Boolean
    val torStatusFlow: StateFlow<TorStatus>

    /** Bootstrap percentage while Tor bootstraps, null otherwise. */
    val bootstrapProgressFlow: StateFlow<Int?>

    /** Asks Tor for fresh circuits when connected; never waits long. */
    suspend fun newCircuits()
}
