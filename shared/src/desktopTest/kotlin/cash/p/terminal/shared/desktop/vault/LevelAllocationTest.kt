package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelAllocationTest {
    private val fixture = VaultLevelsFixture()
    private val vault get() = fixture.vault

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun setDuressPassword_noDuressYet_takesAPlaceAndMovesTheWholePoolDown() = runBlocking<Unit> {
        fixture.create()
        val before = fixture.session.chain.root.meta

        fixture.setDuress()

        val root = fixture.session.chain.root.meta
        val duress = fixture.session.chain.safes[1]
        assertTrue(root.slotPool.isEmpty())
        assertTrue(root.envelopePool.isEmpty())
        assertEquals(before.slotPool, duress.meta.slotPool + duress.link.slot)
        assertEquals(before.envelopePool, duress.meta.envelopePool + duress.link.envelopeIndex)
        assertEquals(duress.link.slot, root.child?.slot)
        assertFalse(duress.meta.isHiddenRoot)
    }

    @Test
    fun setDuressPassword_duressExists_changesItsPasswordAndKeepsItsData() = runBlocking<Unit> {
        fixture.mainWithDuress()

        fixture.setDuress(NEW_DURESS_PASSWORD)

        assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(DURESS_PASSWORD))
        fixture.reloginOk(NEW_DURESS_PASSWORD)
        assertEquals(mapOf(DURESS_ACCOUNT to 0), fixture.visibleAccounts())
    }

    @Test
    fun setDuressPassword_passwordOpensAnEnvelope_isInUse() = runBlocking<Unit> {
        fixture.create()

        assertEquals(LevelOutcome.PASSWORD_IN_USE, vault.setDuressPassword(password(MAIN_PASSWORD)))
        assertNull(fixture.session.chain.root.meta.child)
    }

    @Test
    fun createHidden_passwordOpensAnEnvelope_isInUse() = runBlocking<Unit> {
        fixture.create()

        assertEquals(LevelOutcome.PASSWORD_IN_USE, vault.createHiddenWallet(password(MAIN_PASSWORD)))
    }

    @Test
    fun createHidden_rootWithDuress_isNoLevelOfTheChainAndOpensEmpty() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val childBefore = fixture.session.chain.root.meta.child?.slot

        fixture.createHidden()

        assertEquals(childBefore, fixture.session.chain.root.meta.child?.slot)
        assertEquals(2, fixture.session.chain.safes.size)
        fixture.reloginOk(HIDDEN_PASSWORD)
        assertTrue(fixture.visibleAccounts().isEmpty())
        assertEquals(DuressAvailability.UNAVAILABLE, vault.duressAvailability())
    }

    @Test
    fun createHidden_rootWithItsOwnPool_splitsTheRemainderInHalfAndLosesNoPlace() = runBlocking<Unit> {
        fixture.create()
        val before = fixture.session.chain.root.meta

        fixture.createHidden()

        val creator = fixture.session.chain.root.meta
        fixture.reloginOk(HIDDEN_PASSWORD)
        val hiddenSafe = fixture.session.chain.root
        val hidden = hiddenSafe.meta
        assertSplit(before.slotPool, hiddenSafe.link.slot, creator.slotPool, hidden.slotPool)
        assertSplit(before.envelopePool, hiddenSafe.link.envelopeIndex, creator.envelopePool, hidden.envelopePool)
    }

    @Test
    fun createHidden_placeFromTheDescendant_splitsTheDescendantsRemainderAndLeavesTheRootAlone() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val rootBefore = fixture.session.chain.root.meta
        val duressBefore = fixture.session.chain.safes[1].meta

        fixture.createHidden()

        val root = fixture.session.chain.root.meta
        val duress = fixture.session.chain.safes[1].meta
        assertEquals(rootBefore.slotPool, root.slotPool)
        assertEquals(rootBefore.envelopePool, root.envelopePool)
        assertNotNull(root.child)
        fixture.reloginOk(HIDDEN_PASSWORD)
        val hiddenSafe = fixture.session.chain.root
        assertSplit(duressBefore.slotPool, hiddenSafe.link.slot, duress.slotPool, hiddenSafe.meta.slotPool)
        assertSplit(
            duressBefore.envelopePool,
            hiddenSafe.link.envelopeIndex,
            duress.envelopePool,
            hiddenSafe.meta.envelopePool,
        )
    }

    @Test
    fun setDuressPassword_afterAHiddenWallet_worksAndTakesTheWholeRemainder() = runBlocking<Unit> {
        fixture.create()
        fixture.createHidden()
        val before = fixture.session.chain.root.meta

        fixture.setDuress()

        val root = fixture.session.chain.root.meta
        val duress = fixture.session.chain.safes[1]
        assertTrue(root.slotPool.isEmpty())
        assertEquals(before.slotPool, duress.meta.slotPool + duress.link.slot)
        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(DuressAvailability.NOT_SET, vault.duressAvailability())
    }

    @Test
    fun createHidden_afterAHiddenWallet_createsASecondOneWithItsOwnPlaces() = runBlocking<Unit> {
        fixture.create()
        fixture.createHidden()

        fixture.createHidden(NESTED_PASSWORD)

        fixture.reloginOk(HIDDEN_PASSWORD)
        val first = fixture.session.chain.root.let { it.meta.slotPool + it.link.slot }
        fixture.reloginOk(NESTED_PASSWORD)
        val second = fixture.session.chain.root.let { it.meta.slotPool + it.link.slot }
        assertTrue(first.intersect(second).isEmpty())
    }

    @Test
    fun createHidden_repeatedUntilThePoolIsGone_reportsTheLimitAndEveryPasswordStillOpens() = runBlocking<Unit> {
        fixture.create()
        val passwords = mutableListOf<String>()
        while (vault.createHiddenWallet(password("Hidden-password-${passwords.size}")) == LevelOutcome.SUCCESS) {
            passwords += "Hidden-password-${passwords.size}"
        }

        assertTrue(passwords.size >= 2, "$passwords")
        assertEquals(LevelOutcome.LIMIT_REACHED, vault.setDuressPassword(password(DURESS_PASSWORD)))
        val slots = passwords.map { hidden ->
            fixture.reloginOk(hidden)
            fixture.session.chain.root.link.slot
        }
        assertEquals(slots.size, slots.toSet().size)
    }

    @Test
    fun duressAvailability_eachKindOfRoot_followsTheHiddenFlagOnly() = runBlocking<Unit> {
        fixture.create()
        assertEquals(DuressAvailability.NOT_SET, vault.duressAvailability())

        fixture.setDuress()
        assertEquals(DuressAvailability.SET, vault.duressAvailability())
        fixture.createHidden()

        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(DuressAvailability.NOT_SET, vault.duressAvailability())

        fixture.reloginOk(HIDDEN_PASSWORD)
        assertEquals(DuressAvailability.UNAVAILABLE, vault.duressAvailability())
        assertEquals(LevelOutcome.FAILED, vault.setDuressPassword(password(NESTED_PASSWORD)))
    }

    @Test
    fun resolveHiddenPassword_eachKindOfPassword_answersAsThePlanSays() = runBlocking<Unit> {
        fixture.mainWithDuress()
        fixture.createHidden()

        assertEquals(HiddenPasswordResolution.NEW, vault.resolveHiddenPassword(password(NESTED_PASSWORD)))
        assertEquals(HiddenPasswordResolution.IN_USE, vault.resolveHiddenPassword(password(MAIN_PASSWORD)))
        assertEquals(HiddenPasswordResolution.IN_USE, vault.resolveHiddenPassword(password(DURESS_PASSWORD)))
        assertEquals(HiddenPasswordResolution.REUSE_EMPTY, vault.resolveHiddenPassword(password(HIDDEN_PASSWORD)))

        fixture.reloginOk(HIDDEN_PASSWORD)
        assertEquals(DuressAvailability.UNAVAILABLE, vault.duressAvailability())
        fixture.addAccount("hidden-account")
        fixture.reloginOk()
        assertEquals(HiddenPasswordResolution.IN_USE, vault.resolveHiddenPassword(password(HIDDEN_PASSWORD)))
    }

    @Test
    fun resolveHiddenPassword_emptyDuressOfAnotherRoot_isReusableAndKeepsItsFlag() = runBlocking<Unit> {
        fixture.create()
        fixture.setDuress()
        fixture.createHidden()
        fixture.reloginOk(HIDDEN_PASSWORD)

        assertEquals(HiddenPasswordResolution.REUSE_EMPTY, vault.resolveHiddenPassword(password(DURESS_PASSWORD)))

        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(DuressAvailability.NOT_SET, vault.duressAvailability())
    }

    @Test
    fun setDuressPassword_envelopeCannotBeSealed_rollsBackAndLeavesTheSlotNoise() = runBlocking<Unit> {
        fixture.create()
        val before = fixture.session.chain.root.meta
        val namesBefore = fixture.vaultDirectoryNames()
        val directory = fixture.dataDirectory.toPath()
        assumeTrue(Files.getFileAttributeView(directory, PosixFileAttributeView::class.java) != null)
        val permissions = Files.getPosixFilePermissions(directory)
        Files.setPosixFilePermissions(
            directory,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
        )

        val outcome = try {
            vault.setDuressPassword(password(DURESS_PASSWORD))
        } finally {
            Files.setPosixFilePermissions(directory, permissions)
        }

        assertEquals(LevelOutcome.FAILED, outcome)
        val after = fixture.session.chain.root.meta
        assertEquals(before.slotPool, after.slotPool)
        assertEquals(before.envelopePool, after.envelopePool)
        assertNull(after.child)
        assertEquals(1, fixture.session.chain.safes.size)
        assertEquals(namesBefore, fixture.vaultDirectoryNames())
        assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(DURESS_PASSWORD))
        fixture.reloginOk()
        assertNotNull(vault.openSession())
    }

    private fun assertSplit(before: Set<Int>, taken: Int, kept: Set<Int>, given: Set<Int>) {
        assertTrue(kept.intersect(given).isEmpty(), "$kept $given")
        assertEquals(before, kept + given + taken)
        assertTrue(kept.size - given.size in 0..1, "$kept $given")
    }

    private companion object {
        const val NEW_DURESS_PASSWORD = "Duress-password9"
    }
}
