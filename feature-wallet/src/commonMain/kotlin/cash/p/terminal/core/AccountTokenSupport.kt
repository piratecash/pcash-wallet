package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.isHardwareWalletType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.policy.PlatformChainSupport

/** Whether the coin manager lets this account add the token. */
class AccountTokenSupport(
    private val platformChainSupport: PlatformChainSupport,
    private val hardwareWalletTokenPolicy: HardwareWalletTokenPolicy,
) {
    fun supports(token: Token, accountType: AccountType): Boolean =
        platformChainSupport.isSupported(token.blockchainType) &&
            (!accountType.isHardwareWalletType || hardwareWalletTokenPolicy.isSupported(token)) &&
            token.blockchainType.supports(accountType) &&
            token.supports(accountType)
}
