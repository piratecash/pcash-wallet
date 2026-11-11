package cash.p.terminal.shared.desktop.session

import cash.p.terminal.wallet.Account
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.instance.SingleInstanceFactory
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Walks everything reachable from the app's singletons and names each place that still mentions an account.
 * Only our own and the kits' classes are opened up; a container is followed into its elements.
 */
internal class AccountIdScan(private val accountIds: Set<String>) {
    private class Node(val value: Any, val path: String)

    private val seen = IdentityHashMap<Any, Unit>()
    private val queue = ArrayDeque<Node>()
    val found = mutableListOf<String>()

    @OptIn(KoinInternalApi::class)
    fun scan(koin: Koin): List<String> {
        createdSingletons(koin).forEach { enqueue(it, it::class.java.name) }
        while (queue.isNotEmpty()) visit(queue.removeFirst())
        return found.distinct().sorted()
    }

    private fun enqueue(value: Any?, path: String) {
        if (value == null || isLeaf(value)) return
        // Before the seen check: one id string or account object lying in several holders counts in each.
        when (value) {
            is String -> if (accountIds.any(value::contains)) found += path
            is Account -> if (value.id in accountIds) found += path
        }
        if (value is String || value is Account || seen.containsKey(value)) return
        seen[value] = Unit
        queue.addLast(Node(value, path))
    }

    private fun isLeaf(value: Any): Boolean =
        value is Number || value is Boolean || value is Char || value is Enum<*> || value is Class<*>

    private fun visit(node: Node) {
        val value = node.value
        when (value) {
            is Map<*, *> -> elements { value.entries.toList() }.forEach { entry ->
                val pair = entry as Map.Entry<*, *>
                enqueue(pair.key, "${node.path}.key")
                enqueue(pair.value, "${node.path}.value")
            }
            is Iterable<*> -> elements { value.toList() }.forEach { enqueue(it, "${node.path}[]") }
            is Array<*> -> value.forEach { enqueue(it, "${node.path}[]") }
            is StateFlow<*> -> enqueue(value.value, "${node.path}.value")
            is SharedFlow<*> -> value.replayCache.forEach { enqueue(it, "${node.path}.replay") }
            is BehaviorSubject<*> -> enqueue(value.value, "${node.path}.value")
            is AtomicReference<*> -> enqueue(value.get(), "${node.path}.ref")
            is Lazy<*> -> if (value.isInitialized()) enqueue(value.value, "${node.path}.lazy")
            else -> if (isOurs(value)) fields(node)
        }
    }

    private inline fun <T> elements(read: () -> List<T>): List<T> = try {
        read()
    } catch (e: ConcurrentModificationException) {
        emptyList()
    }

    private fun isOurs(value: Any): Boolean {
        val name = value::class.java.name
        return OUR_PACKAGES.any(name::startsWith) && "Subclass" !in name
    }

    private fun fields(node: Node) {
        var type: Class<*>? = node.value::class.java
        while (type != null && type != Any::class.java) {
            type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.type.isPrimitive }.forEach { field ->
                if (field.trySetAccessible()) enqueue(field.get(node.value), "${node.path}.${field.name}")
            }
            type = type.superclass
        }
    }

    @OptIn(KoinInternalApi::class)
    private fun createdSingletons(koin: Koin): List<Any> =
        koin.instanceRegistry.instances.values.filterIsInstance<SingleInstanceFactory<*>>()
            .filter { it.isCreated() }
            .mapNotNull { factory ->
                val field = SingleInstanceFactory::class.java.getDeclaredField("value").apply { isAccessible = true }
                field.get(factory)
            }
            .distinct()

    private companion object {
        val OUR_PACKAGES = listOf("cash.p.terminal", "io.horizontalsystems", "com.m2049r", "com.tangem")
    }
}
