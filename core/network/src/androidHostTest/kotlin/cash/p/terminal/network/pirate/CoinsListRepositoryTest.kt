package cash.p.terminal.network.pirate

import cash.p.terminal.network.pirate.api.CoinsListApi
import cash.p.terminal.network.pirate.data.mapper.CoinsListMapper
import cash.p.terminal.network.pirate.data.repository.CoinsListRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinsListRepositoryTest {

    private val requestedUrls = mutableListOf<String>()
    private val requestedCacheControlHeaders = mutableListOf<String?>()

    private val coinsListJson = """
        {
          "blockchains": [
            {"uid": "ethereum", "name": "Ethereum", "url": "https://etherscan.io"},
            {"uid": "solana", "name": "Solana"}
          ],
          "coins": [
            {
              "coingecko_id": "yearn-finance",
              "name": "yearn.finance",
              "code": "yfi",
              "image": "https://assets.coingecko.com/coins/images/11849/large/yearn.jpg",
              "symbol": "YFI",
              "priority": 100,
              "tokens": [
                {"type": "eip20", "blockchain_uid": "ethereum", "address": "0xabc", "decimals": 18},
                {"type": "native", "blockchain_uid": "solana"}
              ]
            },
            {
              "coingecko_id": "bitcoin",
              "name": "Bitcoin",
              "code": "btc",
              "tokens": []
            }
          ]
        }
    """.trimIndent()

    private val coinRanksJson = """{"bitcoin":1,"ethereum":2,"tether":3}"""

    private val coinsUpdatesJson = """{"updated_at_utc":"2026-09-25T00:00:00Z","updated_at":1790332902}"""

    @Test
    fun coinsList_twoCoinsWithNestedAndEmptyTokens_mapsToDomainAndIgnoresUnknownFields() = runTest {
        val repository = repository { respondJson(coinsListJson) }

        val result = repository.coinsList()

        assertEquals(2, result.coins.size)
        val yearnFinance = result.coins.first { it.coingeckoId == "yearn-finance" }
        assertEquals("yfi", yearnFinance.code)
        assertEquals(100, yearnFinance.priority)
        assertEquals(2, yearnFinance.tokens.size)

        val bitcoin = result.coins.first { it.coingeckoId == "bitcoin" }
        assertTrue(bitcoin.tokens.isEmpty())
        assertNull(bitcoin.priority)
    }

    @Test
    fun coinsList_blockchainWithoutUrlAndTokenWithoutAddressDecimals_mapsNullableFieldsAsNull() = runTest {
        val repository = repository { respondJson(coinsListJson) }

        val result = repository.coinsList()

        val ethereum = result.blockchains.first { it.uid == "ethereum" }
        assertEquals("https://etherscan.io", ethereum.url)
        val solana = result.blockchains.first { it.uid == "solana" }
        assertNull(solana.url)

        val nativeToken = result.coins.first { it.coingeckoId == "yearn-finance" }
            .tokens.first { it.type == "native" }
        assertNull(nativeToken.address)
        assertNull(nativeToken.decimals)
    }

    @Test
    fun coinRanks_dictionaryResponse_parsesToMap() = runTest {
        val repository = repository { respondJson(coinRanksJson) }

        val result = repository.coinRanks()

        assertEquals(mapOf("bitcoin" to 1, "ethereum" to 2, "tether" to 3), result)
    }

    @Test
    fun coinsList_request_sendsCacheControlNoCacheHeader() = runTest {
        val repository = repository { respondJson(coinsListJson) }

        repository.coinsList()

        assertTrue(requestedUrls.any { it == "https://p.cash/v2/coins/list" })
        assertEquals(listOf("no-cache, no-store, must-revalidate"), requestedCacheControlHeaders)
    }

    @Test
    fun coinsUpdatedAt_response_returnsUpdatedAtAndRequestsUpdatesUrl() = runTest {
        val repository = repository { respondJson(coinsUpdatesJson) }

        val result = repository.coinsUpdatedAt()

        assertEquals(1790332902L, result)
        assertTrue(requestedUrls.any { it == "https://p.cash/v2/coins/updates" })
        assertEquals(listOf("no-cache, no-store, must-revalidate"), requestedCacheControlHeaders)
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun repository(
        handler: MockRequestHandleScope.() -> HttpResponseData,
    ): CoinsListRepositoryImpl {
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            requestedCacheControlHeaders += request.headers[HttpHeaders.CacheControl]
            handler()
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        return CoinsListRepositoryImpl(CoinsListApi(client), CoinsListMapper())
    }
}
