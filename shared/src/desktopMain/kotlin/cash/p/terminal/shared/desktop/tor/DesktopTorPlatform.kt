package cash.p.terminal.shared.desktop.tor

import cash.p.terminal.core.tor.TorPlatform
import cash.p.terminal.shared.desktop.qrscanner.DesktopOs

/** Windows gets no Tor until its executable can be signed. */
class DesktopTorPlatform(os: DesktopOs) : TorPlatform {
    override val isSupported: Boolean = os != DesktopOs.Windows

    // The vault asks for the password after every relaunch, so there is nothing to carry over
    override fun beforeRestartAfterDisable() = Unit
}
