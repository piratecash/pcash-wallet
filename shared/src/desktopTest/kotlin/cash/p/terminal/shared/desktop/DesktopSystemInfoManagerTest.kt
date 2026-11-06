package cash.p.terminal.shared.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSystemInfoManagerTest {

    @Test
    fun appVersion_fromHolder_isTheSameEverywhereWithoutHeaderPrefix() {
        val manager = DesktopSystemInfoManager(DesktopAppVersion("1.0.260"))

        assertEquals("1.0.260", manager.appVersion)
        assertEquals("1.0.260", manager.appVersionFull)
        assertEquals("1.0.260", manager.appVersionDisplay)
    }
}
