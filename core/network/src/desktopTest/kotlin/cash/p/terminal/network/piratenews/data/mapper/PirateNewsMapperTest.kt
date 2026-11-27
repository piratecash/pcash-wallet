package cash.p.terminal.network.piratenews.data.mapper

import cash.p.terminal.network.piratenews.data.entity.PiratePostItemDto
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PirateNewsMapperTest {

    @Test
    fun mapNews_wordPressPostInSiteTimezone_usesGmtPublishTime() {
        val json = """
            [{
              "id": 1,
              "date": "2026-03-02T15:30:00",
              "date_gmt": "2026-03-02T13:30:00",
              "title": {"rendered": "Title"},
              "link": "https://pirate.blog/post",
              "excerpt": {"rendered": "Body"}
            }]
        """.trimIndent()
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<List<PiratePostItemDto>>(json)

        val post = PirateNewsMapper().mapNews(dto).single()

        assertEquals(Instant.parse("2026-03-02T13:30:00Z").epochSecond, post.date)
    }
}
