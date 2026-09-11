package cash.p.terminal.feature.logging.domain.usecase

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Use case for retrieving wallets that have ZEC (Zcash) tokens enabled.
 * Used for SMS notification configuration where only ZEC wallets are valid options.
 *
 * Only returns wallets with Shielded or Unified address types.
 * Watch-only accounts are excluded: the duress notification sends a real transaction.
 */
class GetZecWalletsUseCase(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager
) {
    /**
     * Returns all wallets that have ZEC (Zcash) tokens with Shielded or Unified address types.
     */
    suspend fun getZecWallets(): List<Wallet> {
        return accountManager.accounts
            .filter { account ->
                val type = account.type
                type is AccountType.Mnemonic ||
                        (type is AccountType.ZCashSaplingKey && type.isSpendingKey)
            }
            .flatMap { account ->
                walletManager.getWallets(account)
            }.filter { wallet ->
                wallet.token.blockchainType == BlockchainType.Zcash &&
                        isShieldedOrUnified(wallet.token.type)
            }
    }

    private fun isShieldedOrUnified(tokenType: TokenType): Boolean {
        return when (tokenType) {
            is TokenType.AddressSpecTyped -> {
                tokenType.type == TokenType.AddressSpecType.Shielded ||
                        tokenType.type == TokenType.AddressSpecType.Unified
            }

            else -> false
        }
    }
}
