package cash.p.terminal.core.tor

import net.freehaven.tor.control.TorControlConnection

/** Blocking calls over Tor's control connection; each throws IOException when Tor refuses or is gone. */
class TorControlClient(private val connection: TorControlConnection) {

    // Tor runs with no auth method, but still refuses other commands before AUTHENTICATE
    fun authenticate() = connection.authenticate(ByteArray(0))

    fun bootstrapProgress(): Int? =
        connection.getInfo("status/bootstrap-phase")?.let(PROGRESS::find)?.groupValues?.get(1)?.toIntOrNull()

    fun hasBuiltCircuit(): Boolean =
        connection.getInfo("circuit-status").orEmpty().lineSequence().any { it.contains(" BUILT ") }

    fun openSocks(port: Int) = connection.setConf("SOCKSPort", "$SOCKS_HOST:$port")

    fun closeSocks() = connection.setConf("SOCKSPort", "0")

    fun setNetworkEnabled(enabled: Boolean) = connection.setConf("DisableNetwork", if (enabled) "0" else "1")

    fun newCircuits() = connection.signal("NEWNYM")

    companion object {
        const val SOCKS_HOST = "127.0.0.1"
        const val BOOTSTRAP_DONE = 100

        private val PROGRESS = Regex("PROGRESS=(\\d+)")
    }
}
