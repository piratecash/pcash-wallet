package cash.p.terminal.network.backendswap.domain.entity

data class BackendSwapProviderInfo(
    val name: String,
    val displayName: String,
    val logoUrl: String?,
    val active: Boolean,
    val supportsFixed: Boolean,
    val supportsFloat: Boolean,
)
