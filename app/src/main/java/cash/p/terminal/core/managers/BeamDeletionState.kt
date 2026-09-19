package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.wallet.AccountDeletionBlockedException

class BeamDeletionState(context: Context, private val database: AppDatabase) {
    // Outside the wallet directory and independent of encrypted account decoding.
    private val preferences = context.getSharedPreferences("beam_deletion", Context.MODE_PRIVATE)

    val resetPending: Boolean get() = preferences.contains("reset_pending")
    val accountIds: List<String> get() = database.accountsDao().getIds()
    val hasRestoreIntent: Boolean get() = database.restoreSettingDao().hasBeamSettings()

    fun ensureAvailable(accountId: String) {
        check(!resetPending && database.accountsDao().isAvailable(accountId)) {
            "BEAM account is deleted or awaiting cleanup"
        }
    }

    fun ensureDeletionPending(accountIds: List<String>) {
        if (resetPending) {
            beginReset()
            return
        }
        check(database.accountsDao().getDeletedIds().containsAll(accountIds)) {
            "BEAM deletion requires a durable intent"
        }
    }

    fun beginReset() {
        if (!preferences.edit().putBoolean("reset_pending", true).commit()) {
            throw AccountDeletionBlockedException()
        }
    }

    fun clearLinkage(accountIds: List<String>) = database.restoreSettingDao().deleteBeam(accountIds)

    fun clearResetLinkage() = database.restoreSettingDao().deleteAllBeam()

    fun finishReset() {
        check(accountIds.isEmpty() && !hasRestoreIntent) { "BEAM reset linkage remains" }
        if (!preferences.edit().remove("reset_pending").commit()) {
            // SharedPreferences can publish a failed commit in memory.
            preferences.edit().putBoolean("reset_pending", true).commit()
            throw AccountDeletionBlockedException()
        }
    }
}
