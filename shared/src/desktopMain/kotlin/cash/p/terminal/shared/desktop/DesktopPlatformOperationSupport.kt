package cash.p.terminal.shared.desktop

import cash.p.terminal.wallet.policy.PlatformOperation
import cash.p.terminal.wallet.policy.PlatformOperationSupport

/** Everything but [PlatformOperation.MoneroSendPreparation], which nothing reads. */
object DesktopPlatformOperationSupport : PlatformOperationSupport {
    private val supported = setOf(
        PlatformOperation.Send,
        PlatformOperation.Swap,
        PlatformOperation.SpeedUpCancel,
        PlatformOperation.Resend,
        PlatformOperation.ZcashMigration,
    )

    override fun supports(operation: PlatformOperation) = operation in supported
}
