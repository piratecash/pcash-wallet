package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class ProviderInfoDto(
    val name: String,
    val displayName: String,
    val logo: ProviderLogoDto? = null,
    val active: Boolean = false,
    val exchangeTypes: ExchangeTypesDto? = null,
)

@Serializable
internal data class ProviderLogoDto(
    val url: String? = null,
)

@Serializable
internal data class ExchangeTypesDto(
    val fixed: Boolean = false,
    val float: Boolean = false,
)
