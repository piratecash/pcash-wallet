package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.adapters.zcash.session.ZcashSession
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Per account: the highest receive-scope transparent index ever handed out for a swap (backed up),
 * and the index the rows recreated after a restore or rescan reach (local only). Both only go up.
 */
class ZcashTransparentIndexStore(
    private val storage: IRestoreSettingsStorage,
    private val issuedAddresses: ZcashSingleUseAddressStorage,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val mutex = Mutex()

    suspend fun issueIndex(accountId: String): Int? = read(accountId, ISSUE_INDEX)

    /** Receive must stay above it: every address at or below it may have been given to a provider. */
    suspend fun recreatedThrough(accountId: String): Int? = read(accountId, RECREATED_THROUGH)

    suspend fun raise(accountId: String, index: Int) = raise(accountId, ISSUE_INDEX, index)

    /** Before the session's first discovery, sync or issuance, so the recreated rows are scanned from birth. */
    suspend fun prepare(session: ZcashSession) = session.prepareTransparentIndexOnce { wallet ->
        backfill(session.accountId, wallet, session.dbAccountId)
        recreateIssuedRows(session.accountId, wallet, session.dbAccountId)
    }

    suspend fun raiseFromHistory(session: ZcashSession) {
        session.withOperation { raiseFromHistory(session.accountId, it, session.dbAccountId) }
    }

    /** Only addresses with history count: Receive's own unused address must not move the index. */
    suspend fun raiseFromHistory(accountId: String, wallet: ZcashWallet, dbAccountId: Int) {
        wallet.highestUsedTransparentAddressIndex(dbAccountId)?.let { raise(accountId, it) }
    }

    suspend fun clear(accountId: String) = mutex.withLock {
        write(listOf(record(accountId, ISSUE_INDEX, ""), record(accountId, RECREATED_THROUGH, "")))
    }

    /** Addresses issued before the index existed; only those the SDK still knows have an index. */
    private suspend fun backfill(accountId: String, wallet: ZcashWallet, dbAccountId: Int) {
        if (issueIndex(accountId) != null) return
        issuedAddresses.getAllAddresses(accountId)
            .mapNotNull { wallet.transparentAddressIndex(dbAccountId, it.address) }
            .maxOrNull()
            ?.let { raise(accountId, it) }
    }

    private suspend fun recreateIssuedRows(accountId: String, wallet: ZcashWallet, dbAccountId: Int) {
        val target = issueIndex(accountId) ?: return
        if ((wallet.highestStoredTransparentAddressIndex(dbAccountId) ?: -1) >= target) return
        // Floor first: if the process dies before the rows exist, Receive still stays above them.
        raise(accountId, RECREATED_THROUGH, target)
        wallet.reserveTransparentAddressesThrough(dbAccountId, target)
    }

    private suspend fun raise(accountId: String, key: String, index: Int) = mutex.withLock {
        val stored = read(accountId, key)
        if (stored == null || index > stored) write(listOf(record(accountId, key, index.toString())))
    }

    private suspend fun read(accountId: String, key: String): Int? = withContext(dispatcherProvider.io) {
        storage.restoreSettings(accountId, BlockchainType.Zcash.uid)
            .firstOrNull { it.key == key }
            ?.value
            ?.toIntOrNull()
    }

    private suspend fun write(records: List<RestoreSettingRecord>) =
        withContext(dispatcherProvider.io) { storage.save(records) }

    private fun record(accountId: String, key: String, value: String) =
        RestoreSettingRecord(accountId, BlockchainType.Zcash.uid, key, value)

    private companion object {
        val ISSUE_INDEX = RestoreSettingType.ZcashTransparentIssueIndex.name
        const val RECREATED_THROUGH = "zcash_transparent_recreated_through"
    }
}
