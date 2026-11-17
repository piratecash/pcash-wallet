package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.vault.JournalInboxCrypto
import io.horizontalsystems.core.entities.AutoDeletePeriod
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val WRONG_PASSWORD = "Wrong-password0"

class JournalEntryTest {

    @Test
    fun onEntered_logSuccessfulOff_writesNoRecordButAnnouncesTheEntry() = journalScenario { fixture ->
        fixture.levels.create()

        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(0).isEmpty())
        assertEquals(JournalMailbox.FIRST_SEQ + 1, fixture.vault.mailbox.nextSeq(MailboxRing.NOTIFY))
        assertEquals(0, fixture.camera.captures)
    }

    @Test
    fun onEntered_logSuccessfulWithSelfieAndPremium_recordsSuccessWithOnePhoto() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogSuccessfulLoginsEnabled(0, true)
        fixture.parts.settings.setSelfieOnSuccessfulLoginEnabled(0, true)

        fixture.reloginOk(MAIN_PASSWORD)

        val record = fixture.records(0).single()
        assertEquals(JournalRecordKind.SUCCESS, record.kind)
        assertContentEquals(PHOTO, fixture.safe(0).journal?.photo(record.id))
        assertEquals(1, fixture.camera.captures)
    }

    @Test
    fun onEntered_noPremium_writesNoVisibleRecord() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogSuccessfulLoginsEnabled(0, true)
        fixture.nonPremiumSlots += fixture.safe(0).link.slot

        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(0).isEmpty())
    }

    @Test
    fun onEntered_cameraFails_marksTheSelfieProblem() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogSuccessfulLoginsEnabled(0, true)
        fixture.parts.settings.setSelfieOnSuccessfulLoginEnabled(0, true)
        fixture.camera.photo = null

        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.safe(0).dao.flag(JournalSetting.SELFIE_PROBLEM))
        assertTrue(!fixture.records(0).single().hasPhoto)
    }

    @Test
    fun importChildEntries_duressEntryWithoutChildPremium_parentGetsDuressEntryWithPhoto() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setSelfieOnDuressLoginEnabled(0, true)
        fixture.nonPremiumSlots += fixture.safe(1).link.slot

        fixture.reloginOk(DURESS_PASSWORD)
        assertTrue(fixture.records(0).isEmpty())
        fixture.reloginOk(MAIN_PASSWORD)

        val record = fixture.records(0).single()
        assertEquals(JournalRecordKind.DURESS_ENTRY, record.kind)
        assertContentEquals(PHOTO, fixture.safe(0).journal?.photo(record.id))
    }

    @Test
    fun importChildEntries_childDeletedItsOwnRecords_parentStillGetsTheEntryOnce() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)

        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(JournalRecordKind.SUCCESS, fixture.records(0).single().kind)
        fixture.parts.records.deleteAll(0)
        fixture.reloginOk(MAIN_PASSWORD)
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(listOf(JournalRecordKind.DURESS_ENTRY), fixture.records(0).map { it.kind })
    }

    @Test
    fun applyDuressFlags_setBeforeTheChildExists_applyAtItsCreation() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setSelfieOnDuressLoginEnabled(0, true)
        fixture.levels.setDuress()

        fixture.reloginOk(DURESS_PASSWORD)
        val own = fixture.records(0).single()
        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(own.hasPhoto)
        assertEquals(JournalRecordKind.DURESS_ENTRY, fixture.records(0).single().kind)
    }

    @Test
    fun importFailedAttempts_perLevelByFlagAndPremium_importsOnceWithPhoto() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        fixture.parts.settings.setSelfieOnUnsuccessfulLoginEnabled(0, true)

        assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(WRONG_PASSWORD))
        fixture.reloginOk(MAIN_PASSWORD)
        fixture.reloginOk(MAIN_PASSWORD)

        val record = fixture.records(0).single()
        assertEquals(JournalRecordKind.FAILED, record.kind)
        assertContentEquals(PHOTO, fixture.safe(0).journal?.photo(record.id))
        assertTrue(fixture.records(1).isEmpty())
    }

    @Test
    fun importFailedAttempts_noPremium_skipsThemForGood() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        val slot = fixture.safe(0).link.slot
        fixture.nonPremiumSlots += slot

        fixture.relogin(WRONG_PASSWORD)
        fixture.reloginOk(MAIN_PASSWORD)
        fixture.nonPremiumSlots -= slot
        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(0).isEmpty())
    }

    @Test
    fun setLogUnsuccessfulLoginsEnabled_attemptsBeforeEnabling_areNotImported() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.relogin(WRONG_PASSWORD)
        fixture.reloginOk(MAIN_PASSWORD)

        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(0).isEmpty())
    }

    @Test
    fun verifyPassword_wrongPassword_isNotRecorded() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)

        assertIs<UnlockOutcome.WrongPassword>(fixture.vault.verifyPassword(password(WRONG_PASSWORD)))
        fixture.settle()
        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(0).isEmpty())
        assertEquals(JournalMailbox.FIRST_SEQ, fixture.vault.mailbox.nextSeq(MailboxRing.FAILED))
    }

    @Test
    fun onEntered_autoDeletePeriod_removesOnlyRecordsPastIt() = journalScenario { fixture ->
        fixture.levels.create()
        val journal = checkNotNull(fixture.safe(0).journal)
        journal.insert(0, JournalRecordKind.SUCCESS, "", null)
        fixture.parts.settings.setAutoDeleteLogsPeriod(0, AutoDeletePeriod.NEVER.value)
        fixture.reloginOk(MAIN_PASSWORD)
        assertEquals(1, fixture.records(0).size)

        fixture.parts.settings.setAutoDeleteLogsPeriod(0, AutoDeletePeriod.MONTH.value)
        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(0).isEmpty())
    }

    @Test
    fun onEntered_levelBelowWithItsOwnPeriod_removesItsExpiredRecords() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        checkNotNull(fixture.safe(1).journal).insert(0, JournalRecordKind.FAILED, "", null)
        fixture.parts.settings.setAutoDeleteLogsPeriod(0, AutoDeletePeriod.NEVER.value)
        fixture.parts.settings.setAutoDeleteLogsPeriod(1, AutoDeletePeriod.MONTH.value)

        fixture.reloginOk(MAIN_PASSWORD)

        assertTrue(fixture.records(1).isEmpty())
    }

    @Test
    fun appendFailed_generationReadBeforeAWipe_isNotWritten() = journalScenario { fixture ->
        fixture.levels.create()
        val mailbox = fixture.vault.mailbox
        val generation = mailbox.wipeGeneration

        mailbox.overwriteEntries()

        assertTrue(!mailbox.appendFailed(1, null, generation))
        assertTrue(!mailbox.appendNotification(JournalInboxCrypto.decoyPublicKey(), 1, null, generation))
        assertEquals(JournalMailbox.FIRST_SEQ, mailbox.nextSeq(MailboxRing.FAILED))
    }
}
