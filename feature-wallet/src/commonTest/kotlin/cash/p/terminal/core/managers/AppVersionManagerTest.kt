package cash.p.terminal.core.managers

import cash.p.terminal.core.AppVersionStorage
import cash.p.terminal.entities.AppVersion
import io.horizontalsystems.core.ISystemInfoManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class AppVersionManagerTest {

    private class FakeStorage(override var appVersions: List<AppVersion> = emptyList()) : AppVersionStorage

    private fun manager(storage: AppVersionStorage, version: String) = AppVersionManager(
        mockk<ISystemInfoManager> { every { appVersion } returns version },
        storage,
    )

    @Test
    fun storeAppVersion_firstLaunch_recordsTheVersion() {
        val storage = FakeStorage()

        manager(storage, "1.5.0").storeAppVersion()

        assertEquals(listOf("1.5.0"), storage.appVersions.map { it.version })
    }

    @Test
    fun storeAppVersion_sameVersionAgain_recordsNothing() {
        val storage = FakeStorage(listOf(AppVersion("1.5.0", 1L)))

        manager(storage, "1.5.0").storeAppVersion()

        assertEquals(listOf(AppVersion("1.5.0", 1L)), storage.appVersions)
    }

    @Test
    fun storeAppVersion_newVersion_appendsToTheHistory() {
        val storage = FakeStorage(listOf(AppVersion("1.4.0", 1L)))

        manager(storage, "1.5.0").storeAppVersion()

        assertEquals(listOf("1.4.0", "1.5.0"), storage.appVersions.map { it.version })
    }
}
