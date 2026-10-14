package cash.p.terminal.modules.theme

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsTheme_Dark
import cash.p.terminal.resources.SettingsTheme_Light
import cash.p.terminal.resources.SettingsTheme_System
import com.google.gson.annotations.SerializedName
import cash.p.terminal.R
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

enum class ThemeType(
    val value: String,
    override val title: TranslatableString,
    val iconRes: Int
) : WithTranslatableTitle {
    @SerializedName("dark")
    Dark(
        "Dark",
        TranslatableString.ResString(Res.string.SettingsTheme_Dark),
        R.drawable.ic_theme_dark
    ),
    @SerializedName("light")
    Light(
        "Light",
        TranslatableString.ResString(Res.string.SettingsTheme_Light),
        R.drawable.ic_theme_light
    ),
    @SerializedName("system")
    System(
        "System",
        TranslatableString.ResString(Res.string.SettingsTheme_System),
        R.drawable.ic_theme_system
    );
}
