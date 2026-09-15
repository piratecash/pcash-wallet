package cash.p.terminal.network.yifi.api

object YiFiHelper {
    const val YIFI_URL = "yifi.io"

    fun getViewTransactionUrl(transactionId: String): String {
        return "https://yifi.io/swap/txt-$transactionId"
    }
}
