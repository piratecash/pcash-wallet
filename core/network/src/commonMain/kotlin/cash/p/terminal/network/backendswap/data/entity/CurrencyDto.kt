package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class CurrencyDto(
    val ticker: String,
    val coinId: String,
    val blockchain: String,
    val contractAddress: String? = null,
)
