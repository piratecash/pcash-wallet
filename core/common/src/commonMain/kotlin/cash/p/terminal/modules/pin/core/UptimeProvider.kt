package cash.p.terminal.modules.pin.core

/** Elapsed milliseconds from a platform clock; only differences within one run are meaningful. */
interface UptimeProvider {
    val uptime: Long
}
