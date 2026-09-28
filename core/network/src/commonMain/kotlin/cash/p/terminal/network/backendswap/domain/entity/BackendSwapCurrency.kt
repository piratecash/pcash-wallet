package cash.p.terminal.network.backendswap.domain.entity

data class BackendSwapCurrency(
    val ticker: String,
    val coinId: String,
    val blockchain: String,
    val contractAddress: String?,
)
