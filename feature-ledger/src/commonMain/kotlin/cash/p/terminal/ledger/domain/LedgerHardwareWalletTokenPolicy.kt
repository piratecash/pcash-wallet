package cash.p.terminal.ledger.domain

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.BlockchainType

class LedgerHardwareWalletTokenPolicy(private val fetchers: LedgerAppKeyFetchers) : HardwareWalletTokenPolicy {
    override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType): Boolean =
        fetchers.supports(TokenQuery(blockchainType, tokenType))

    override fun isSupported(account: Account, token: Token): Boolean =
        account.type is AccountType.LedgerDevice && isSupported(token)
}
