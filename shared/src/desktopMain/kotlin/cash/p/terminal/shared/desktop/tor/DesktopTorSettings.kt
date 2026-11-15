package cash.p.terminal.shared.desktop.tor

import cash.p.terminal.core.tor.TorPlatform
import cash.p.terminal.core.tor.TorSettings
import io.horizontalsystems.core.DesktopSettingsStore

/** Reads as off where Tor cannot run, so nothing waits for a Tor that never starts. */
class DesktopTorSettings(
    private val store: DesktopSettingsStore,
    private val platform: TorPlatform,
) : TorSettings {
    override var torEnabled: Boolean
        get() = platform.isSupported && store.flag(TOR_ENABLED) == true
        set(value) = store.setFlag(TOR_ENABLED, value)

    private companion object {
        const val TOR_ENABLED = "torEnabled"
    }
}
