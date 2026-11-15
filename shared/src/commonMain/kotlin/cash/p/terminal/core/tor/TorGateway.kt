package cash.p.terminal.core.tor

import kotlinx.coroutines.flow.SharedFlow

/** Starts and stops the platform's Tor and reports when it is gone. Calls other than [events] block. */
interface TorGateway {

    enum class Event { ServiceLost, TorExited }

    val events: SharedFlow<Event>
    val isTorRunning: Boolean

    /** Starts a fresh Tor; false when it cannot start. */
    fun bind(): Boolean

    fun unbind()

    fun controlClient(): TorControlClient?

    /** Opens our own control connection, replacing the previous one; null until Tor accepts it. */
    fun openControlClient(): TorControlClient?

    /** Removes Tor files nothing will use, e.g. left over by an older version or with Tor turned off. */
    fun deleteStaleFiles(torEnabled: Boolean)

    companion object {
        // The SOCKS listener stays closed until TorManager opens it on a working circuit.
        val BASE_TORRC_OPTIONS = listOf(
            "SOCKSPort 0",
            "HTTPTunnelPort 0",
            "DNSPort 0",
            "TransPort 0",
            "SafeSocks 0",
            "TestSocks 0",
            "AvoidDiskWrites 1",
            "ReducedConnectionPadding 1",
            "ReducedCircuitPadding 1",
        )
    }
}
