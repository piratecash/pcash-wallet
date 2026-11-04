package cash.p.terminal.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_120_121Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate120To121_existingSwap_keptWithWritableReceivedAt() = runTest {
        buildAppDatabase(context).run {
            swapProviderTransactionsDao().insert(linkedSwap())
            openHelper.writableDatabase.run {
                execSQL("ALTER TABLE SwapProviderTransaction DROP COLUMN receivedAt")
                version = 120
            }
            close()
        }

        val database = buildAppDatabase(context)
        try {
            val dao = database.swapProviderTransactionsDao()
            val migrated = requireNotNull(dao.getByDate(SWAP_DATE))
            assertEquals(INCOMING_UID, migrated.incomingRecordUid)
            assertNull(migrated.receivedAt)

            dao.syncReceivedAt(date = SWAP_DATE, incomingRecordUid = INCOMING_UID, receivedAt = 5_000L)
            assertEquals(5_000L, dao.getByDate(SWAP_DATE)?.receivedAt)
        } finally {
            database.close()
        }
    }

    private fun linkedSwap() = SwapProviderTransaction(
        date = SWAP_DATE,
        outgoingRecordUid = null,
        transactionId = "tx-1",
        status = "finished",
        provider = SwapProvider.CHANGENOW,
        coinUidIn = "ethereum",
        blockchainTypeIn = BlockchainType.Ethereum.uid,
        amountIn = BigDecimal.ONE,
        addressIn = "0xIn",
        coinUidOut = "tron",
        blockchainTypeOut = BlockchainType.Tron.uid,
        amountOut = BigDecimal("18159.295232"),
        addressOut = "TAddressOut",
        incomingRecordUid = INCOMING_UID,
    )

    private companion object {
        const val SWAP_DATE = 1_000L
        const val INCOMING_UID = "incoming-uid"
    }
}
