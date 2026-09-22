package cash.p.terminal.network.yifi.data.entity

import kotlinx.serialization.Serializable

class BackendYiFiResponseError(
    val code: String?,
    override val message: String?,
    val statusCode: Int,
) : Throwable() {
    override fun toString(): String {
        return "BackendYiFiResponseError(code=$code, message=$message, statusCode=$statusCode)"
    }

    companion object {
        const val RATE_EXPIRED = "RATE_EXPIRED"
        const val RATE_MISMATCH = "RATE_MISMATCH"
        const val COIN_NOT_FOUND = "COIN_NOT_FOUND"
        const val NO_SOURCES_AVAILABLE = "NO_SOURCES_AVAILABLE"
        const val NO_QUOTES_AVAILABLE = "NO_QUOTES_AVAILABLE"
    }
}

@Serializable
internal data class YiFiErrorResponseDto(
    val error: YiFiErrorDto? = null,
)

@Serializable
internal data class YiFiErrorDto(
    val code: String? = null,
    val message: String? = null,
)
