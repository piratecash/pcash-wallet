package cash.p.terminal.network.evmlabel.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EvmLabelUpdatesStatus(
    @SerialName("address_labels") val addressLabels: Long,
    @SerialName("evm_method_labels") val evmMethodLabels: Long,
)

@Serializable
data class EvmMethodLabelDto(
    @SerialName("method_id") val methodId: String,
    val label: String,
)

@Serializable
data class EvmAddressLabelDto(
    val address: String,
    val label: String,
)
