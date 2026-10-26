package cash.p.terminal.wallet.policy

object AndroidPlatformOperationSupport : PlatformOperationSupport {
    override fun supports(operation: PlatformOperation) = true
}
