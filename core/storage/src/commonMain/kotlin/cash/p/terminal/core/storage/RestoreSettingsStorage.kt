package cash.p.terminal.core.storage

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.entities.RestoreSettingRecord

class RestoreSettingsStorage(private val router: StorageRouter) : IRestoreSettingsStorage {

    override fun restoreSettings(accountId: String, blockchainTypeUid: String): List<RestoreSettingRecord> {
        return router.account(accountId)?.restoreSettingDao()?.get(accountId, blockchainTypeUid).orEmpty()
    }

    override fun restoreSettings(accountId: String): List<RestoreSettingRecord> {
        return router.account(accountId)?.restoreSettingDao()?.get(accountId).orEmpty()
    }

    override fun save(restoreSettingRecords: List<RestoreSettingRecord>) {
        router.writePerStore(restoreSettingRecords, { it.accountId }) { restoreSettingDao().insert(it) }
    }

    override fun prepareForNewAccount(accountId: String) = router.prepareAccountStore(accountId)

    override fun deleteAllRestoreSettings(accountId: String) {
        router.account(accountId)?.restoreSettingDao()?.delete(accountId)
    }
}
