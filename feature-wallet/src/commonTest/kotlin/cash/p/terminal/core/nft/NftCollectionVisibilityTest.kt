package cash.p.terminal.core.nft

import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NftCollectionVisibilityTest {

    @Test
    fun nftCollectionState_noPref_blacklistIsProviderSpamOthersVisible() =
        assertStates(pref = null, blacklist = NftCollectionState.SpamByProvider, others = NftCollectionState.Visible)

    @Test
    fun nftCollectionState_untouchedPref_blacklistIsProviderSpamOthersVisible() = assertStates(
        pref = pref(hidden = false, userTrust = NftUserTrust.None),
        blacklist = NftCollectionState.SpamByProvider,
        others = NftCollectionState.Visible,
    )

    @Test
    fun nftCollectionState_hiddenWithoutMark_blacklistStaysProviderSpamOthersHidden() = assertStates(
        pref = pref(hidden = true, userTrust = NftUserTrust.None),
        blacklist = NftCollectionState.SpamByProvider,
        others = NftCollectionState.Hidden,
    )

    @Test
    fun nftCollectionState_markedNotSpam_allVisible() = assertStates(
        pref = pref(hidden = false, userTrust = NftUserTrust.Trust),
        blacklist = NftCollectionState.Visible,
        others = NftCollectionState.Visible,
    )

    @Test
    fun nftCollectionState_markedNotSpamAndHidden_allHidden() = assertStates(
        pref = pref(hidden = true, userTrust = NftUserTrust.Trust),
        blacklist = NftCollectionState.Hidden,
        others = NftCollectionState.Hidden,
    )

    @Test
    fun nftCollectionState_markedSpam_allUserSpam() {
        listOf(false, true).forEach { hidden ->
            assertStates(
                pref = pref(hidden = hidden, userTrust = NftUserTrust.Spam),
                blacklist = NftCollectionState.SpamByUser,
                others = NftCollectionState.SpamByUser,
            )
        }
    }

    @Test
    fun withUserTrust_markedNotSpam_becomesWhitelistAndRepeatIsNoOp() {
        val trustPref = pref(hidden = false, userTrust = NftUserTrust.Trust)

        val once = item(collectionAddress = "collection", trust = NftTrust.Blacklist).withUserTrust(trustPref)

        assertEquals(NftTrust.Whitelist, once.trust)
        assertEquals(once, once.withUserTrust(trustPref))
    }

    @Test
    fun withUserTrust_noTrustMark_returnsSameRecord() {
        val record = item(collectionAddress = "collection", trust = NftTrust.Blacklist)

        listOf(null, pref(hidden = true, userTrust = NftUserTrust.None), pref(hidden = false, userTrust = NftUserTrust.Spam))
            .forEach { assertSame(record, record.withUserTrust(it)) }
    }

    @Test
    fun collectionKey_withoutCollection_isItemAddress() {
        assertEquals("collection", item(collectionAddress = "collection").collectionKey)
        assertEquals(NFT_ADDRESS, item(collectionAddress = null).collectionKey)
    }

    private fun assertStates(pref: NftCollectionPrefRecord?, blacklist: NftCollectionState, others: NftCollectionState) {
        NftTrust.entries.forEach { trust ->
            val expected = if (trust == NftTrust.Blacklist) blacklist else others
            assertEquals("trust=$trust pref=$pref", expected, nftCollectionState(trust, pref))
        }
    }

    private fun pref(hidden: Boolean, userTrust: NftUserTrust) =
        NftCollectionPrefRecord("account", BlockchainType.Ton, "collection", hidden, userTrust)

    private fun item(collectionAddress: String?, trust: NftTrust = NftTrust.None) = NftItemRecord(
        accountId = "account",
        blockchainType = BlockchainType.Ton,
        ownerAddress = "owner",
        address = NFT_ADDRESS,
        position = 0,
        name = null,
        description = null,
        realOwnerAddress = null,
        collectionAddress = collectionAddress,
        collectionName = null,
        collectionDescription = null,
        trust = trust,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    private companion object {
        const val NFT_ADDRESS = "nft-address"
    }
}
