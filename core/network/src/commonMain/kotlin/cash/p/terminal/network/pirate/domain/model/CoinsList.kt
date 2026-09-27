package cash.p.terminal.network.pirate.domain.model

data class CoinsList(
    val blockchains: List<RemoteBlockchain>,
    val coins: List<RemoteCoin>,
)

data class RemoteCoin(
    val coingeckoId: String,
    val name: String,
    val code: String,
    val priority: Int?,
    val tokens: List<RemoteToken>,
)

data class RemoteToken(
    val type: String,
    val blockchainUid: String,
    val address: String?,
    val decimals: Int?,
)

data class RemoteBlockchain(
    val uid: String,
    val name: String,
    val url: String?,
)
