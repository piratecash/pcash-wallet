package cash.p.terminal.modules.multiswap.providers

import java.util.Collections

/** Thread-safe access-ordered cache that drops the least recently used entry above [maxSize]. */
internal class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val entries: MutableMap<K, V> = Collections.synchronizedMap(
        object : LinkedHashMap<K, V>(maxSize, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
        }
    )

    operator fun get(key: K): V? = entries[key]

    fun put(key: K, value: V) {
        entries[key] = value
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
