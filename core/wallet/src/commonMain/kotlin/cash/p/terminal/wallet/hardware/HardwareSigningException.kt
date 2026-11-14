package cash.p.terminal.wallet.hardware

open class HardwareSigningException(message: String) : Exception(message)

open class HardwareCancelledException(message: String) : HardwareSigningException(message)
