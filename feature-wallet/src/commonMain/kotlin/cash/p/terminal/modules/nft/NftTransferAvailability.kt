package cash.p.terminal.modules.nft

import cash.p.terminal.core.nft.DOMAIN_EXPIRY_LOOKUP_DAYS
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.owner
import cash.p.terminal.entities.NftItemRecord
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
    private val walletResolver: NftOwnerWalletResolver,
    private val repository: NftCollectionsRepository,
) {
    suspend fun check(item: NftItemRecord): NftTransferAvailability {
        val owner = item.owner
        val wallet = walletResolver.wallet(owner) ?: return NftTransferAvailability.Hidden
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

    /** Null when the item is not a domain that expires or the expiry cannot be read now. */
    suspend fun domainExpiry(item: NftItemRecord): NftDomainExpiry? {
        val owner = item.owner
        return repository.expiringDomains(setOf(owner), DOMAIN_EXPIRY_LOOKUP_DAYS)
            ?.firstOrNull { it.nftAddress == item.address }
    }

    private fun unavailable(reason: NftTransferUnavailableReason) = NftTransferAvailability.Unavailable(reason)
}
