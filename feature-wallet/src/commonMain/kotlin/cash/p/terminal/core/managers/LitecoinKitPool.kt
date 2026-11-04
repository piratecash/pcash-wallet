package cash.p.terminal.core.managers

import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.litecoinkit.LitecoinKit
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/** SyncMode has no equals, so its class identifies it — as the kit's database name does. */
data class LitecoinKitKey(
    val accountId: String,
    val syncModeClass: KClass<out BitcoinCore.SyncMode>,
    val purpose: HDWallet.Purpose,
    val mwebEnabled: Boolean,
)

/** Kits with one key open the same database file, so adapters with that key share one kit. */
class LitecoinKitPool {
    private class Entry(val kit: LitecoinKit, val holders: Int)

    private val entries = ConcurrentHashMap<LitecoinKitKey, Entry>()

    fun acquire(key: LitecoinKitKey, create: () -> LitecoinKit): LitecoinKit {
        val entry = entries.compute(key) { _, entry ->
            entry?.let { Entry(it.kit, it.holders + 1) } ?: Entry(create(), 1)
        }
        return checkNotNull(entry).kit
    }

    fun release(kit: LitecoinKit) {
        val key = entries.entries.firstOrNull { it.value.kit === kit }?.key ?: return
        var lastHolder = false
        entries.compute(key) { _, entry ->
            when {
                entry == null || entry.kit !== kit -> entry
                entry.holders > 1 -> Entry(kit, entry.holders - 1)
                else -> {
                    lastHolder = true
                    null
                }
            }
        }
        if (lastHolder) kit.dispose()
    }
}
