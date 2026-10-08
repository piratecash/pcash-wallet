package androidx.room.util

import androidx.collection.ArrayMap

/** SQLite's own limit on bound parameters, the reason the generated code chunks at all. */
private const val MAX_BIND_PARAMETER_CNT = 999

/**
 * Room generates calls to this for `@Relation` fetches but ships it only in its Android artifact.
 * The package and signature are dictated by that generated code, not chosen; the file name is
 * unique so the facade never clashes with the same shim shipped by another kit.
 *
 * Fetches the relation in chunks of at most [MAX_BIND_PARAMETER_CNT] keys. For a single-valued
 * relation (`isRelationCollection == false`) the values are filled in by [fetchBlock] and copied
 * back into [map]; for a collection the per-key containers are passed through and filled in place.
 */
fun <K : Any, V> recursiveFetchArrayMap(
    map: ArrayMap<K, V>,
    isRelationCollection: Boolean,
    fetchBlock: (ArrayMap<K, V>) -> Unit,
) {
    val chunk = ArrayMap<K, V>(MAX_BIND_PARAMETER_CNT)
    var count = 0
    var mapIndex = 0
    val limit = map.size
    while (mapIndex < limit) {
        @Suppress("UNCHECKED_CAST")
        chunk[map.keyAt(mapIndex)] =
            if (isRelationCollection) map.valueAt(mapIndex) else null as V
        mapIndex++
        count++
        if (count == MAX_BIND_PARAMETER_CNT) {
            fetchBlock(chunk)
            if (!isRelationCollection) map.copyFrom(chunk)
            chunk.clear()
            count = 0
        }
    }
    if (count > 0) {
        fetchBlock(chunk)
        if (!isRelationCollection) map.copyFrom(chunk)
    }
}

private fun <K : Any, V> ArrayMap<K, V>.copyFrom(source: ArrayMap<K, V>) {
    source.forEach { (key, value) -> put(key, value) }
}
