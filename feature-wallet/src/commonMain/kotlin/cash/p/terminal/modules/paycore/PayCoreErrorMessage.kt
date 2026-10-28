package cash.p.terminal.modules.paycore

import cash.p.terminal.core.isNoInternetException
import io.horizontalsystems.core.Translator
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.paycore_generic_error
import cash.p.terminal.resources.paycore_verification_error

internal fun Throwable.payCoreUserMessage(defaultMessage: String? = null): String {
    return when {
        this is PayCoreWalletNotApprovedException -> Translator.getString(Res.string.paycore_verification_error)
        isNoInternetException() -> Translator.getString(Res.string.Hud_Text_NoInternet)
        !defaultMessage.isNullOrBlank() -> defaultMessage
        else -> Translator.getString(Res.string.paycore_generic_error)
    }
}
