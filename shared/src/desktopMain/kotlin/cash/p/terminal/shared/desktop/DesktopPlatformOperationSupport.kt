package cash.p.terminal.shared.desktop

import cash.p.terminal.wallet.policy.PlatformOperation
import cash.p.terminal.wallet.policy.PlatformOperationSupport

/** Sending and swapping are separate desktop tasks; until then their buttons only say so. */
object DesktopPlatformOperationSupport : PlatformOperationSupport {
    override fun supports(operation: PlatformOperation) = false
}
