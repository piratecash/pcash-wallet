package cash.p.terminal.core.tor

/** What the Tor settings need from the platform. */
interface TorPlatform {
    val isSupported: Boolean

    /** Called before the app restarts with Tor turned off. */
    fun beforeRestartAfterDisable()
}
