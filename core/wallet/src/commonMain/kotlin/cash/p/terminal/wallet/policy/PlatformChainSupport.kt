package cash.p.terminal.wallet.policy

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.BlockchainType

/** Whether this platform can run the wallet's chain; an unsupported wallet stays visible but gets no adapter. */
interface PlatformChainSupport {
    fun isSupported(blockchainType: BlockchainType): Boolean

    fun isSupported(account: Account): Boolean

    fun isSupported(token: Token, account: Account): Boolean =
        isSupported(token.blockchainType) && isSupported(account)

    fun isSupported(wallet: Wallet): Boolean = isSupported(wallet.token, wallet.account)

    fun isSupported(source: TransactionSource): Boolean =
        isSupported(source.blockchain.type) && isSupported(source.account)
}
