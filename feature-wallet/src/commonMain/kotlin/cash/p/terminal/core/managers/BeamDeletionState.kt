package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.storage.acrossAccounts
import cash.p.terminal.core.storage.forEachAccount

/** Durable "explicit reset in progress" flag; a failed write throws AccountDeletionBlockedException. */
interface BeamResetMarker {
    val isSet: Boolean
    fun set()
    fun clear()
}

class BeamDeletionState(private val router: StorageRouter, private val resetMarker: BeamResetMarker) {
    val resetPending: Boolean get() = resetMarker.isSet
    val accountIds: List<String> get() = router.acrossAccounts { accountsDao().getIds() }
    val hasRestoreIntent: Boolean get() = router.accounts.any { it.restoreSettingDao().hasBeamSettings() }

    fun ensureAvailable(accountId: String) {
        check(!resetPending && router.account(accountId)?.accountsDao()?.isAvailable(accountId) == true) {
            "BEAM account is deleted or awaiting cleanup"
        }
    }

    fun ensureDeletionPending(accountIds: List<String>) {
        if (resetPending) {
            beginReset()
            return
        }
        check(router.acrossAccounts { accountsDao().getDeletedIds() }.containsAll(accountIds)) {
            "BEAM deletion requires a durable intent"
        }
    }

    fun beginReset() = resetMarker.set()

    fun clearLinkage(accountIds: List<String>) = router.forEachAccount { restoreSettingDao().deleteBeam(accountIds) }

    fun clearResetLinkage() = router.forEachAccount { restoreSettingDao().deleteAllBeam() }

    fun finishReset() {
        check(accountIds.isEmpty() && !hasRestoreIntent) { "BEAM reset linkage remains" }
        resetMarker.clear()
    }
}
