package cash.p.terminal.modules.theme

import androidx.appcompat.app.AppCompatDelegate

internal actual fun applyNightMode(themeType: ThemeType) {
    val nightMode = when (themeType) {
        ThemeType.Light -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeType.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeType.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    AppCompatDelegate.setDefaultNightMode(nightMode)
}
