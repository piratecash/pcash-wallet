package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WRONG_PASSWORD = "Wrong-password0"
private const val NOTIFY_KEY_SIZE = 32
private const val FAIL_FAILED_INSERTS =
    "CREATE TRIGGER fail_failed BEFORE INSERT ON JournalRecord WHEN NEW.kind = 'FAILED' " +
        "BEGIN SELECT RAISE(ABORT, 'test'); END"
private const val FAIL_SECOND_FAILED_INSERT =
    "CREATE TRIGGER fail_second BEFORE INSERT ON JournalRecord " +
        "WHEN (SELECT COUNT(*) FROM JournalRecord WHERE kind = 'FAILED') >= 1 BEGIN SELECT RAISE(ABORT, 'test'); END"

/** Storage and timing failures the journal must come through without losing or doubling an entry. */
class JournalFailuresTest {

    @Test
    fun onEntered_sessionSwitchedDuringTheCapture_stillAnnouncesTheEntry() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setSelfieOnDuressLoginEnabled(0, true)
        fixture.reloginOk(DURESS_PASSWORD)
        val gate = CompletableDeferred<Unit>().also { fixture.camera.gate = it }

        unlockWarm(fixture, DURESS_PASSWORD)
        unlockWarm(fixture, MAIN_PASSWORD)
        gate.complete(Unit)
        fixture.settle()
        unlockWarm(fixture, MAIN_PASSWORD)
        fixture.settle()

        assertEquals(2, fixture.records(0).count { it.kind == JournalRecordKind.DURESS_ENTRY })
    }

    @Test
    fun onEntered_narrowedBeforeTheQueueGetsToIt_recordsNothingForTheNewRoot() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogSuccessfulLoginsEnabled(0, true)
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setSelfieOnUnsuccessfulLoginEnabled(0, true)
        val gate = CompletableDeferred<Unit>().also { fixture.camera.gate = it }
        fixture.vault.lock()

        fixture.vault.unlock(password(WRONG_PASSWORD))
        unlockWarm(fixture, MAIN_PASSWORD)
        unlockWarm(fixture, DURESS_PASSWORD)
        gate.complete(Unit)
        fixture.settle()

        assertEquals(1, fixture.session.rootLevel)
        assertEquals(listOf(JournalRecordKind.SUCCESS), fixture.records(1).map { it.kind })
    }

    @Test
    fun importChildEntries_parentJournalFailedToOpen_keepsTheEntryForLater() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        val journal = fixture.levels.files.journalFile(fixture.safe(0).link.slot)
        fixture.reloginOk(DURESS_PASSWORD)

        fixture.breakFile(journal)
        fixture.reloginOk(MAIN_PASSWORD)
        assertEquals(null, fixture.safe(0).journal)
        fixture.vault.close()
        fixture.restoreFile(journal)
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(listOf(JournalRecordKind.DURESS_ENTRY), fixture.records(0).map { it.kind })
    }

    @Test
    fun unlock_warmAfterAChildJournalFailedToOpen_reopensAndFinishesTheWipe() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setDeleteAllAuthDataOnDuressEnabled(0, true)
        fixture.reloginOk(DURESS_PASSWORD)
        val childJournal = fixture.levels.files.journalFile(fixture.safe(0).link.slot)
        fixture.breakFile(childJournal)
        fixture.reloginOk(MAIN_PASSWORD)
        assertTrue(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))

        fixture.vault.lock()
        fixture.restoreFile(childJournal)
        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(MAIN_PASSWORD)))
        fixture.settle()

        assertFalse(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))
        assertTrue(fixture.records(1).isEmpty())
    }

    @Test
    fun importFailedAttempts_secondInsertFailsThenRetried_importsEachOnce() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        val (journal, key) = journalOf(fixture)
        fixture.relogin(WRONG_PASSWORD)
        fixture.relogin(WRONG_PASSWORD)
        fixture.execute(journal, key, FAIL_SECOND_FAILED_INSERT)

        fixture.reloginOk(MAIN_PASSWORD)
        fixture.vault.close()
        fixture.execute(journal, key, "DROP TRIGGER fail_second")
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(2, fixture.records(0).count { it.kind == JournalRecordKind.FAILED })
    }

    @Test
    fun onEntered_vaultDirectoryReadOnly_stillAnnouncesTheEntry() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.reloginOk(DURESS_PASSWORD)

        fixture.withVaultDirectoryReadOnly { unlockWarm(fixture, DURESS_PASSWORD) }
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(2, fixture.records(0).count { it.kind == JournalRecordKind.DURESS_ENTRY })
    }

    @Test
    fun importFailedAttempts_watermarkWriteFailsThenRetried_importsTheAttemptOnce() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        val root = fixture.safe(0)
        val safeFile = fixture.levels.files.safeFile(root.link.slot)
        val key = root.link.key.copyOf()
        fixture.relogin(WRONG_PASSWORD)
        listOf("INSERT", "UPDATE").forEach { operation ->
            fixture.execute(
                safeFile,
                key,
                "CREATE TRIGGER fail_watermark_$operation BEFORE $operation ON LevelSetting " +
                    "WHEN NEW.`key` = '${JournalSetting.INBOX_WATERMARK}' BEGIN SELECT RAISE(ABORT, 'test'); END",
            )
        }

        fixture.reloginOk(MAIN_PASSWORD)
        fixture.vault.close()
        listOf("INSERT", "UPDATE").forEach { fixture.execute(safeFile, key, "DROP TRIGGER fail_watermark_$it") }
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(1, fixture.records(0).count { it.kind == JournalRecordKind.FAILED })
    }

    @Test
    fun importFailedAttempts_premiumCheckFailsThenRecovers_importsTheAttempt() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        fixture.relogin(WRONG_PASSWORD)
        fixture.premiumCheckFails = true
        fixture.reloginOk(MAIN_PASSWORD)

        fixture.premiumCheckFails = false
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(1, fixture.records(0).count { it.kind == JournalRecordKind.FAILED })
    }

    @Test
    fun run_importFailsWithAPendingWipe_stillWipes() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        fixture.safe(0).dao.putFlag(JournalSetting.PENDING_WIPE, true)
        val (journal, key) = journalOf(fixture)
        fixture.relogin(WRONG_PASSWORD)
        fixture.execute(journal, key, FAIL_FAILED_INSERTS)

        fixture.reloginOk(MAIN_PASSWORD)

        assertFalse(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))
    }

    @Test
    fun setLogUnsuccessfulLoginsEnabled_mailboxUnreadable_staysOff() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.levels.files.inboxFile.writeBytes(ByteArray(1))

        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)

        assertFalse(fixture.parts.settings.getLogUnsuccessfulLoginsEnabled(0))
        assertFalse(fixture.parts.settings.getLogIntoDuressModeEnabled(0))
    }

    @Test
    fun setLogIntoDuressModeEnabled_offAfterTheSelfie_childKeepsNeitherFlag() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setSelfieOnDuressLoginEnabled(0, true)

        fixture.parts.settings.setLogIntoDuressModeEnabled(0, false)

        val child = fixture.safe(1).dao
        assertFalse(child.flag(JournalSetting.LOG_SUCCESSFUL))
        assertFalse(child.flag(JournalSetting.SELFIE_ON_SUCCESSFUL))
    }

    @Test
    fun onEntered_inboxUnwritable_stillRecordsTheSuccessfulLogin() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.parts.settings.setLogSuccessfulLoginsEnabled(0, true)
        val inbox = fixture.levels.files.inboxFile

        check(inbox.setWritable(false))
        try {
            unlockWarm(fixture, MAIN_PASSWORD)
            fixture.settle()
        } finally {
            inbox.setWritable(true)
        }

        assertEquals(1, fixture.records(0).count { it.kind == JournalRecordKind.SUCCESS })
    }

    @Test
    fun unlock_childKeyLeftUnlinkedByAFailedWrite_duressEntryStillReachesTheParent() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.safe(1).dao.putBytes(JournalSetting.NOTIFY_KEY, Random.nextBytes(NOTIFY_KEY_SIZE))

        unlockWarm(fixture, DURESS_PASSWORD)
        fixture.settle()
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(1, fixture.records(0).count { it.kind == JournalRecordKind.DURESS_ENTRY })
    }

    /** The root's journal file and key; the vault is closed afterwards. */
    private suspend fun journalOf(fixture: JournalFixture) = fixture.safe(0).let { root ->
        val key = checkNotNull(root.dao.bytes(JournalSetting.JOURNAL_KEY))
        val file = fixture.levels.files.journalFile(root.link.slot)
        fixture.vault.close()
        file to key
    }

    private suspend fun unlockWarm(fixture: JournalFixture, password: String) {
        fixture.vault.lock()
        assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(password)))
    }
}
