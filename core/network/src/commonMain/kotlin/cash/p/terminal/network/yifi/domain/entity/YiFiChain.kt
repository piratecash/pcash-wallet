package cash.p.terminal.network.yifi.domain.entity

data class YiFiChain(
    val id: String,
    val chainId: Long?,
    val aliases: List<String>,
    val nativeToken: String,
)
