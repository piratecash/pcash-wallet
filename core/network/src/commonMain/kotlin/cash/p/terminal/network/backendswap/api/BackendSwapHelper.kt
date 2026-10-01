package cash.p.terminal.network.backendswap.api

object BackendSwapHelper {
    private const val CHANGELLY = "changelly"

    fun trackUrl(providerName: String?, externalId: String?): Pair<String, String>? {
        if (externalId.isNullOrBlank()) return null
        return when (providerName) {
            CHANGELLY -> "changelly.com" to "https://changelly.com/track/$externalId"
            else -> null
        }
    }
}
