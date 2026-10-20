package cash.p.terminal.modules.theme

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsTheme_Dark
import cash.p.terminal.resources.SettingsTheme_Light
import cash.p.terminal.resources.SettingsTheme_System
import cash.p.terminal.resources.ic_theme_dark
import cash.p.terminal.resources.ic_theme_light
import cash.p.terminal.resources.ic_theme_system
import com.google.gson.annotations.SerializedName
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import org.jetbrains.compose.resources.DrawableResource

enum class ThemeType(
    val value: String,
    override val title: TranslatableString,
    val iconRes: DrawableResource
) : WithTranslatableTitle {
    @SerializedName("dark")
    Dark(
        "Dark",
        TranslatableString.ResString(Res.string.SettingsTheme_Dark),
        Res.drawable.ic_theme_dark
    ),
    @SerializedName("light")
    Light(
        "Light",
        TranslatableString.ResString(Res.string.SettingsTheme_Light),
        Res.drawable.ic_theme_light
    ),
    @SerializedName("system")
    System(
        "System",
        TranslatableString.ResString(Res.string.SettingsTheme_System),
        Res.drawable.ic_theme_system
    );
}
