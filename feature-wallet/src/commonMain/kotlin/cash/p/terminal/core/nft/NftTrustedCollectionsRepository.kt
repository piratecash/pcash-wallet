package cash.p.terminal.core.nft

import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address

private const val ENTRY_SEPARATOR = '|'

private val BUILT_IN = listOf(
    BlockchainType.Ton to "EQBlPHLRW1w9NfKaakkYwJ8dmQVqexVozklmGVclUaUlQ4eQ", // PirateCash Treasure Seed Coins
)

/** Collections we vouch for: their NFTs count as whitelisted whatever the provider says. */
class NftTrustedCollectionsRepository(storage: BalanceSettingsStorage) {
    private val trusted: Set<NftCollectionPrefKey> =
        BUILT_IN.mapNotNull { (type, address) -> canonicalKey(type, address) }.toSet() +
            storage.trustedNftCollections.mapNotNull { entry ->
                canonicalKey(
                    BlockchainType.fromUid(entry.substringBefore(ENTRY_SEPARATOR)),
                    entry.substringAfter(ENTRY_SEPARATOR, ""),
                )
            }

    fun isTrusted(blockchainType: BlockchainType, collectionAddress: String?): Boolean =
        collectionAddress?.let { canonicalKey(blockchainType, it) }?.let { it in trusted } == true
}

/** The form the TON kit writes into `NftItemRecord.collectionAddress`; only TON has NFTs here. */
private fun canonicalKey(blockchainType: BlockchainType, address: String): NftCollectionPrefKey? = when (blockchainType) {
    BlockchainType.Ton -> tryOrNull { Address.parse(address).toUserFriendly(bounceable = true) }
        ?.let { NftCollectionPrefKey(blockchainType, it) }
    else -> null
}
