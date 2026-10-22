package cash.p.terminal.shared.desktop

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.policy.PlatformChainSupport
import io.horizontalsystems.core.entities.BlockchainType

/** Every chain runs on desktop, except through a hardware wallet. */
object DesktopPlatformChainSupport : PlatformChainSupport {
    override fun isSupported(blockchainType: BlockchainType) = true

    override fun isSupported(account: Account) = !account.isHardwareWalletAccount
}
