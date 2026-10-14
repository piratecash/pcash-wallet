package cash.p.terminal.modules.basecurrency

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_32_flag_argentine
import cash.p.terminal.resources.icon_32_flag_australia
import cash.p.terminal.resources.icon_32_flag_brazil
import cash.p.terminal.resources.icon_32_flag_canada
import cash.p.terminal.resources.icon_32_flag_china
import cash.p.terminal.resources.icon_32_flag_england
import cash.p.terminal.resources.icon_32_flag_europe
import cash.p.terminal.resources.icon_32_flag_hongkong
import cash.p.terminal.resources.icon_32_flag_hungary
import cash.p.terminal.resources.icon_32_flag_india
import cash.p.terminal.resources.icon_32_flag_israel
import cash.p.terminal.resources.icon_32_flag_japan
import cash.p.terminal.resources.icon_32_flag_norway
import cash.p.terminal.resources.icon_32_flag_philippine
import cash.p.terminal.resources.icon_32_flag_russia
import cash.p.terminal.resources.icon_32_flag_singapore
import cash.p.terminal.resources.icon_32_flag_south_africa
import cash.p.terminal.resources.icon_32_flag_switzerland
import cash.p.terminal.resources.icon_32_flag_usa
import org.jetbrains.compose.resources.DrawableResource

private val flags: Map<String, DrawableResource> = mapOf(
    "AUD" to Res.drawable.icon_32_flag_australia,
    "ARS" to Res.drawable.icon_32_flag_argentine,
    "BRL" to Res.drawable.icon_32_flag_brazil,
    "CAD" to Res.drawable.icon_32_flag_canada,
    "CHF" to Res.drawable.icon_32_flag_switzerland,
    "CNY" to Res.drawable.icon_32_flag_china,
    "EUR" to Res.drawable.icon_32_flag_europe,
    "GBP" to Res.drawable.icon_32_flag_england,
    "HKD" to Res.drawable.icon_32_flag_hongkong,
    "HUF" to Res.drawable.icon_32_flag_hungary,
    "ILS" to Res.drawable.icon_32_flag_israel,
    "INR" to Res.drawable.icon_32_flag_india,
    "JPY" to Res.drawable.icon_32_flag_japan,
    "NOK" to Res.drawable.icon_32_flag_norway,
    "PHP" to Res.drawable.icon_32_flag_philippine,
    "RUB" to Res.drawable.icon_32_flag_russia,
    "SGD" to Res.drawable.icon_32_flag_singapore,
    "USD" to Res.drawable.icon_32_flag_usa,
    "ZAR" to Res.drawable.icon_32_flag_south_africa,
)

fun currencyFlag(code: String): DrawableResource = flags[code] ?: Res.drawable.icon_32_flag_usa
