package cash.p.terminal.core

import cash.p.terminal.entities.RestoreSettingRecord

interface IRestoreSettingsStorage {
    fun restoreSettings(accountId: String, blockchainTypeUid: String): List<RestoreSettingRecord>
    fun restoreSettings(accountId: String): List<RestoreSettingRecord>
    fun save(restoreSettingRecords: List<RestoreSettingRecord>)
    fun prepareForNewAccount(accountId: String)
    fun deleteAllRestoreSettings(accountId: String)
}
