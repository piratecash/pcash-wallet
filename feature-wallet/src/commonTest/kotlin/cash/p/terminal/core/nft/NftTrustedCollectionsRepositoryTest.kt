package cash.p.terminal.core.nft

import cash.p.terminal.core.BalanceSettingsStorage
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NftTrustedCollectionsRepositoryTest {

    private fun repository(vararg storedEntries: String) = NftTrustedCollectionsRepository(
        mockk<BalanceSettingsStorage> { every { trustedNftCollections } returns storedEntries.toSet() },
    )

    @Test
    fun isTrusted_builtInCollection_trusted() {
        assertTrue(repository().isTrusted(BlockchainType.Ton, BUILT_IN_COLLECTION))
    }

    @Test
    fun isTrusted_storedCollectionInAnyAddressForm_trusted() {
        val repository = repository("the-open-network|$STORED_COLLECTION_RAW")

        assertTrue(repository.isTrusted(BlockchainType.Ton, STORED_COLLECTION))
        assertTrue(repository.isTrusted(BlockchainType.Ton, STORED_COLLECTION_NON_BOUNCEABLE))
    }

    @Test
    fun isTrusted_unknownOrMissingCollection_notTrusted() {
        val repository = repository("the-open-network|$STORED_COLLECTION")

        assertFalse(repository.isTrusted(BlockchainType.Ton, OTHER_COLLECTION))
        assertFalse(repository.isTrusted(BlockchainType.Ton, null))
        assertFalse(repository.isTrusted(BlockchainType.Ethereum, STORED_COLLECTION))
    }

    @Test
    fun isTrusted_malformedStoredEntries_ignored() {
        val repository = repository("ethereum|$OTHER_COLLECTION", "the-open-network|not-an-address", "garbage")

        assertFalse(repository.isTrusted(BlockchainType.Ton, OTHER_COLLECTION))
        assertTrue(repository.isTrusted(BlockchainType.Ton, BUILT_IN_COLLECTION))
    }

    private companion object {
        const val BUILT_IN_COLLECTION = "0:653c72d15b5c3d35f29a6a4918c09f1d99056a7b1568ce496619572551a52543"
        val STORED_COLLECTION_RAW = "0:" + "34".repeat(32)
        val STORED_COLLECTION: String = Address.parse(STORED_COLLECTION_RAW).toUserFriendly(bounceable = true)
        val STORED_COLLECTION_NON_BOUNCEABLE: String =
            Address.parse(STORED_COLLECTION_RAW).toUserFriendly(bounceable = false)
        val OTHER_COLLECTION: String = Address.parse("0:" + "56".repeat(32)).toUserFriendly(bounceable = true)
    }
}
