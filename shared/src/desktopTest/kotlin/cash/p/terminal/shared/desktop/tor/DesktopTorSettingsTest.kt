package cash.p.terminal.shared.desktop.tor

import cash.p.terminal.core.tor.TorPlatform
import io.horizontalsystems.core.DesktopSettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTorSettingsTest {

    private val store = mockk<DesktopSettingsStore> { every { flag("torEnabled") } returns true }

    @Test
    fun torEnabled_storedOnUnsupportedPlatform_readsOff() {
        assertFalse(DesktopTorSettings(store, platform(supported = false)).torEnabled)
    }

    @Test
    fun torEnabled_storedOnSupportedPlatform_readsOn() {
        assertTrue(DesktopTorSettings(store, platform(supported = true)).torEnabled)
    }

    private fun platform(supported: Boolean) = mockk<TorPlatform> { every { isSupported } returns supported }
}
