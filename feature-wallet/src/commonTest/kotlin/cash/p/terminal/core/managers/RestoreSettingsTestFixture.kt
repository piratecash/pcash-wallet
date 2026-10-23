package cash.p.terminal.core.managers

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.entities.RestoreSettingRecord
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap

internal class RestoreSettingsTestFixture {
    val storage = mockk<IRestoreSettingsStorage>()
    private val records = ConcurrentHashMap<Triple<String, String, String>, RestoreSettingRecord>()

    init {
        every { storage.save(any()) } answers {
            firstArg<List<RestoreSettingRecord>>().forEach { record ->
                records[Triple(record.accountId, record.blockchainTypeUid, record.key)] = record
            }
        }
        every { storage.restoreSettings(any()) } answers {
            val accountId = firstArg<String>()
            records.values.filter { it.accountId == accountId }
        }
        every { storage.restoreSettings(any(), any()) } answers {
            val accountId = firstArg<String>()
            val blockchainUid = secondArg<String>()
            records.values.filter { it.accountId == accountId && it.blockchainTypeUid == blockchainUid }
        }
    }

    fun manager() = RestoreSettingsManager(storage, mockk(), mockk(), mockk())
}
