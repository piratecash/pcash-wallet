package cash.p.terminal.wallet.policy

import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType

object AndroidPlatformChainSupport : PlatformChainSupport {
    override fun isSupported(blockchainType: BlockchainType) = true

    override fun isSupported(account: Account) = true
}
