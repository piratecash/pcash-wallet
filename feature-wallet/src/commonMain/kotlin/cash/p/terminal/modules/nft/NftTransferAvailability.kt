package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.supportsTonConnect

enum class NftTransferUnavailableReason { OnSale, WatchOnly, NotOwner, CheckFailed }

sealed interface NftTransferAvailability {
    data object Hidden : NftTransferAvailability
    data object Loading : NftTransferAvailability

    // Not a data class: Wallet.toString() would print key material.
    class Available(val wallet: Wallet, val domain: String?) : NftTransferAvailability

    data class Unavailable(val reason: NftTransferUnavailableReason) : NftTransferAvailability
}

class NftTransferAvailabilityProvider(
    private val walletManager: IWalletManager,
    private val repository: NftCollectionsRepository,
) {
    suspend fun check(item: NftItemRecord): NftTransferAvailability {
        val (wallet, owner) = ownerWallet(item) ?: return NftTransferAvailability.Hidden
        if (wallet.account.isWatchAccount) return unavailable(NftTransferUnavailableReason.WatchOnly)
        if (!wallet.account.supportsTonConnect()) return NftTransferAvailability.Hidden

        val live = repository.liveState(owner, item.address)
            ?: return unavailable(NftTransferUnavailableReason.CheckFailed)
        return when {
            live.isOnSale -> unavailable(NftTransferUnavailableReason.OnSale)
            live.ownerAddress != owner.address -> unavailable(NftTransferUnavailableReason.NotOwner)
            else -> NftTransferAvailability.Available(wallet, live.domain)
        }
    }

    private suspend fun ownerWallet(item: NftItemRecord): Pair<Wallet, NftOwner>? = walletManager.activeWallets
        .filter { it.account.id == item.accountId && it.token.blockchainType == item.blockchainType }
        .filter(repository::supportsSection)
        .firstNotNullOfOrNull { wallet ->
            repository.ownerFor(wallet)?.takeIf { it.address == item.ownerAddress }?.let { wallet to it }
        }

    private fun unavailable(reason: NftTransferUnavailableReason) = NftTransferAvailability.Unavailable(reason)
}
