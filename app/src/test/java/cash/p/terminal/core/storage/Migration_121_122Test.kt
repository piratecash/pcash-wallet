package cash.p.terminal.core.storage

import android.content.Context
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
class Migration_121_122Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate121To122_pairingMetadataTable_droppedOtherDataKept() {
        buildAppDatabase(context).run {
            openHelper.writableDatabase.run {
                execSQL(
                    "INSERT INTO AccountRecord (id, name, type, origin, isBackedUp, isFileBackedUp, level, deleted) " +
                        "VALUES ('$ACCOUNT_ID', 'Seeded', 'mnemonic', 'Created', 1, 0, 0, 0)"
                )
                execSQL(
                    "CREATE TABLE IF NOT EXISTS `WalletConnectV2PairingMetadata` " +
                        "(`topic` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`icon` TEXT, PRIMARY KEY(`topic`))"
                )
                execSQL(
                    "INSERT INTO `WalletConnectV2PairingMetadata` (`topic`, `name`, `url`, `icon`) " +
                        "VALUES ('topic-1', 'PancakeSwap', 'https://pancakeswap.finance', NULL)"
                )
                version = 121
            }
            close()
        }

        val database = buildAppDatabase(context)
        try {
            database.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='WalletConnectV2PairingMetadata'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }

            assertEquals(listOf(ACCOUNT_ID), database.accountsDao().getIds())
        } finally {
            database.close()
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
    }
}
