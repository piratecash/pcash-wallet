package cash.p.terminal.network.yifi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class YiFiTransactionDto(
    val status: String,
    val receiveAmount: String? = null,
    val completionTime: String? = null,
)
