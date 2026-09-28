package cash.p.terminal.modules.multiswap.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** TTL cache for catalog lookups; the load runs outside the lock and a thrown load stores nothing. */
internal class ExpiringCache<K, V>(private val ttlMillis: Long) {
    private class Entry<V>(val value: V, val timestamp: Long)

    private val mutex = Mutex()
    private val entries = mutableMapOf<K, Entry<V>>()

    suspend fun getOrLoad(key: K, load: suspend () -> V): V {
        mutex.withLock {
            entries[key]?.takeIf { System.currentTimeMillis() - it.timestamp < ttlMillis }
                ?.let { return it.value }
        }
        val value = load()
        mutex.withLock { entries[key] = Entry(value, System.currentTimeMillis()) }
        return value
    }

    suspend fun clear() = mutex.withLock { entries.clear() }
}
