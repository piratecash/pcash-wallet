package cash.p.terminal.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** A new entity must be assigned here: its group decides which database holds it once stores split. */
class StorageGroupsSchemaTest {

    @Test
    fun appDatabaseEntities_everyEntity_belongsToExactlyOneGroup() {
        val assignments = (ACCOUNT_ENTITIES + LEVEL_ENTITIES + SHARED_ENTITIES).groupingBy { it }.eachCount()

        assertEquals(emptyMap<String, Int>(), assignments.filterValues { it > 1 })
        assertEquals(appDatabaseEntities(), assignments.keys)
    }

    private fun appDatabaseEntities(): Set<String> {
        val source = File("src/commonMain/kotlin/cash/p/terminal/core/storage/AppDatabase.kt").readText()
        val entities = requireNotNull(Regex("""entities\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(source))
        return Regex("""(\w+)::class""").findAll(entities.groupValues[1]).map { it.groupValues[1] }.toSet()
    }

    private companion object {
        val ACCOUNT_ENTITIES = listOf(
            "AccountRecord",
            "EnabledWallet",
            "EnabledWalletCache",
            "RestoreSettingRecord",
            "WalletConnectV2Session",
            "TokenAutoEnabledBlockchain",
            "SpamAddress",
            "SpamScanState",
            "SwapProviderTransaction",
            "HardwarePublicKey",
            "RecentAddress",
            "MoneroFileRecord",
            "PendingMultiSwap",
            "PendingTransactionEntity",
            "ZcashSingleUseAddress",
            "UserDeletedWallet",
            "PoisonAddress",
            "LocallyCreatedTransactionRecord",
            "OfflineSignedTransactionEntity",
            "OfflineBlockchain",
        )
        val LEVEL_ENTITIES = listOf("ActiveAccount", "StackingCache", "LogEntry")
        val SHARED_ENTITIES = listOf(
            "BlockchainSettingRecord",
            "EvmSyncSourceRecord",
            "FavoriteCoin",
            "ChartIndicatorSetting",
            "AddressLabel",
            "EvmMethodLabel",
            "SyncerState",
            "Pin",
        )
    }
}
