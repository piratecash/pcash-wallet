package cash.p.terminal.wallet.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDatabaseTest {

    @Test
    fun loadInitialCoins_initialCoinList_returnsExecutedLineCount() {
        val context = mockk<Context>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val file = initialCoinsFile()
        val inputStream = file.inputStream()

        every { context.assets.open("initial_coins_list") } returns inputStream

        val marketDatabase = MarketDatabase.Companion
        val count = marketDatabase.loadInitialCoins(db, context)

        assertEquals(file.readLines().size, count)
    }

    @Test
    fun initialCoins_litecoin_containsMwebToken() {
        val file = initialCoinsFile()
        // Rows are batched into multi-row INSERT statements (see DumpManager.CHUNK_SIZE), so the
        // tuple is checked as a substring rather than expecting it to be a whole statement/line.
        val mwebTokenTuple = "('litecoin','litecoin','mweb',8,'')"

        assertTrue(file.readText().contains(mwebTokenTuple))
    }

    @Test
    fun initialCoins_beam_keepsNativeAndGameTokensSeparate() {
        val text = initialCoinsFile().readText()
        // Rows are batched into multi-row INSERT statements, so each token is matched as a tuple.
        val beamCoinTuples = Regex("""\('beam','[^']*','[^']*',\d*,'[^']*'\)""")
            .findAll(text).map { it.value }.toList()
        val gameContract = "0x62d0a8458ed7719fdaf978fe5929c6d342b0bfce"

        assertTrue(beamCoinTuples.contains("('beam','beam','native',8,'')"))
        assertTrue(beamCoinTuples.none { it.contains(gameContract) })
        assertTrue(text.contains("('beam-2','ethereum','eip20',18,'$gameContract')"))
        assertTrue(text.contains("('beam-2','binance-smart-chain','eip20',18,'$gameContract')"))
    }

}
