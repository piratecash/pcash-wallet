package cash.p.terminal.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_122_123Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate122To123_nftTablesWithRows_droppedAccountKept() {
        seedVersion122 {
            nftTableStatements().also { assertEquals(4, it.size) }.forEach(::execSQL)
            execSQL(
                "INSERT INTO NftCollectionRecord (blockchainType, accountId, uid, name) " +
                    "VALUES ('ethereum', '$ACCOUNT_ID', 'collection-1', 'Collection')"
            )
            execSQL(
                "INSERT INTO NftAssetRecord (blockchainType, accountId, nftUid, collectionUid, onSale) " +
                    "VALUES ('ethereum', '$ACCOUNT_ID', '$NFT_UID', 'collection-1', 0)"
            )
            execSQL("INSERT INTO NftMetadataSyncRecord VALUES ('ethereum', '$ACCOUNT_ID', 1)")
            execSQL(
                "INSERT INTO NftAssetBriefMetadataRecord (nftUid, providerCollectionUid) " +
                    "VALUES ('$NFT_UID', 'collection-1')"
            )
        }

        assertMigratedWithAccountKept()
    }

    @Test
    fun migrate122To123_noNftTables_succeedsAccountKept() {
        seedVersion122 {}

        assertMigratedWithAccountKept()
    }

    private fun seedVersion122(seedNft: SupportSQLiteDatabase.() -> Unit) {
        buildAppDatabase(context).run {
            openHelper.writableDatabase.run {
                execSQL(
                    "INSERT INTO AccountRecord (id, name, type, origin, isBackedUp, isFileBackedUp, level, deleted) " +
                        "VALUES ('$ACCOUNT_ID', 'Seeded', 'mnemonic', 'Created', 1, 0, 0, 0)"
                )
                seedNft()
                version = 122
            }
            close()
        }
    }

    private fun assertMigratedWithAccountKept() {
        val database = buildAppDatabase(context)
        try {
            database.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE 'Nft%'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }

            assertEquals(listOf(ACCOUNT_ID), database.accountsDao().getIds())
        } finally {
            database.close()
        }
    }

    /** The NFT DDL a released app left behind; unchanged from v118 up to v122. */
    private fun nftTableStatements(): List<String> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(SCHEMA_RESOURCE))
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines().map(String::trim).filter { it.startsWith("CREATE TABLE IF NOT EXISTS `Nft") }
        }
    }

    private companion object {
        const val SCHEMA_RESOURCE = "schema/app_db_v118.sql"
        const val ACCOUNT_ID = "account-1"
        const val NFT_UID = "evm|ethereum|0x1|1"
    }
}
