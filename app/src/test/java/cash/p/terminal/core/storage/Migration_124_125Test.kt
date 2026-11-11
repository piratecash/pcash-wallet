package cash.p.terminal.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_124_125Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate124To125_tableAbsent_createsValidTableAccountKept() {
        seedVersion124(dropNftTable = true)

        assertMigratedWithAccountKept()
    }

    @Test
    fun migrate124To125_tableAlreadyExists_succeedsAccountKept() {
        seedVersion124(dropNftTable = false)

        assertMigratedWithAccountKept()
    }

    @Test
    fun replace_afterMigrationWithAnotherOwner_swapsOnlyThatOwnerInPositionOrder() = runBlocking {
        val database = migratedDatabase()
        try {
            val dao = database.nftItemDao()
            dao.replace(ACCOUNT_ID, BlockchainType.Ton, OWNER_A, listOf(record(OWNER_A, "stale", 0)))
            val otherOwnerItem = record(OWNER_B, "nft-b", 0)
            dao.replace(ACCOUNT_ID, BlockchainType.Ton, OWNER_B, listOf(otherOwnerItem))
            val first = record(OWNER_A, "nft-1", 0)
            val second = record(OWNER_A, "nft-2", 1)

            dao.replace(ACCOUNT_ID, BlockchainType.Ton, OWNER_A, listOf(second, first))

            assertEquals(
                listOf(first, second, otherOwnerItem),
                dao.observe(ACCOUNT_ID, listOf(OWNER_A, OWNER_B)).first(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun replace_duplicateAddressInList_keepsLastRow() = runBlocking {
        val database = migratedDatabase()
        try {
            val dao = database.nftItemDao()
            val last = record(OWNER_A, "nft-1", 1)

            dao.replace(ACCOUNT_ID, BlockchainType.Ton, OWNER_A, listOf(record(OWNER_A, "nft-1", 0), last))

            assertEquals(listOf(last), dao.observe(ACCOUNT_ID, listOf(OWNER_A)).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun replace_afterMigration_storesTrustAsEnumName() = runBlocking {
        val database = migratedDatabase()
        try {
            database.nftItemDao().replace(ACCOUNT_ID, BlockchainType.Ton, OWNER_A, listOf(record(OWNER_A, "nft-1", 0)))

            database.openHelper.readableDatabase.query("SELECT trust FROM NftItemRecord").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Graylist", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    private fun migratedDatabase(): AppDatabase {
        seedVersion124(dropNftTable = true)
        return buildAppDatabase(context)
    }

    private fun seedVersion124(dropNftTable: Boolean) {
        buildAppDatabase(context).run {
            openHelper.writableDatabase.run {
                execSQL(
                    "INSERT INTO AccountRecord (id, name, type, origin, isBackedUp, isFileBackedUp, level, deleted) " +
                        "VALUES ('$ACCOUNT_ID', 'Seeded', 'mnemonic', 'Created', 1, 0, 0, 0)"
                )
                if (dropNftTable) execSQL("DROP TABLE NftItemRecord")
                version = 124
            }
            close()
        }
    }

    /** Room validates the schema against the entity when the database opens. */
    private fun assertMigratedWithAccountKept() {
        val database = buildAppDatabase(context)
        try {
            database.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='NftItemRecord'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            assertEquals(listOf(ACCOUNT_ID), database.accountsDao().getIds())
        } finally {
            database.close()
        }
    }

    private fun record(owner: String, address: String, position: Int) = NftItemRecord(
        accountId = ACCOUNT_ID,
        blockchainType = BlockchainType.Ton,
        ownerAddress = owner,
        address = address,
        position = position,
        name = "Name",
        description = null,
        realOwnerAddress = owner,
        collectionAddress = null,
        collectionName = "Collection",
        collectionDescription = null,
        trust = NftTrust.Graylist,
        verified = true,
        imageUrl = "https://example.com/500.png",
        largeImageUrl = null,
    )

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
    }
}
