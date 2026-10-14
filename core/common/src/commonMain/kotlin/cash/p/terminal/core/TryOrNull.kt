package cash.p.terminal.core

inline fun <T> tryOrNull(block: () -> T): T? {
    return try {
        block()
    } catch (_: Throwable) {
        null
    }
}
