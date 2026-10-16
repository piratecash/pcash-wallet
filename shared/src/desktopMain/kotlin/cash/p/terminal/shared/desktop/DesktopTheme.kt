package cash.p.terminal.shared.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.theme.ThemeType
import org.koin.compose.koinInject

/** Desktop applies the theme by composition, so a change in Appearance repaints the whole window. */
@Composable
fun rememberDesktopDarkTheme(): Boolean {
    val themeOptions by koinInject<ThemeService>().optionsFlow.collectAsStateWithLifecycle()
    return themeOptions.selected.isDark(systemDark = isSystemInDarkTheme())
}

internal fun ThemeType.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeType.Dark -> true
    ThemeType.Light -> false
    ThemeType.System -> systemDark
}
