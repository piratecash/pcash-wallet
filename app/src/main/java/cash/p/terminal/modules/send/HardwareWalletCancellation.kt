package cash.p.terminal.modules.send

import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.trezor_connect_failed
import cash.p.terminal.resources.trezor_not_initialized_description
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.trezor.domain.TrezorCancelledException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import org.jetbrains.compose.resources.StringResource

/** True when [this] is a user-initiated cancellation on any supported hardware wallet (Trezor or Tangem). */
fun Throwable.isHardwareWalletCancelled(): Boolean =
    this is TrezorCancelledException ||
        this is HardwareWalletOperationException && error == HardwareWalletErrorCode.Cancelled ||
        isHardwareWalletUserCancelled()

fun HardwareWalletOperationException.userMessageRes(): StringResource = when (error) {
    HardwareWalletErrorCode.DeviceNotInitialized -> Res.string.trezor_not_initialized_description
    HardwareWalletErrorCode.Network -> Res.string.Hud_Text_NoInternet
    else -> Res.string.trezor_connect_failed
}

fun Throwable.hardwareWalletUserMessageRes(): StringResource =
    (this as? HardwareWalletOperationException)?.userMessageRes()
        ?: Res.string.trezor_connect_failed
