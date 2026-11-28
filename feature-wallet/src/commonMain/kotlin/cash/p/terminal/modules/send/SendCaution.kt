package cash.p.terminal.modules.send

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.swap_insufficient_fee_balance
import cash.p.terminal.strings.helpers.TranslatableString
import java.net.UnknownHostException

fun Throwable.toSendCaution(): HSCaution = when (this) {
    is UnknownHostException -> HSCaution(TranslatableString.ResString(Res.string.Hud_Text_NoInternet))
    is LocalizedException -> HSCaution(TranslatableString.ResString(errorTextRes))
    else -> HSCaution(TranslatableString.PlainString(message ?: ""))
}

/** The exact text names the balance, so a hidden balance gets the generic one. */
fun TranslatableString.orHiddenBalanceText(balanceHidden: Boolean, coinCode: String): TranslatableString =
    if (balanceHidden) TranslatableString.ResString(Res.string.swap_insufficient_fee_balance, coinCode) else this
