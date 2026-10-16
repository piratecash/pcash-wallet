package cash.p.terminal.shared.desktop.backup

import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.CUSTOM_AUTH
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.PASSPHRASE
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.androidSettings
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.customAuth
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.withoutCustomAuth
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Android -> desktop -> Android: a backup that passes through the desktop keeps every setting but the app icon. */
class DesktopBackupOnAndroidTest {

    private val fixture = DesktopSettingsBackupFixture()

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun currentSettings_restoredAndroidBackup_reExportsItWithoutTheAppIcon() = runTest {
        val androidSettings = androidSettings()

        fixture.backup.restore(androidSettings, PASSPHRASE)
        val reExported = fixture.export()

        assertEquals(CUSTOM_AUTH, reExported.customAuth())
        assertEquals(androidSettings.copy(appIcon = null).withoutCustomAuth(), reExported.withoutCustomAuth())
    }
}
