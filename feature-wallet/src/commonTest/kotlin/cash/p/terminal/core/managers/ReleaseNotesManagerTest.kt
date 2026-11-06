package cash.p.terminal.core.managers

import cash.p.terminal.core.ReleaseNotesStorage
import io.horizontalsystems.core.ISystemInfoManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseNotesManagerTest {

    private class FakeStorage(
        override var changelogShownForAppVersion: String? = null,
        override var showChangelogAfterUpdate: Boolean = true,
    ) : ReleaseNotesStorage

    private val systemInfoManager = mockk<ISystemInfoManager> {
        every { appVersion } returns "1.5.0"
    }

    private fun manager(storage: ReleaseNotesStorage) = ReleaseNotesManager(systemInfoManager, storage)

    @Test
    fun shouldShowChangeLog_freshInstall_returnsFalseAndStoresCurrentVersion() {
        val storage = FakeStorage()

        assertFalse(manager(storage).shouldShowChangeLog())
        assertEquals("1.5.0", storage.changelogShownForAppVersion)
    }

    @Test
    fun shouldShowChangeLog_shownForOlderMinorAndToggleOn_returnsTrue() {
        val storage = FakeStorage(changelogShownForAppVersion = "1.4.0")

        assertTrue(manager(storage).shouldShowChangeLog())
        assertEquals("1.4.0", storage.changelogShownForAppVersion)
    }

    @Test
    fun shouldShowChangeLog_shownForCurrentVersion_returnsFalse() {
        val storage = FakeStorage(changelogShownForAppVersion = "1.5.0")

        assertFalse(manager(storage).shouldShowChangeLog())
    }

    @Test
    fun shouldShowChangeLog_toggleOff_returnsFalseAndStoresCurrentVersion() {
        val storage = FakeStorage(changelogShownForAppVersion = "1.4.0", showChangelogAfterUpdate = false)

        assertFalse(manager(storage).shouldShowChangeLog())
        assertEquals("1.5.0", storage.changelogShownForAppVersion)
    }
}
