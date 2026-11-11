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
class Migration_125_126Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate125To126_columnsAbsent_addsThemWithDefaultsAndKeepsItem() = runBlocking {
        seedVersion125(dropNewColumns = true)

        assertEquals(listOf(NFT_ITEM), readItems())
    }

    @Test
    fun migrate125To126_columnsAlreadyExist_succeedsAndKeepsItem() = runBlocking {
        seedVersion125(dropNewColumns = false)

        assertEquals(listOf(NFT_ITEM), readItems())
    }

    @Test
    fun migrate125To126_afterMigration_storesSaleStateAndLottieUrl() = runBlocking {
        seedVersion125(dropNewColumns = true)
        val listed = NFT_ITEM.copy(inSale = true, lottieUrl = "https://nft.fragment.com/gift/a.lottie.json")

        val database = buildAppDatabase(context)
        try {
            database.nftItemDao().replace(ACCOUNT_ID, BlockchainType.Ton, OWNER, listOf(listed))

            assertEquals(listOf(listed), database.nftItemDao().observe(ACCOUNT_ID, listOf(OWNER)).first())
        } finally {
            database.close()
        }
    }

    private suspend fun seedVersion125(dropNewColumns: Boolean) {
        buildAppDatabase(context).run {
            nftItemDao().replace(ACCOUNT_ID, BlockchainType.Ton, OWNER, listOf(NFT_ITEM))
            openHelper.writableDatabase.run {
                if (dropNewColumns) {
                    execSQL("ALTER TABLE NftItemRecord DROP COLUMN inSale")
                    execSQL("ALTER TABLE NftItemRecord DROP COLUMN lottieUrl")
                }
                version = 125
            }
            close()
        }
    }

    /** Room validates the schema against the entity when the database opens. */
    private suspend fun readItems(): List<NftItemRecord> {
        val database = buildAppDatabase(context)
        return try {
            database.nftItemDao().observe(ACCOUNT_ID, listOf(OWNER)).first()
        } finally {
            database.close()
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
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
            trust = NftTrust.Whitelist,
            verified = true,
            imageUrl = null,
            largeImageUrl = null,
        )
    }
}
