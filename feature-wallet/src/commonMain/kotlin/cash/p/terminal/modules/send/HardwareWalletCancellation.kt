package cash.p.terminal.modules.send

import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.core.HSCaution
import cash.p.terminal.ledger.ui.ledgerErrorMessageOrNull
import cash.p.terminal.ledger.ui.ledgerErrorResOrNull
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ledger_monero_confirm_view_key
import cash.p.terminal.resources.ledger_signing_failed
import cash.p.terminal.resources.ledger_ton_single_message
import cash.p.terminal.resources.trezor_connect_failed
import cash.p.terminal.resources.trezor_not_initialized_description
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.hardware.HardwareCancelledException
import cash.p.terminal.wallet.hardware.HardwareSigningException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.Translator
import io.horizontalsystems.tonkit.core.TonExternalSignerException
import org.jetbrains.compose.resources.StringResource

/** True when [this] is a user-initiated cancellation on any supported hardware wallet (Trezor or Tangem). */
fun Throwable.isHardwareWalletCancelled(): Boolean =
    this is HardwareCancelledException ||
        this is HardwareWalletOperationException && error == HardwareWalletErrorCode.Cancelled ||
        HardwareWalletSupport.isUserCancellation(this)

fun HardwareWalletOperationException.userMessageRes(): StringResource = when (error) {
    HardwareWalletErrorCode.DeviceNotInitialized -> Res.string.trezor_not_initialized_description
    HardwareWalletErrorCode.Network -> Res.string.Hud_Text_NoInternet
    HardwareWalletErrorCode.ViewKeyExportDenied -> Res.string.ledger_monero_confirm_view_key
    else -> Res.string.trezor_connect_failed
}

fun Throwable.hardwareWalletUserMessageRes(): StringResource =
    (this as? HardwareWalletOperationException)?.userMessageRes()
        ?: ledgerErrorResOrNull(this)
        ?: Res.string.trezor_connect_failed

/** The text of a failure a hardware wallet raised, or null when [this] did not come from one. */
fun Throwable.hardwareWalletErrorMessage(): String? {
    ledgerErrorMessageOrNull(this)?.let { return it }
    tonDeviceSigningMessageRes()?.let { return Translator.getString(it) }
    if (this !is HardwareWalletOperationException && this !is HardwareSigningException) return null
    return Translator.getString(hardwareWalletUserMessageRes())
}

/** A caution with the hardware wallet's reason, or null when [this] did not come from one. */
fun Throwable.hardwareWalletCaution(): HSCaution? =
    hardwareWalletErrorMessage()?.let { HSCaution(TranslatableString.PlainString(it)) }

/** What to show for a failed signing: nothing for a cancellation, else the device's reason or [fallback]. */
fun Throwable.signingErrorText(fallback: String): String? =
    if (isHardwareWalletCancelled()) null else hardwareWalletErrorMessage() ?: fallback

// ton-kit raises these around a device signature; only a Ledger account signs TON through it.
private fun Throwable.tonDeviceSigningMessageRes(): StringResource? = when (this) {
    is TonExternalSignerException.UnsupportedMessageCount -> Res.string.ledger_ton_single_message
    is TonExternalSignerException.SignedHashMismatch -> Res.string.ledger_signing_failed
    else -> null
}
