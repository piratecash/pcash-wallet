package cash.p.terminal.modules.walletconnect

import com.reown.walletkit.client.Wallet

sealed interface WCVerifyAlert {
    data object Scam : WCVerifyAlert
    data class Impersonation(val origin: String?) : WCVerifyAlert
    data object Unverified : WCVerifyAlert
}

/**
 * The dApp's name and url are self-reported; Verify API attests where the connection really came from.
 * Scam is checked first because a known scam domain still validates against its own metadata.
 */
fun Wallet.Model.VerifyContext.toAlert(reportUnverified: Boolean): WCVerifyAlert? = when {
    isScam == true -> WCVerifyAlert.Scam
    validation == Wallet.Model.Validation.INVALID -> WCVerifyAlert.Impersonation(origin.takeIf { it.isNotBlank() })
    reportUnverified && validation == Wallet.Model.Validation.UNKNOWN -> WCVerifyAlert.Unverified
    else -> null
}
