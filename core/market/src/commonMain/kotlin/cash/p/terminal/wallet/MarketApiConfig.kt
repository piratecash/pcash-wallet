package cash.p.terminal.wallet

data class MarketApiConfig(
    val baseUrl: String,
    val apiKey: String,
) {
    companion object {
        /** Not a secret: the same key ships in every build and in the iOS/desktop clients. */
        const val API_KEY = "IQf1uAjkthZp1i2pYzkXFDom"

        const val PROD_BASE_URL = "https://api.blocksdecoded.com"
    }
}
