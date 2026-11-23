package cash.p.terminal.modules.signmessage

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat.Bip322
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat.Standard

/** Formats offered for a BTC wallet, default first; must stay within what the kit signs. */
object SignMessageFormats {

    fun available(accountType: AccountType, derivation: TokenType.Derivation): List<MessageSignatureFormat> =
        when (derivation) {
            TokenType.Derivation.Bip44, TokenType.Derivation.Bip49 -> listOf(Standard)
            TokenType.Derivation.Bip84 -> if (accountType.isTrezor) listOf(Standard) else listOf(Standard, Bip322)
            // Trezor firmware does not sign messages for Taproot.
            TokenType.Derivation.Bip86 -> if (accountType.isTrezor) emptyList() else listOf(Bip322)
        }

    private val AccountType.isTrezor: Boolean
        get() = this is AccountType.TrezorDevice
}
