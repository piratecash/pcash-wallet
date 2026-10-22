package cash.p.terminal.wallet.policy

/** User operations a platform may not offer yet; their controls stay visible and say so. */
enum class PlatformOperation {
    Send,
    Swap,
    SpeedUpCancel,
    Resend,
    ZcashMigration,
    MoneroSendPreparation,
}

interface PlatformOperationSupport {
    fun supports(operation: PlatformOperation): Boolean
}
