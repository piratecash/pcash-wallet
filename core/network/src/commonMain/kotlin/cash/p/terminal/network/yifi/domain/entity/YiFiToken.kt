package cash.p.terminal.network.yifi.domain.entity

data class YiFiToken(
    val network: String,
    val ticker: String,
    val contractAddress: String?,
)
