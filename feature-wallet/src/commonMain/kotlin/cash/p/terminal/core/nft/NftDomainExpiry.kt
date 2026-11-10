package cash.p.terminal.core.nft

import kotlin.math.max

const val DOMAIN_RENEW_SOON_DAYS = 30
const val DOMAIN_EXPIRY_LOOKUP_DAYS = 366
private const val DOMAIN_RENEW_URGENT_DAYS = 7
private const val SECONDS_PER_DAY = 86_400L

data class NftDomainExpiry(
    val owner: NftOwner,
    val nftAddress: String,
    val name: String,
    /** Epoch seconds. */
    val expiresAt: Long,
    val isOnSale: Boolean,
    val imageUrl: String? = null,
)

enum class DomainExpiryLevel { Normal, Soon, Urgent }

fun NftDomainExpiry.daysLeft(nowSeconds: Long): Int = max(0L, (expiresAt - nowSeconds) / SECONDS_PER_DAY).toInt()

fun domainExpiryLevel(daysLeft: Int): DomainExpiryLevel = when {
    daysLeft <= DOMAIN_RENEW_URGENT_DAYS -> DomainExpiryLevel.Urgent
    daysLeft <= DOMAIN_RENEW_SOON_DAYS -> DomainExpiryLevel.Soon
    else -> DomainExpiryLevel.Normal
}
