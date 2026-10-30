package cash.p.terminal.widgets

import cash.p.terminal.widgets.MarketWidgetStateDefinition.MarketWidgetStateSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketWidgetStateSerializerTest {

    private val item = MarketWidgetItem(
        uid = "punks",
        title = "Punks",
        subtitle = "1 ETH",
        label = "1",
        value = "10 ETH",
        diff = BigDecimal.ONE,
        blockchainTypeUid = "ethereum",
        imageRemoteUrl = "https://example.com/punks.png",
    )

    private suspend fun read(json: String) =
        MarketWidgetStateSerializer.readFrom(ByteArrayInputStream(json.encodeToByteArray()))

    @Test
    fun readFrom_persistedTopNftsWidget_fallsBackToTopGainersWithoutItems() = runTest {
        val json = """
            {"widgetId":7,"type":"topNfts","loading":false,"error":"boom","updateTimestampMillis":1,
            "items":[{"uid":"punks","title":"Punks","subtitle":"1 ETH","label":"1","value":"10 ETH",
            "diff":1,"blockchainTypeUid":"ethereum","imageRemoteUrl":"https://example.com/punks.png"}]}
        """.trimIndent()

        val state = read(json)

        assertEquals(7, state.widgetId)
        assertEquals(MarketWidgetType.TopGainers, state.type)
        assertTrue(state.items.isEmpty())
        assertTrue(state.loading)
        assertEquals(null, state.error)
    }

    @Test
    fun readFrom_knownType_keepsStateUnchanged() = runTest {
        val state = MarketWidgetState(
            widgetId = 3,
            type = MarketWidgetType.TopPlatforms,
            items = listOf(item),
            error = "offline",
            updateTimestampMillis = 42,
        )
        val output = ByteArrayOutputStream()
        MarketWidgetStateSerializer.writeTo(state, output)

        assertEquals(state, read(output.toString()))
    }
}
