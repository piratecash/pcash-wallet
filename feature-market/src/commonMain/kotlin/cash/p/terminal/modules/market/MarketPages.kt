package cash.p.terminal.modules.market

import cash.p.terminal.navigation.HSPage

/** Pages of the Android shell that market screens open; `null` means the platform has no such page. */
interface MarketPages {
    fun markdown(url: String): HSPage?
    fun nftCollection(collectionUid: String, blockchainUid: String): HSPage?
}
