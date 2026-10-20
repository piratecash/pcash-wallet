package cash.p.terminal.modules.address

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SwapSettings_Error_InvalidAddress
import cash.p.terminal.resources.SwapSettings_Error_InvalidBlockchainAddress
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
sealed class AddressValidationException : Exception(), CommonParcelable {
    object Blank : AddressValidationException()
    class Unsupported(val blockchain: String? = null) : AddressValidationException()
    class Invalid(override val cause: Throwable, val blockchain: String? = null) : AddressValidationException()

    private val blockchain: String?
        get() = when (this) {
            is Blank -> null
            is Invalid -> blockchain
            is Unsupported -> blockchain
        }

    override fun getLocalizedMessage() = when (val blockchainName = blockchain) {
        null -> Translator.getString(Res.string.SwapSettings_Error_InvalidAddress)
        else -> Translator.getString(Res.string.SwapSettings_Error_InvalidBlockchainAddress, blockchainName)
    }
}
