package cash.p.terminal.core

expect fun currentProcessId(): Long

/** Milliseconds since this process started. */
expect fun currentProcessAgeMillis(): Long
