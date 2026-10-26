package cash.p.terminal.core

import java.lang.management.ManagementFactory

// ProcessHandle is JVM-only: Android has no such API, hence the expect.
actual fun currentProcessId(): Long = ProcessHandle.current().pid()

actual fun currentProcessAgeMillis(): Long = ManagementFactory.getRuntimeMXBean().uptime
