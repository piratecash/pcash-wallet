package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.vault.JournalInboxCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalWipeTest {

    @Test
    fun importChildEntries_wipeOnDuress_clearsParentAndChildRecordsAndEveryRingEntry() = journalScenario { fixture ->
        wipeReady(fixture)
        fixture.parts.settings.setLogUnsuccessfulLoginsEnabled(0, true)
        fixture.relogin("Wrong-password0")
        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(1, fixture.records(0).size)

        fixture.reloginOk(MAIN_PASSWORD)

        assertOnlyTheCurrentEntry(fixture)
        assertTrue(fixture.records(1).isEmpty())
        assertFalse(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))
        assertNoRingEntryOpens(fixture)
    }

    @Test
    fun importChildEntries_wipe_leavesTheChildSafeUnchanged() = journalScenario { fixture ->
        wipeReady(fixture)
        fixture.reloginOk(DURESS_PASSWORD)
        val slot = fixture.safe(0).link.slot
        val key = fixture.safe(0).link.key.copyOf()
        fixture.vault.close()
        val before = fixture.safeSettings(slot, key)

        fixture.reloginOk(MAIN_PASSWORD)
        fixture.vault.close()

        assertEquals(before, fixture.safeSettings(slot, key))
    }

    @Test
    fun importChildEntries_childFailedToOpen_wipesParentAndFinishesAtTheNextColdUnlock() = journalScenario { fixture ->
        val childSlot = enteredDuressThenBrokeChild(fixture)

        fixture.reloginOk(MAIN_PASSWORD)
        assertOnlyTheCurrentEntry(fixture)
        assertTrue(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))

        fixture.vault.close()
        fixture.restoreSafe(childSlot)
        fixture.reloginOk(MAIN_PASSWORD)

        assertFalse(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))
        assertTrue(fixture.records(1).isEmpty())
    }

    @Test
    fun unlock_warmAfterAChildFailedToOpen_reopensTheChainFinishesTheWipeAndReloadsAccounts() =
        journalScenario { fixture ->
            val childSlot = enteredDuressThenBrokeChild(fixture)
            fixture.reloginOk(MAIN_PASSWORD)
            assertTrue(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))

            fixture.vault.lock()
            fixture.restoreSafe(childSlot)
            assertEquals(UnlockOutcome.Success, fixture.vault.unlock(password(MAIN_PASSWORD)))
            fixture.settle()

            assertFalse(fixture.safe(0).dao.flag(JournalSetting.PENDING_WIPE))
            assertTrue(fixture.records(1).isEmpty())
            assertEquals(1, fixture.levels.visibleAccounts()[DURESS_ACCOUNT])
        }

    /** P wipes on a duress entry; C logs its own entries, P logs successful ones. */
    private suspend fun wipeReady(fixture: JournalFixture) {
        fixture.levels.mainWithDuress()
        fixture.parts.settings.setLogIntoDuressModeEnabled(0, true)
        fixture.parts.settings.setDeleteAllAuthDataOnDuressEnabled(0, true)
        fixture.parts.settings.setLogSuccessfulLoginsEnabled(0, true)
        fixture.reloginOk(MAIN_PASSWORD)
        assertEquals(1, fixture.records(0).size)
    }

    /** The child's slot. */
    private suspend fun enteredDuressThenBrokeChild(fixture: JournalFixture): Int {
        wipeReady(fixture)
        fixture.reloginOk(DURESS_PASSWORD)
        return fixture.safe(0).link.slot.also { fixture.hideSafe(it) }
    }

    /** The wipe runs before this unlock records itself. */
    private suspend fun assertOnlyTheCurrentEntry(fixture: JournalFixture) =
        assertEquals(listOf(JournalRecordKind.SUCCESS), fixture.records(0).map { it.kind })

    private suspend fun assertNoRingEntryOpens(fixture: JournalFixture) {
        val mailbox = fixture.vault.mailbox
        fixture.session.chain.safes.forEach { safe ->
            val inboxKey = checkNotNull(safe.dao.bytes(JournalSetting.INBOX_KEY))
            assertTrue(mailbox.read(MailboxRing.FAILED) { JournalInboxCrypto.openSlot(it, safe.link.slot, inboxKey) }
                .isEmpty())
            safe.dao.bytes(JournalSetting.CHILD_NOTIFY_PRIVATE)?.let { notifyKey ->
                assertTrue(mailbox.read(MailboxRing.NOTIFY) { JournalInboxCrypto.openSingle(it, notifyKey) }.isEmpty())
            }
        }
    }
}
