package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType

suspend fun Iterable<AutoEnableTokenInfo>.toEnabledWallets(
    accountId: String,
    blockchainType: BlockchainType,
    userDeletedWalletManager: UserDeletedWalletManager,
): List<EnabledWallet> = mapNotNull { tokenInfo ->
    val tokenQueryId = TokenQuery(blockchainType, tokenInfo.type).id
    if (userDeletedWalletManager.isDeletedByUser(accountId, tokenQueryId)) {
        return@mapNotNull null
    }
    EnabledWallet(
        tokenQueryId = tokenQueryId,
        accountId = accountId,
        coinName = tokenInfo.coinName,
        coinCode = tokenInfo.coinCode,
        coinDecimals = tokenInfo.coinDecimals,
        coinImage = tokenInfo.coinImage,
    )
}
