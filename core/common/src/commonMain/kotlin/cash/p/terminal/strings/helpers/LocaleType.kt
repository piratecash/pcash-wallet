package cash.p.terminal.strings.helpers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_32_flag_brazil
import cash.p.terminal.resources.icon_32_flag_china
import cash.p.terminal.resources.icon_32_flag_england
import cash.p.terminal.resources.icon_32_flag_france
import cash.p.terminal.resources.icon_32_flag_germany
import cash.p.terminal.resources.icon_32_flag_iran
import cash.p.terminal.resources.icon_32_flag_korea
import cash.p.terminal.resources.icon_32_flag_portugal
import cash.p.terminal.resources.icon_32_flag_russia
import cash.p.terminal.resources.icon_32_flag_spain
import cash.p.terminal.resources.icon_32_flag_turkey
import cash.p.terminal.resources.icon_32_flag_ukraine
import org.jetbrains.compose.resources.DrawableResource

enum class LocaleType(val tag: String, val flag: DrawableResource) {
    de("de", Res.drawable.icon_32_flag_germany),
    en("en", Res.drawable.icon_32_flag_england),
    es("es", Res.drawable.icon_32_flag_spain),
    pt_br("pt-BR", Res.drawable.icon_32_flag_brazil),
    pt("pt", Res.drawable.icon_32_flag_portugal),
    fa("fa", Res.drawable.icon_32_flag_iran),
    fr("fr", Res.drawable.icon_32_flag_france),
    ko("ko", Res.drawable.icon_32_flag_korea),
    ru("ru", Res.drawable.icon_32_flag_russia),
    uk("uk", Res.drawable.icon_32_flag_ukraine),
    tr("tr", Res.drawable.icon_32_flag_turkey),
    zh("zh", Res.drawable.icon_32_flag_china);

    companion object {
        /** A regional tag (en-US, zh-CN) belongs to its language; only Brazilian Portuguese keeps a region. */
        fun of(localeTag: String): LocaleType? {
            val tag = if (localeTag == pt_br.tag) localeTag else localeTag.substringBefore("-")
            return entries.firstOrNull { it.tag == tag }
        }
    }
}
