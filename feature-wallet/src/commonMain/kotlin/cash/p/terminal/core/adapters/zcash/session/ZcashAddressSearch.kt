package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.adapters.zcash.ZcashTransparentIndexStore
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ZcashAddressSearchState(
    val checkedThrough: Int,
    val target: Int?,
    val checkedAt: Long?,
    val found: Int,
) {
    val running: Boolean get() = target != null && target > checkedThrough
}

internal enum class ZcashAddressSearchStep { IDLE, CHECKED, MORE_PENDING }

/**
 * The manual walk past what automatic discovery covers, one chunk per scheduler turn. Its progress
 * is local only: what it finds lives in the SDK database, which a backup does not carry.
 */
class ZcashAddressSearch(
    private val storage: IRestoreSettingsStorage,
    private val transparentIndexStore: ZcashTransparentIndexStore,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val mutex = Mutex()
    private val changes = MutableStateFlow(0)

    /** Null until the first certified discovery walk tells where the search starts. */
    fun state(accountId: String): Flow<ZcashAddressSearchState?> =
        changes.map { read(accountId) }.distinctUntilChanged()

    suspend fun start(accountId: String, more: Int) {
        require(more > 0) { "Nothing to check: $more" }
        update(accountId) { mapOf(TARGET to (it.checkedThrough + more).toLong()) }
    }

    suspend fun checkAllAgain(accountId: String) = update(accountId) { state ->
        val target = state.checkedThrough.takeIf { it > 0 } ?: DEFAULT_MORE
        mapOf(CHECKED_THROUGH to 0L, FOUND to 0L, TARGET to target.toLong())
    }

    /** The chunk in flight still finishes and is kept. */
    suspend fun stop(accountId: String) = update(accountId) { mapOf(TARGET to null) }

    suspend fun clear(accountId: String) = mutex.withLock { write(accountId, KEYS.associateWith { null }) }

    /** One session operation, so an erase cannot land between the walk, the index raise and the record. */
    internal suspend fun runStep(session: ZcashSession): ZcashAddressSearchStep {
        if (!session.supportsTransparent) return ZcashAddressSearchStep.IDLE
        val outcome = session.withOperation { wallet ->
            val state = read(session.accountId)
            if (state == null) {
                initCursor(wallet, session)
                return@withOperation ZcashAddressSearchStep.IDLE
            }
            val target = state.target?.takeIf { it > state.checkedThrough }
                ?: return@withOperation ZcashAddressSearchStep.IDLE
            val from = state.checkedThrough
            val to = minOf(from + CHUNK, target)
            val found = wallet.discoverTransparentAddressRange(
                session.dbAccountId, from, to, ZcashSession.DISCOVERY_CONCURRENCY,
            )
            // Before the record: a chunk whose find is not in the index must be walked again.
            transparentIndexStore.raiseFromHistory(session.accountId, wallet, session.dbAccountId)
            recordChunk(session.accountId, from, to, found)
        }
        return (outcome as? ZcashSessionResult.Success)?.value ?: ZcashAddressSearchStep.IDLE
    }

    /** Starts where the automatic walk stopped: the highest used index plus the gap it walked past it. */
    private suspend fun initCursor(wallet: ZcashWallet, session: ZcashSession) {
        val coverage = wallet.transparentCoverage(session.dbAccountId)
        if (!coverage.certified) return
        val start = (wallet.highestUsedTransparentAddressIndex(session.dbAccountId) ?: -1) + 1 + coverage.gap
        mutex.withLock {
            if (read(session.accountId) == null) write(session.accountId, mapOf(CHECKED_THROUGH to start.toLong()))
        }
    }

    private suspend fun recordChunk(accountId: String, from: Int, to: Int, found: Int) = mutex.withLock {
        val state = read(accountId)
        // "Check everything again" moved the cursor during the walk; that run owns it now.
        if (state == null || state.checkedThrough != from) return@withLock ZcashAddressSearchStep.CHECKED
        write(
            accountId,
            mapOf(
                CHECKED_THROUGH to to.toLong(),
                FOUND to (state.found + found).toLong(),
                CHECKED_AT to System.currentTimeMillis(),
            ),
        )
        if (state.target != null && state.target > to) ZcashAddressSearchStep.MORE_PENDING
        else ZcashAddressSearchStep.CHECKED
    }

    private suspend fun update(
        accountId: String,
        values: (ZcashAddressSearchState) -> Map<String, Long?>,
    ) = mutex.withLock {
        read(accountId)?.let { write(accountId, values(it)) }
    }

    private suspend fun read(accountId: String): ZcashAddressSearchState? = withContext(dispatcherProvider.io) {
        val values = storage.restoreSettings(accountId, BlockchainType.Zcash.uid)
            .filter { it.key in KEYS }
            .associate { it.key to it.value.toLongOrNull() }
        values[CHECKED_THROUGH]?.let { checkedThrough ->
            ZcashAddressSearchState(
                checkedThrough = checkedThrough.toInt(),
                target = values[TARGET]?.toInt(),
                checkedAt = values[CHECKED_AT],
                found = values[FOUND]?.toInt() ?: 0,
            )
        }
    }

    /** A null value is stored empty, which reads back as absent. */
    private suspend fun write(accountId: String, values: Map<String, Long?>) {
        withContext(dispatcherProvider.io) {
            storage.save(
                values.map { (key, value) ->
                    RestoreSettingRecord(accountId, BlockchainType.Zcash.uid, key, value?.toString().orEmpty())
                }
            )
        }
        changes.update { it + 1 }
    }

    companion object {
        const val DEFAULT_MORE = 500
        const val MAX_MORE = 100_000

        /** About two minutes at [ESTIMATED_MS_PER_ADDRESS]. */
        const val LONG_SEARCH_THRESHOLD = 3_000

        /** Measured on mainnet at the chain tip; the kit's discovery calls dominate, not the sync passes. */
        private const val ESTIMATED_MS_PER_ADDRESS = 40L
        private const val MS_PER_MINUTE = 60_000L

        internal const val CHUNK = 100
        private const val CHECKED_THROUGH = "zcash_address_search_checked_through"
        private const val TARGET = "zcash_address_search_target"
        private const val CHECKED_AT = "zcash_address_search_checked_at"
        private const val FOUND = "zcash_address_search_found"
        private val KEYS = setOf(CHECKED_THROUGH, TARGET, CHECKED_AT, FOUND)

        /** Null when [more] is short enough not to warn about. */
        fun longSearchMinutes(more: Int): Int? = if (more > LONG_SEARCH_THRESHOLD) {
            ((more * ESTIMATED_MS_PER_ADDRESS + MS_PER_MINUTE - 1) / MS_PER_MINUTE).toInt()
        } else {
            null
        }
    }
}
