package cash.p.terminal.core.storage

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.entities.RestoreSettingRecord

class RestoreSettingsStorage(private val databaseProvider: AppDatabaseProvider) : IRestoreSettingsStorage {
    private val dao: RestoreSettingDao
        get() = databaseProvider.database.restoreSettingDao()

    override fun restoreSettings(accountId: String, blockchainTypeUid: String): List<RestoreSettingRecord> {
        return dao.get(accountId, blockchainTypeUid)
    }

    override fun restoreSettings(accountId: String): List<RestoreSettingRecord> {
        return dao.get(accountId)
    }

    override fun save(restoreSettingRecords: List<RestoreSettingRecord>) {
        dao.insert(restoreSettingRecords)
    }

    override fun deleteAllRestoreSettings(accountId: String) {
        dao.delete(accountId)
    }
}
