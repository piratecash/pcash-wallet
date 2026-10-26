package cash.p.terminal.network.addresscheck.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HashDitTransactionSecurityRequest(
    val chainId: Int,
    val to: String,
    val from: String,
    val data: String,
)

@Serializable
data class HashDitTransactionSecurityResponse(val data: Data) {
    @Serializable
    data class Data(@SerialName("risk_level") val riskLevel: Int)
}

@Serializable
data class ChainalysisAddressResponse(val identifications: List<Identification>) {
    @Serializable
    data class Identification(
        val category: String? = null,
        val name: String? = null,
        val description: String? = null,
        val url: String? = null,
    )
}
