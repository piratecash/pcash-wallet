package cash.p.terminal.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
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
    fun migrate124To125_tableAbsent_createsTableAndKeepsNftItems() = runBlocking {
        seedVersion124(dropPrefTable = true)

        assertMigratedWithNftItemKept()
    }

    @Test
    fun migrate124To125_tableAlreadyExists_succeedsAndKeepsNftItems() = runBlocking {
        seedVersion124(dropPrefTable = false)

        assertMigratedWithNftItemKept()
    }

    @Test
    fun upsert_afterMigration_replacesRowOfSameCollectionOnly() = runBlocking {
        seedVersion124(dropPrefTable = true)
        val database = buildAppDatabase(context)
        try {
            val dao = database.nftCollectionPrefDao()
            val other = pref("collection-b", hidden = true, userTrust = NftUserTrust.None)
            dao.upsert(pref("collection-a", hidden = true, userTrust = NftUserTrust.None))
            dao.upsert(other)
            val updated = pref("collection-a", hidden = false, userTrust = NftUserTrust.Spam)

            dao.upsert(updated)

            assertEquals(setOf(updated, other), dao.observe(ACCOUNT_ID).first().toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteByAccountIds_afterMigration_removesOnlyThoseAccounts() = runBlocking {
        seedVersion124(dropPrefTable = true)
        val database = buildAppDatabase(context)
        try {
            val dao = database.nftCollectionPrefDao()
            val kept = pref("collection-a", hidden = true, userTrust = NftUserTrust.Trust, accountId = OTHER_ACCOUNT_ID)
            dao.upsert(pref("collection-a", hidden = true, userTrust = NftUserTrust.Trust))
            dao.upsert(kept)

            dao.deleteByAccountIds(listOf(ACCOUNT_ID))

            assertEquals(emptyList<NftCollectionPrefRecord>(), dao.observe(ACCOUNT_ID).first())
            assertEquals(listOf(kept), dao.observe(OTHER_ACCOUNT_ID).first())
        } finally {
            database.close()
        }
    }

    private suspend fun seedVersion124(dropPrefTable: Boolean) {
        buildAppDatabase(context).run {
            nftItemDao().replace(ACCOUNT_ID, BlockchainType.Ton, OWNER, listOf(NFT_ITEM))
            openHelper.writableDatabase.run {
                if (dropPrefTable) execSQL("DROP TABLE NftCollectionPrefRecord")
                version = 124
            }
            close()
        }
    }

    /** Room validates the schema against the entity when the database opens. */
    private suspend fun assertMigratedWithNftItemKept() {
        val database = buildAppDatabase(context)
        try {
            database.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='NftCollectionPrefRecord'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            assertEquals(listOf(NFT_ITEM), database.nftItemDao().observe(ACCOUNT_ID, listOf(OWNER)).first())
        } finally {
            database.close()
        }
    }

    private fun pref(
        collectionKey: String,
        hidden: Boolean,
        userTrust: NftUserTrust,
        accountId: String = ACCOUNT_ID,
    ) = NftCollectionPrefRecord(accountId, BlockchainType.Ton, collectionKey, hidden, userTrust)

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val OTHER_ACCOUNT_ID = "account-2"
        const val OWNER = "owner-a"

        val NFT_ITEM = NftItemRecord(
            accountId = ACCOUNT_ID,
            blockchainType = BlockchainType.Ton,
            ownerAddress = OWNER,
            address = "nft-1",
            position = 0,
            name = "Name",
            description = null,
            realOwnerAddress = OWNER,
            collectionAddress = "collection-a",
            collectionName = "Collection",
            collectionDescription = null,
            trust = NftTrust.Blacklist,
            verified = false,
            imageUrl = null,
            largeImageUrl = null,
        )
    }
}
