package cash.p.terminal.modules.settings.appearance

import cash.p.terminal.resources.Market_PriceChange_24H
import cash.p.terminal.resources.Market_PriceChange_Utc
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import com.google.gson.annotations.SerializedName

enum class PriceChangeInterval(val raw: String, override val title: TranslatableString) :
    WithTranslatableTitle {
    @SerializedName("hour_24")
    LAST_24H("hour_24", TranslatableString.ResString(Res.string.Market_PriceChange_24H)),

    @SerializedName("midnight_utc")
    FROM_UTC_MIDNIGHT("midnight_utc", TranslatableString.ResString(Res.string.Market_PriceChange_Utc));

    companion object {
        fun fromRaw(raw: String): PriceChangeInterval? {
            return entries.find { it.raw == raw }
        }
    }
}
