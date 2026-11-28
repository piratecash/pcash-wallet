package cash.p.terminal.network.piratenews.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class PiratePostItemDto(
    val id: Int,
    // `date` is in the site's timezone; only `date_gmt` is UTC.
    @SerialName("date_gmt")
    val dateGmt: String,
    val title: StringRendered,
    val link: String,
    val excerpt: StringRendered
)

@Serializable
internal class StringRendered(
    val rendered: String
)