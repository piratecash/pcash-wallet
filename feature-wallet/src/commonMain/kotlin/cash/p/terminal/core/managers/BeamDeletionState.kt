package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.AppDatabaseProvider

/** Durable "explicit reset in progress" flag; a failed write throws AccountDeletionBlockedException. */
interface BeamResetMarker {
    val isSet: Boolean
    fun set()
    fun clear()
}

class BeamDeletionState(private val databases: AppDatabaseProvider, private val resetMarker: BeamResetMarker) {
    val resetPending: Boolean get() = resetMarker.isSet
    val accountIds: List<String> get() = databases.database.accountsDao().getIds()
    val hasRestoreIntent: Boolean get() = databases.database.restoreSettingDao().hasBeamSettings()

    fun ensureAvailable(accountId: String) {
        check(!resetPending && databases.database.accountsDao().isAvailable(accountId)) {
            "BEAM account is deleted or awaiting cleanup"
        }
    }

    fun ensureDeletionPending(accountIds: List<String>) {
        if (resetPending) {
            beginReset()
            return
        }
        check(databases.database.accountsDao().getDeletedIds().containsAll(accountIds)) {
            "BEAM deletion requires a durable intent"
        }
    }

    fun beginReset() = resetMarker.set()

    fun clearLinkage(accountIds: List<String>) = databases.database.restoreSettingDao().deleteBeam(accountIds)

    fun clearResetLinkage() = databases.database.restoreSettingDao().deleteAllBeam()

    fun finishReset() {
        check(accountIds.isEmpty() && !hasRestoreIntent) { "BEAM reset linkage remains" }
        resetMarker.clear()
    }
}
