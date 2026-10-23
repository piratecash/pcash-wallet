package cash.p.terminal.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_118_119Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate118To119_zeroAmountOutReal_clearedOthersKept() = runTest {
        val amounts = listOf(BigDecimal("0"), BigDecimal("0.00"), BigDecimal("18159.295232"), null)
        buildAppDatabase(context).run {
            amounts.forEachIndexed { index, amount ->
                swapProviderTransactionsDao().insert(swap(index.toLong(), amount))
            }
            openHelper.writableDatabase.version = 118
            close()
        }

        val database = buildAppDatabase(context)
        try {
            val dao = database.swapProviderTransactionsDao()
            val migrated = amounts.indices.map { requireNotNull(dao.getByDate(it.toLong())).amountOutReal }
            assertEquals(listOf(null, null, BigDecimal("18159.295232"), null), migrated)
        } finally {
            database.close()
        }
    }

    private fun swap(date: Long, amountOutReal: BigDecimal?) = SwapProviderTransaction(
        date = date,
        outgoingRecordUid = null,
        transactionId = "tx-$date",
        status = "finished",
        provider = SwapProvider.PCASH_BACKEND,
        coinUidIn = "ethereum",
        blockchainTypeIn = BlockchainType.Ethereum.uid,
        amountIn = BigDecimal.ONE,
        addressIn = "0xIn",
        coinUidOut = "tron",
        blockchainTypeOut = BlockchainType.Tron.uid,
        amountOut = BigDecimal("18159.295232"),
        addressOut = "TAddressOut",
        amountOutReal = amountOutReal,
    )
}
