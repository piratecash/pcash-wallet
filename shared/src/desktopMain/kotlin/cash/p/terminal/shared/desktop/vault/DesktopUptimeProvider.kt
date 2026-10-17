package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.modules.pin.core.UptimeProvider

/** The JVM has no monotonic clock surviving a restart, so the lockout counter uses wall time. */
class DesktopUptimeProvider : UptimeProvider {
    override val uptime: Long
        get() = System.currentTimeMillis()
}
