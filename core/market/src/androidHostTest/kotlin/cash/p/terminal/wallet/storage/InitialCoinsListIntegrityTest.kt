package cash.p.terminal.wallet.storage

import cash.p.terminal.wallet.syncers.CoinsListMapper
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InitialCoinsListIntegrityTest {

    @Test
    fun initialCoinsList_committedAsset_isValidSql() {
        validateDumpSql(initialCoinsFile().readText())
    }

    @Test
    fun initialCoinsList_everyTonRowHasW5Twin() {
        val tonTokens = withDumpDatabase(initialCoinsFile().readText()) { database ->
            database.rawQuery(
                "SELECT coinUid, blockchainUid, type, decimals, reference FROM Token WHERE blockchainUid = ?",
                arrayOf(BlockchainType.Ton.uid)
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(
                            TokenRecord(
                                coinUid = cursor.getString(0),
                                blockchainUid = cursor.getString(1),
                                type = cursor.getString(2),
                                decimals = if (cursor.isNull(3)) null else cursor.getInt(3),
                                reference = cursor.getString(4),
                            )
                        )
                    }
                }
            }
        }

        assertTrue("No TON W5 rows in the dump", tonTokens.any { it.type == "ton_w5_native" })
        assertEquals(tonTokens, CoinsListMapper().twinTonTokens(tonTokens.toList()).toSet())
    }
}
