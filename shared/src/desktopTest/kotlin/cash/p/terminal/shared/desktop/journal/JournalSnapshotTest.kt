package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.OpenSafe
import cash.p.terminal.vault.JournalInboxCrypto
import cash.p.terminal.vault.PasswordVault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What one stolen safe and the vault directory show, with nothing else known. */
class JournalSnapshotTest {

    @Test
    fun duressChildSafe_comparedWithAStandaloneRoot_holdsTheSameKindsOfItems() {
        val standalone = settingKeys { fixture ->
            fixture.levels.create()
            fixture.reloginOk(MAIN_PASSWORD)
            fixture.safe(0)
        }
        val child = settingKeys { fixture ->
            fixture.levels.mainWithDuress()
            fixture.reloginOk(DURESS_PASSWORD)
            fixture.reloginOk(MAIN_PASSWORD)
            fixture.reloginOk(DURESS_PASSWORD)
            fixture.safe(0)
        }

        assertEquals(standalone, child)
    }

    @Test
    fun duressChildSafe_everyItem_derivesAndVerifiesNothingOfItsParent() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        fixture.reloginOk(MAIN_PASSWORD)
        val eraseOutcome = fixture.parts.secrets.setDeleteContactsPassword(password("Erase-contacts5"))
        assertEquals(JournalSecretOutcome.SUCCESS, eraseOutcome)
        val parent = fixture.safe(0)
        val child = fixture.safe(1)
        val parentValues = listOf(
            JournalSetting.JOURNAL_KEY,
            JournalSetting.INBOX_KEY,
            JournalSetting.NOTIFY_KEY,
            JournalSetting.ERASE_TAG,
        ).map { checkNotNull(parent.dao.setting(it)) { it } }.toSet()
        val parentSecrets = listOf(JournalSetting.INBOX_KEY, JournalSetting.CHILD_NOTIFY_PRIVATE)
            .mapNotNull { parent.dao.bytes(it) }
        val parentPublicKeys = parentSecrets.map { JournalInboxCrypto.publicKey(it).toHexString() }
        val childNotifyKey = checkNotNull(child.dao.bytes(JournalSetting.NOTIFY_KEY)).toHexString()
        val childSlot = child.link.slot
        val childKey = child.link.key.copyOf()
        fixture.vault.close()

        val childValues = fixture.safeSettings(childSlot, childKey).values
        assertTrue(childValues.none { it in parentValues }, "the child stores a value of its parent")
        // The one link is the public half of a pair the parent made for this child: it opens and proves nothing.
        assertEquals(listOf(childNotifyKey), childValues.filter { it in parentPublicKeys })
    }

    @Test
    fun notifyKeys_everyRootAndChild_matchNoMailboxHeaderKey() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.reloginOk(MAIN_PASSWORD)
        val headerKeys = List(PasswordVault.SLOT_COUNT) { fixture.vault.mailbox.publicKey(it).toHexString() }

        fixture.session.chain.safes.forEach { safe ->
            val notifyKey = checkNotNull(safe.dao.bytes(JournalSetting.NOTIFY_KEY)).toHexString()
            assertFalse(notifyKey in headerKeys)
        }
    }

    private fun settingKeys(scenario: suspend (JournalFixture) -> OpenSafe): Set<String> {
        var keys: Set<String> = emptySet()
        journalScenario { fixture ->
            val safe = scenario(fixture)
            val slot = safe.link.slot
            val key = safe.link.key.copyOf()
            fixture.vault.close()
            keys = fixture.safeSettings(slot, key).keys
        }
        return keys
    }
}
