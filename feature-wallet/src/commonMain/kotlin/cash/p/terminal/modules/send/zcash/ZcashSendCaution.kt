package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.adapters.zcash.isZcashNetworkFailure
import cash.p.terminal.modules.send.hardwareWalletCaution
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.resources.zcash_server_unavailable
import cash.p.terminal.strings.helpers.TranslatableString
import java.net.UnknownHostException

fun zcashInsufficientBalanceCaution() =
    HSCaution(TranslatableString.ResString(Res.string.Swap_ErrorInsufficientBalance))

fun zcashSendCaution(error: Throwable) = error.hardwareWalletCaution() ?: when (error) {
    is UnknownHostException -> HSCaution(TranslatableString.ResString(Res.string.Hud_Text_NoInternet))
    is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
    else -> if (error.isZcashNetworkFailure()) {
        HSCaution(TranslatableString.ResString(Res.string.zcash_server_unavailable))
    } else {
        HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }
}
