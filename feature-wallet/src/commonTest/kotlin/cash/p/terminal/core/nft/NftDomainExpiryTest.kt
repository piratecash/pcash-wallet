package cash.p.terminal.core.nft

import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Test

class NftDomainExpiryTest {

    @Test
    fun daysLeft_partialDay_roundsDown() {
        assertEquals(29, expiry(expiresAt = NOW + 30 * DAY - 1).daysLeft(NOW))
    }

    @Test
    fun daysLeft_pastExpiry_isZero() {
        assertEquals(0, expiry(expiresAt = NOW - 5 * DAY).daysLeft(NOW))
    }

    @Test
    fun domainExpiryLevel_tierBoundaries_matchDesign() {
        assertEquals(DomainExpiryLevel.Normal, domainExpiryLevel(31))
        assertEquals(DomainExpiryLevel.Soon, domainExpiryLevel(30))
        assertEquals(DomainExpiryLevel.Soon, domainExpiryLevel(8))
        assertEquals(DomainExpiryLevel.Urgent, domainExpiryLevel(7))
        assertEquals(DomainExpiryLevel.Urgent, domainExpiryLevel(0))
    }

    private fun expiry(expiresAt: Long) = NftDomainExpiry(
        owner = NftOwner("account", BlockchainType.Ton, "owner"),
        nftAddress = "nft",
        name = "pirate.ton",
        expiresAt = expiresAt,
        isOnSale = false,
    )

    private companion object {
        const val NOW = 1_800_000_000L
        const val DAY = 86_400L
    }
}
