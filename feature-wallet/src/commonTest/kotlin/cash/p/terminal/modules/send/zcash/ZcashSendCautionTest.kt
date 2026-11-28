package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.LocalizedException
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.resources.zcash_server_unavailable
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.zcash.ZcashException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class ZcashSendCautionTest {

    @Test
    fun zcashSendCaution_networkFailure_showsServerUnavailable() {
        val caution = zcashSendCaution(ZcashException("transport error: connection reset"))

        assertEquals(Res.string.zcash_server_unavailable, (caution.s as TranslatableString.ResString).id)
    }

    @Test
    fun zcashSendCaution_unknownHost_showsNoInternet() {
        val caution = zcashSendCaution(UnknownHostException("zec.rocks"))

        assertEquals(Res.string.Hud_Text_NoInternet, (caution.s as TranslatableString.ResString).id)
    }

    @Test
    fun zcashSendCaution_localizedException_showsItsText() {
        val caution = zcashSendCaution(LocalizedException(Res.string.Swap_ErrorInsufficientBalance))

        assertEquals(Res.string.Swap_ErrorInsufficientBalance, (caution.s as TranslatableString.ResString).id)
    }

    @Test
    fun zcashSendCaution_otherFailure_showsItsMessage() {
        val caution = zcashSendCaution(ZcashException("No feasible note selection found"))

        assertEquals("No feasible note selection found", (caution.s as TranslatableString.PlainString).text)
    }
}
