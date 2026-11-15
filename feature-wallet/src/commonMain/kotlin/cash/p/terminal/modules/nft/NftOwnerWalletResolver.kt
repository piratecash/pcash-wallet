package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet

class NftOwnerWalletResolver(
    private val walletManager: IWalletManager,
    private val repository: NftCollectionsRepository,
) {
    /** The active wallet whose NFT owner address is [owner]; null when that wallet is not active. */
    suspend fun wallet(owner: NftOwner): Wallet? = walletManager.activeWallets
        .filter { it.account.id == owner.accountId && it.token.blockchainType == owner.blockchainType }
        .filter(repository::supportsSection)
        .firstOrNull { repository.ownerFor(it) == owner }
}
