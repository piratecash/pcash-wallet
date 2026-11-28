package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.ChangePasswordOutcome
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.DuressAvailability
import cash.p.terminal.shared.desktop.vault.HiddenPasswordResolution
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.vault.UnlockResult
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val JOURNAL_PASSWORD = "Journal-password6"
private const val ERASE_PASSWORD = "Erase-contacts5"
private const val FREE_PASSWORD = "Free-password7"
private const val NEW_ERASE_PASSWORD = "Erase-contacts8"

class JournalPasswordsTest {

    @Test
    fun everySetter_journalOrErasePassword_refusesItAsInUse() = journalScenario { fixture ->
        fixture.levels.create()
        val secrets = fixture.parts.secrets
        assertEquals(JournalSecretOutcome.SUCCESS, secrets.setJournalPassword(password(JOURNAL_PASSWORD)))
        assertEquals(JournalSecretOutcome.SUCCESS, secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))

        listOf(JOURNAL_PASSWORD, ERASE_PASSWORD, MAIN_PASSWORD).forEach { taken ->
            assertEquals(LevelOutcome.PASSWORD_IN_USE, fixture.vault.setDuressPassword(password(taken)), taken)
            assertEquals(LevelOutcome.PASSWORD_IN_USE, fixture.vault.createHiddenWallet(password(taken)), taken)
            assertEquals(
                HiddenPasswordResolution.IN_USE,
                fixture.vault.resolveHiddenPassword(password(taken)),
                taken,
            )
            assertEquals(JournalSecretOutcome.PASSWORD_IN_USE, secrets.setJournalPassword(password(taken)), taken)
            assertEquals(JournalSecretOutcome.PASSWORD_IN_USE, secrets.setDeleteContactsPassword(password(taken)), taken)
            assertEquals(
                JournalSecretOutcome.PASSWORD_IN_USE,
                secrets.changeDeleteContactsPassword(password(ERASE_PASSWORD), password(taken)),
                taken,
            )
        }
        assertEquals(
            ChangePasswordOutcome.PASSWORD_IN_USE,
            fixture.vault.changePassword(password(MAIN_PASSWORD), password(JOURNAL_PASSWORD)),
        )
        assertEquals(
            ChangePasswordOutcome.PASSWORD_IN_USE,
            fixture.vault.changePassword(password(MAIN_PASSWORD), password(ERASE_PASSWORD)),
        )
    }

    @Test
    fun journalSetters_transactionsHidePassword_refuseItAsInUse() = journalScenario { fixture ->
        fixture.levels.create()
        fixture.transactionsHidePassword = FREE_PASSWORD
        val secrets = fixture.parts.secrets

        assertEquals(JournalSecretOutcome.PASSWORD_IN_USE, secrets.setDeleteContactsPassword(password(FREE_PASSWORD)))
        assertEquals(JournalSecretOutcome.PASSWORD_IN_USE, secrets.setJournalPassword(password(FREE_PASSWORD)))
    }

    @Test
    fun everySetter_freePasswordOrTheCurrentOne_stillSucceeds() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setJournalPassword(password(JOURNAL_PASSWORD)))

        assertEquals(HiddenPasswordResolution.NEW, fixture.vault.resolveHiddenPassword(password(FREE_PASSWORD)))
        assertEquals(
            ChangePasswordOutcome.SUCCESS,
            fixture.vault.changePassword(password(MAIN_PASSWORD), password(MAIN_PASSWORD)),
        )
        assertEquals(LevelOutcome.SUCCESS, fixture.vault.setDuressPassword(password(FREE_PASSWORD)))
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.verifyJournalPassword(password(JOURNAL_PASSWORD)))
    }

    @Test
    fun levelSetters_incompleteChain_refuseWithFailed() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        fixture.hideSafe(fixture.safe(0).link.slot)
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(
            ChangePasswordOutcome.FAILED,
            fixture.vault.changePassword(password(MAIN_PASSWORD), password(FREE_PASSWORD)),
        )
        assertEquals(LevelOutcome.FAILED, fixture.vault.createHiddenWallet(password(FREE_PASSWORD)))
        val secrets = fixture.parts.secrets
        assertEquals(JournalSecretOutcome.FAILED, secrets.setJournalPassword(password(FREE_PASSWORD)))
        assertEquals(JournalSecretOutcome.FAILED, secrets.setDeleteContactsPassword(password(FREE_PASSWORD)))
        assertFalse(secrets.isJournalSecretSet())
        assertFalse(secrets.isDeleteContactsSecretSet())
        assertEquals(DuressAvailability.UNAVAILABLE, fixture.vault.duressAvailability())
        assertEquals(HiddenPasswordResolution.FAILED, fixture.vault.resolveHiddenPassword(password(FREE_PASSWORD)))
    }

    @Test
    fun unlock_erasePasswordAtTheLockScreen_erasesContactsAndCountsAsAFailedAttempt() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        val contacts = writeContacts(fixture)
        val before = fixture.vault.mailbox.nextSeq(MailboxRing.FAILED)
        fixture.vault.lock()

        val outcome = fixture.vault.unlock(password(ERASE_PASSWORD))
        fixture.settle()

        assertIs<UnlockOutcome.WrongPassword>(outcome)
        assertFalse(contacts.exists())
        verify { fixture.contacts.clear(true) }
        assertEquals(before + 1, fixture.vault.mailbox.nextSeq(MailboxRing.FAILED))
    }

    @Test
    fun unlock_erasePasswordWithNoStorageSession_stillClearsTheContactsCache() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        val contacts = writeContacts(fixture)
        fixture.vault.close()

        fixture.vault.unlock(password(ERASE_PASSWORD))

        assertFalse(contacts.exists())
        verify { fixture.contacts.clear(true) }
    }

    @Test
    fun verifyAndChangePassword_erasePassword_eraseNothing() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        val contacts = writeContacts(fixture)

        assertIs<UnlockOutcome.WrongPassword>(fixture.vault.verifyPassword(password(ERASE_PASSWORD)))
        assertEquals(
            ChangePasswordOutcome.WRONG_PASSWORD,
            fixture.vault.changePassword(password(ERASE_PASSWORD), password(FREE_PASSWORD)),
        )

        assertTrue(contacts.exists())
        verify(exactly = 0) { fixture.contacts.clear(any()) }
    }

    @Test
    fun disableDeleteContactsPassword_wrongThenRight_onlyTheRightOneDisablesIt() = journalScenario { fixture ->
        fixture.levels.create()
        val secrets = fixture.parts.secrets
        assertEquals(JournalSecretOutcome.SUCCESS, secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))

        assertEquals(JournalSecretOutcome.WRONG_PASSWORD, secrets.disableDeleteContactsPassword(password(FREE_PASSWORD)))
        assertTrue(secrets.isDeleteContactsSecretSet())
        assertEquals(JournalSecretOutcome.SUCCESS, secrets.disableDeleteContactsPassword(password(ERASE_PASSWORD)))

        assertFalse(secrets.isDeleteContactsSecretSet())
        assertFalse(fixture.levels.passwordVault.opensAny(password(ERASE_PASSWORD)))
    }

    @Test
    fun setDeleteContactsPassword_copyWriteFails_offAndNothingErases() = journalScenario { fixture ->
        fixture.levels.create()
        failCopyWrites(fixture, "INSERT")

        assertEquals(JournalSecretOutcome.FAILED, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))

        assertSwitchMatchesTheLockScreen(fixture, erasing = null)
    }

    @Test
    fun setDeleteContactsPassword_tagPublishFails_offAndNothingErases() = journalScenario { fixture ->
        fixture.levels.create()

        val outcome = fixture.withVaultDirectoryReadOnly {
            fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD))
        }

        assertEquals(JournalSecretOutcome.FAILED, outcome)
        assertSwitchMatchesTheLockScreen(fixture, erasing = null)
    }

    @Test
    fun disableDeleteContactsPassword_tagClearFails_staysOnAndStillErases() = journalScenario { fixture ->
        fixture.levels.create()
        val secrets = fixture.parts.secrets
        assertEquals(JournalSecretOutcome.SUCCESS, secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))

        val outcome = fixture.withVaultDirectoryReadOnly { secrets.disableDeleteContactsPassword(password(ERASE_PASSWORD)) }

        assertEquals(JournalSecretOutcome.FAILED, outcome)
        assertSwitchMatchesTheLockScreen(fixture, erasing = ERASE_PASSWORD)
    }

    @Test
    fun disableDeleteContactsPassword_copyDeleteFails_offAndNothingErases() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        failCopyWrites(fixture, "DELETE")

        val outcome = fixture.parts.secrets.disableDeleteContactsPassword(password(ERASE_PASSWORD))

        assertEquals(JournalSecretOutcome.SUCCESS, outcome)
        assertSwitchMatchesTheLockScreen(fixture, erasing = null)
    }

    @Test
    fun changeDeleteContactsPassword_copyWriteFails_staysOnWithTheOldPassword() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        failCopyWrites(fixture, "INSERT")

        val outcome = fixture.parts.secrets.changeDeleteContactsPassword(password(ERASE_PASSWORD), password(NEW_ERASE_PASSWORD))

        assertEquals(JournalSecretOutcome.FAILED, outcome)
        assertSwitchMatchesTheLockScreen(fixture, erasing = ERASE_PASSWORD)
    }

    @Test
    fun changeDeleteContactsPassword_tagPublishFails_staysOnWithTheOldPassword() = journalScenario { fixture ->
        fixture.levels.create()
        val secrets = fixture.parts.secrets
        assertEquals(JournalSecretOutcome.SUCCESS, secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))

        val outcome = fixture.withVaultDirectoryReadOnly {
            secrets.changeDeleteContactsPassword(password(ERASE_PASSWORD), password(NEW_ERASE_PASSWORD))
        }

        assertEquals(JournalSecretOutcome.FAILED, outcome)
        assertSwitchMatchesTheLockScreen(fixture, erasing = ERASE_PASSWORD)
    }

    @Test
    fun changeDeleteContactsPassword_lastCopyTidyFails_onWithTheNewPassword() = journalScenario { fixture ->
        fixture.levels.create()
        assertEquals(JournalSecretOutcome.SUCCESS, fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        // Only the write that leaves a single copy: the one after the new tag is published.
        failCopyWrites(fixture, "INSERT", condition = "instr(NEW.value, ',') = 0")

        val outcome = fixture.parts.secrets.changeDeleteContactsPassword(password(ERASE_PASSWORD), password(NEW_ERASE_PASSWORD))

        assertEquals(JournalSecretOutcome.SUCCESS, outcome)
        assertSwitchMatchesTheLockScreen(fixture, erasing = NEW_ERASE_PASSWORD)
    }

    @Test
    fun changePassword_erasePasswordLeftInAChildByAFailedStep_refusesItAsInUse() = journalScenario { fixture ->
        fixture.levels.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        val outcome = fixture.withVaultDirectoryReadOnly {
            fixture.parts.secrets.setDeleteContactsPassword(password(ERASE_PASSWORD))
        }
        assertEquals(JournalSecretOutcome.FAILED, outcome)
        fixture.reloginOk(MAIN_PASSWORD)

        assertEquals(
            ChangePasswordOutcome.PASSWORD_IN_USE,
            fixture.vault.changePassword(password(MAIN_PASSWORD), password(ERASE_PASSWORD)),
        )
        assertEquals(JournalSecretOutcome.PASSWORD_IN_USE, fixture.parts.secrets.setJournalPassword(password(ERASE_PASSWORD)))
    }

    @Test
    fun setDeleteContactsPassword_ownCopyLeftByAFailedPublish_retrySucceeds() = journalScenario { fixture ->
        fixture.levels.create()
        val secrets = fixture.parts.secrets
        fixture.withVaultDirectoryReadOnly { secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)) }

        assertEquals(JournalSecretOutcome.SUCCESS, secrets.setDeleteContactsPassword(password(ERASE_PASSWORD)))
        assertSwitchMatchesTheLockScreen(fixture, erasing = ERASE_PASSWORD)
    }

    /** The switch shows on exactly when the lock screen erases contacts with [erasing], and no other password erases. */
    private fun assertSwitchMatchesTheLockScreen(fixture: JournalFixture, erasing: String?) {
        assertEquals(erasing != null, fixture.parts.secrets.isDeleteContactsSecretSet(), "switch")
        listOf(ERASE_PASSWORD, NEW_ERASE_PASSWORD).forEach {
            val erases = fixture.levels.passwordVault.unlock(password(it)) == UnlockResult.EraseContacts
            assertEquals(it == erasing, erases, it)
        }
    }

    /** Makes every [operation] on the root's erase-tag copy that meets [condition] fail. */
    private suspend fun failCopyWrites(fixture: JournalFixture, operation: String, condition: String = "1") {
        val root = fixture.safe(0)
        val key = root.link.key.copyOf()
        val file = fixture.levels.files.safeFile(root.link.slot)
        val row = if (operation == "DELETE") "OLD" else "NEW"
        fixture.vault.close()
        fixture.execute(
            file,
            key,
            "CREATE TRIGGER fail_copy BEFORE $operation ON LevelSetting " +
                "WHEN $row.`key` = '${JournalSetting.ERASE_TAG}' AND $condition BEGIN SELECT RAISE(ABORT, 'test'); END",
        )
        fixture.reloginOk(MAIN_PASSWORD)
    }

    private fun writeContacts(fixture: JournalFixture) =
        fixture.levels.files.contactsFile.apply {
            parentFile.mkdirs()
            writeText("contacts")
        }
}
