package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.VaultSlots
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import cash.p.terminal.vault.PasswordVault
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ERASE_PASSWORD = "Erase-contacts5"

class JournalFilesTest {

    @Test
    fun create_newVault_everySlotHasAJournalInTheDecoyRangeAndTheMailboxExists() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.vault.close()

        repeat(PasswordVault.SLOT_COUNT) { assertInDecoyRange(fixture.levels.files.journalFile(it)) }
        assertEquals(JournalMailbox.FILE_SIZE, fixture.levels.files.inboxFile.length())
        assertTrue(fixture.levels.files.actionsFile.length() > 0)
    }

    @Test
    fun emptyRealJournal_afterCreateDeleteAllAndWipe_isTheSmallestDecoySize() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        val journal = fixture.levels.files.journalFile(fixture.safe(0).link.slot)
        fixture.vault.close()
        assertSmallestDecoy(journal)

        fixture.reloginOk(MAIN_PASSWORD)
        fixture.safe(0).journal?.insert(1, JournalRecordKind.SUCCESS, "account", PHOTO)
        fixture.parts.records.deleteAll(0)
        fixture.vault.close()
        assertSmallestDecoy(journal)

        fixture.reloginOk(MAIN_PASSWORD)
        fixture.parts.settings.setDeleteAllAuthDataOnDuressEnabled(0, true)
        fixture.safe(0).journal?.insert(1, JournalRecordKind.SUCCESS, "account", PHOTO)
        fixture.reloginOk(DURESS_PASSWORD)
        fixture.reloginOk(MAIN_PASSWORD)
        assertTrue(fixture.records(0).isEmpty(), "logging is off, the wipe left nothing")
        fixture.vault.close()
        assertSmallestDecoy(journal)
    }

    @Test
    fun unlock_olderVaultOpenedByEitherLevel_createsTheSameFullFileSet() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()

        val byDuress = fileSetAfterUpgrade(fixture, DURESS_PASSWORD)
        val byOwner = fileSetAfterUpgrade(fixture, MAIN_PASSWORD)

        assertEquals(byOwner, byDuress)
        assertTrue(byOwner.containsAll(List(PasswordVault.SLOT_COUNT) { "log.$it" } + listOf("inbox", "actions")))
    }

    @Test
    fun collapseLevelsAbove_duressLevel_resetsItsJournalEraseTagAndInboxKey() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        val child = fixture.safe(0)
        val slot = child.link.slot
        val journalKey = checkNotNull(child.dao.bytes(JournalSetting.JOURNAL_KEY))
        assertEquals(
            JournalSecretOutcome.SUCCESS,
            fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)),
        )
        val inboxKey = fixture.vault.mailbox.publicKey(slot)
        fixture.reloginOk(MAIN_PASSWORD)

        fixture.levels.router.collapseLevelsAbove(0)

        assertFalse(fixture.vault.mailbox.publicKey(slot).contentEquals(inboxKey))
        assertFalse(fixture.levels.passwordVault.matchesEraseTag(password(ERASE_PASSWORD), slot))
        fixture.vault.close()
        assertFalse(fixture.levels.opensWith(fixture.levels.files.journalFile(slot), journalKey))
        assertInDecoyRange(fixture.levels.files.journalFile(slot))
    }

    @Test
    fun wipe_vaultWithJournals_leavesNoVaultFile() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()

        assertEquals(WipeOutcome.SUCCESS, fixture.vault.wipe())

        assertTrue(fixture.levels.vaultDirectoryNames().isEmpty())
    }

    private suspend fun fileSetAfterUpgrade(fixture: JournalFixture, password: String): Set<String> {
        fixture.vault.close()
        val files = fixture.levels.files
        files.vaultDirectory.listFiles().orEmpty()
            .filter { it.name.startsWith("log.") || it.name == "inbox" || it.name == "actions" }
            .forEach(File::delete)
        fixture.reloginOk(password)
        fixture.vault.close()
        return fixture.levels.vaultDirectoryNames()
    }

    private fun assertSmallestDecoy(file: File) =
        assertEquals(VaultSlots.JOURNAL_MIN_PAGES.toLong() * VaultSlots.PAGE_SIZE, file.length(), file.name)

    private fun assertInDecoyRange(file: File) {
        val pages = file.length() / VaultSlots.PAGE_SIZE
        assertEquals(0, file.length() % VaultSlots.PAGE_SIZE, "${file.name} is not whole pages")
        assertTrue(pages in VaultSlots.JOURNAL_MIN_PAGES..VaultSlots.JOURNAL_MAX_PAGES, "${file.name}: $pages pages")
    }
}
