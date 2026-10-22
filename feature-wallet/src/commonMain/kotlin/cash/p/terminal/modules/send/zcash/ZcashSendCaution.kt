package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import java.net.UnknownHostException

fun zcashSendCaution(error: Throwable) = when (error) {
    is UnknownHostException -> HSCaution(TranslatableString.ResString(Res.string.Hud_Text_NoInternet))
    is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
    else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
}
