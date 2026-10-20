package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.theme.ThemeType
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopThemeTest {

    @Test
    fun isDark_eachTheme_resolvesAgainstTheSystemOnlyForSystem() {
        listOf(true, false).forEach { systemDark ->
            assertEquals(true, ThemeType.Dark.isDark(systemDark))
            assertEquals(false, ThemeType.Light.isDark(systemDark))
            assertEquals(systemDark, ThemeType.System.isDark(systemDark))
        }
    }
}
