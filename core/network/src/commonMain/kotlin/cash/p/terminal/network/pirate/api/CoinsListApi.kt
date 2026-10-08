package cash.p.terminal.network.pirate.api

import cash.p.terminal.network.api.parseResponse
import cash.p.terminal.network.pirate.data.entity.CoinsListDto
import cash.p.terminal.network.pirate.data.entity.CoinsUpdatesDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders

internal class CoinsListApi(
    private val httpClient: HttpClient,
) {
    private companion object {
        const val COINS_LIST_URL = "$PCASH_BASE_URL/v2/coins/list"
        const val COIN_RANKS_URL = "$PCASH_BASE_URL/v2/coins/ranks"
        const val COINS_UPDATES_URL = "$PCASH_BASE_URL/v2/coins/updates"
        const val CACHE_CONTROL_NO_CACHE = "no-cache, no-store, must-revalidate"
    }

    suspend fun coinsList(): CoinsListDto = getNoCache(COINS_LIST_URL)

    suspend fun coinRanks(): Map<String, Int> = getNoCache(COIN_RANKS_URL)

    suspend fun coinsUpdates(): CoinsUpdatesDto = getNoCache(COINS_UPDATES_URL)

    private suspend inline fun <reified T : Any> getNoCache(requestUrl: String): T {
        return httpClient.get {
            url(requestUrl)
            header(HttpHeaders.CacheControl, CACHE_CONTROL_NO_CACHE)
        }.parseResponse()
    }
}
