package cash.p.terminal.trezor.domain

import cash.p.terminal.wallet.hardware.HardwareCancelledException
import cash.p.terminal.wallet.hardware.HardwareSigningException

open class TrezorSigningException(message: String) : HardwareSigningException(message)

class TrezorCancelledException : HardwareCancelledException(
    "Trezor operation cancelled by user"
)
