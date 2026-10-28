package cash.p.terminal.core

import kotlinx.coroutines.delay

suspend fun <T> retryWhen(
    times: Int,
    predicate: suspend (cause: Throwable) -> Boolean,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Throwable) {
            if (!predicate(e)) {
                throw e
            }
        }
        delay(1000)
    }
    return block()
}
