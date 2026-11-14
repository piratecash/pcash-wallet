package cash.p.terminal.core.nft

import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
import io.horizontalsystems.core.entities.BlockchainType

enum class NftCollectionState { Visible, Hidden, SpamByUser, SpamByProvider }

data class NftCollectionPrefKey(val blockchainType: BlockchainType, val collectionKey: String)

/** An NFT outside any collection is managed on its own. */
val NftItemRecord.collectionKey: String
    get() = collectionAddress ?: address

val NftItemRecord.collectionPrefKey: NftCollectionPrefKey
    get() = NftCollectionPrefKey(blockchainType, collectionKey)

val NftCollectionPrefRecord.key: NftCollectionPrefKey
    get() = NftCollectionPrefKey(blockchainType, collectionKey)

/** A user's "not spam" outranks the provider's blacklist; a user's "spam" outranks everything. */
fun nftCollectionState(trust: NftTrust, pref: NftCollectionPrefRecord?): NftCollectionState = when {
    pref?.userTrust == NftUserTrust.Trust -> if (pref.hidden) NftCollectionState.Hidden else NftCollectionState.Visible
    pref?.userTrust == NftUserTrust.Spam -> NftCollectionState.SpamByUser
    trust == NftTrust.Blacklist -> NftCollectionState.SpamByProvider
    pref?.hidden == true -> NftCollectionState.Hidden
    else -> NftCollectionState.Visible
}

fun NftItemRecord.withUserTrust(pref: NftCollectionPrefRecord?): NftItemRecord =
    if (pref?.userTrust == NftUserTrust.Trust) copy(trust = NftTrust.Whitelist) else this
