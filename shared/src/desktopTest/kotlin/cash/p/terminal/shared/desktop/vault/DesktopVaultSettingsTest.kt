package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.shared.desktop.DesktopBeamResetMarker
import cash.p.terminal.shared.desktop.DesktopWalletSettingsStorage
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopVaultSettingsTest {
    private val fixture = VaultLevelsFixture()

    private val storage get() = DesktopWalletSettingsStorage(mockk(relaxed = true), DesktopAccountSettings(fixture.vault))
    private val levelSettings get() = DesktopLevelSettings(fixture.vault)

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun zcashAccountIds_lowerLevelOpened_showsOnlyWhatLivesInItsStores() = runBlocking<Unit> {
        fixture.mainWithDuress()
        storage.zcashAccountIds = setOf(MAIN_ACCOUNT, DURESS_ACCOUNT)
        assertEquals(setOf(MAIN_ACCOUNT, DURESS_ACCOUNT), storage.zcashAccountIds)

        fixture.reloginOk(DURESS_PASSWORD)

        assertEquals(setOf(DURESS_ACCOUNT), storage.zcashAccountIds)
    }

    @Test
    fun zcashAccountIds_writtenFromALowerLevel_keepsTheEntriesItCannotSee() = runBlocking<Unit> {
        fixture.mainWithDuress()
        storage.zcashAccountIds = setOf(MAIN_ACCOUNT, DURESS_ACCOUNT)

        fixture.reloginOk(DURESS_PASSWORD)
        storage.zcashAccountIds -= DURESS_ACCOUNT
        fixture.reloginOk()

        assertEquals(setOf(MAIN_ACCOUNT), storage.zcashAccountIds)
    }

    @Test
    fun zcashIronwoodMigrationTxIds_accountMoved_followsItsAccount() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val mainTx = "$MAIN_ACCOUNT:hash-1"
        val duressTx = "$DURESS_ACCOUNT:hash-2"
        storage.zcashIronwoodMigrationTxIds = setOf(mainTx, duressTx)

        fixture.router.setLevel(listOf(MAIN_ACCOUNT), 1)
        fixture.reloginOk(DURESS_PASSWORD)

        assertEquals(setOf(mainTx, duressTx), storage.zcashIronwoodMigrationTxIds)
    }

    @Test
    fun dismissedNftVersionHints_lowerLevelOpened_showsOnlyItsAccounts() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val mainHint = "$MAIN_ACCOUNT:W5"
        val duressHint = "$DURESS_ACCOUNT:V4R2"
        storage.dismissedNftVersionHints = setOf(mainHint, duressHint)
        assertEquals(setOf(mainHint, duressHint), storage.dismissedNftVersionHints)

        fixture.reloginOk(DURESS_PASSWORD)

        assertEquals(setOf(duressHint), storage.dismissedNftVersionHints)
    }

    @Test
    fun nonRecommendedAlertDismissed_survivesARestartInTheAccountStore() = runBlocking<Unit> {
        fixture.mainWithDuress()
        storage.nonRecommendedAccountAlertDismissedAccounts = setOf(DURESS_ACCOUNT)

        fixture.reloginOk()

        assertEquals(setOf(DURESS_ACCOUNT), storage.nonRecommendedAccountAlertDismissedAccounts)
        assertEquals(DURESS_ACCOUNT, fixture.session.account(DURESS_ACCOUNT)?.setting(NON_RECOMMENDED_KEY))
    }

    @Test
    fun accountSettings_noOpenStorage_readEmptyAndDropTheWrite() = runBlocking<Unit> {
        assertEquals(emptySet(), storage.zcashAccountIds)

        storage.zcashAccountIds = setOf(MAIN_ACCOUNT)

        assertEquals(emptySet(), storage.zcashAccountIds)
    }

    @Test
    fun flag_eachLevel_seesOnlyItsOwnValue() = runBlocking<Unit> {
        fixture.mainWithDuress()
        levelSettings.setFlag(0, AML_KEY, true)
        levelSettings.setFlag(1, AML_KEY, false)

        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(false, levelSettings.flag(0, AML_KEY))
        levelSettings.setFlag(0, AML_KEY, true)

        fixture.reloginOk()
        assertEquals(true, levelSettings.flag(0, AML_KEY))
        assertEquals(true, levelSettings.flag(1, AML_KEY))
    }

    @Test
    fun flag_neverWritten_isAbsentSoTheAndroidDefaultApplies() = runBlocking<Unit> {
        fixture.create()

        assertNull(levelSettings.flag(0, AML_KEY))
    }

    @Test
    fun beamResetMarker_setThenRelogin_persistsAndClears() = runBlocking<Unit> {
        fixture.create()
        DesktopBeamResetMarker(fixture.vault).set()

        fixture.reloginOk()
        assertTrue(DesktopBeamResetMarker(fixture.vault).isSet)
        DesktopBeamResetMarker(fixture.vault).clear()

        fixture.reloginOk()
        assertFalse(DesktopBeamResetMarker(fixture.vault).isSet)
    }

    private companion object {
        const val AML_KEY = "aml_check_received_enabled"
        const val NON_RECOMMENDED_KEY = "nonRecommendedAccountAlertDismissedAccounts"
    }
}
