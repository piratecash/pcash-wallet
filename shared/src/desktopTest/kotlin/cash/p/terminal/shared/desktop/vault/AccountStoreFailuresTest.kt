package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.RestoreSettingsStorage
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val HEALTHY_ACCOUNT = "a-healthy"
private const val BROKEN_ACCOUNT = "b-broken"

class AccountStoreFailuresTest {
    private val fixture = VaultLevelsFixture()

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun unlock_storeDirectoryInaccessible_keepsThePointerAndTheNextUnlockOpensTheAccount() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(MAIN_ACCOUNT)
        fixture.writeKitRows(MAIN_ACCOUNT, "before")
        val directory = fixture.storeDirectory(MAIN_ACCOUNT)
        fixture.vault.close()

        withoutAccess(directory) {
            fixture.reloginOk()
            assertNull(fixture.router.account(MAIN_ACCOUNT))
            assertNull(fixture.router.levelOf(MAIN_ACCOUNT))
            assertEquals(1, fixture.pointerCount(MAIN_ACCOUNT))
            fixture.vault.close()
        }

        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
        assertEquals(1, fixture.store(MAIN_ACCOUNT).walletsDao().enabledCoins(MAIN_ACCOUNT).size)
    }

    @Test
    fun collapseLevelsAbove_lowerStoreInaccessible_movesItsPointerUpAndTheAccountReturns() = runBlocking<Unit> {
        fixture.mainWithDuress()
        fixture.writeKitRows(DURESS_ACCOUNT, "before")
        val directory = fixture.storeDirectory(DURESS_ACCOUNT)
        fixture.vault.close()

        withoutAccess(directory) {
            fixture.reloginOk()
            fixture.router.collapseLevelsAbove(0)
            assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
            fixture.vault.close()
        }

        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0), fixture.visibleAccounts())
        assertEquals(1, fixture.store(DURESS_ACCOUNT).walletsDao().enabledCoins(DURESS_ACCOUNT).size)
    }

    @Test
    fun prepareAccountStore_failureAfterThePointerCommit_retryWritesIntoTheStoreThePointerNames() = runBlocking<Unit> {
        fixture.create()
        var failed = false
        fixture.checkpoints.onReached = { step ->
            if (step == StorageCheckpoint.PREPARE_POINTER_WRITTEN && !failed) {
                failed = true
                error("transient read failure")
            }
        }
        assertFailsWith<IllegalStateException> { fixture.router.prepareAccountStore(MAIN_ACCOUNT) }

        val settings = RestoreSettingsStorage(fixture.router)
        settings.prepareForNewAccount(MAIN_ACCOUNT)
        settings.save(listOf(RestoreSettingRecord(MAIN_ACCOUNT, "zcash", "birthdayHeight", "42")))
        fixture.addAccount(MAIN_ACCOUNT)
        assertEquals(1, fixture.accountStoreDirectories())

        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
        val restored = RestoreSettingsStorage(fixture.router).restoreSettings(MAIN_ACCOUNT)
        assertEquals(listOf("42"), restored.map { it.value })
    }

    @Test
    fun prepareAccountStore_failureAfterThePointerCommit_closesTheStoreItOpened() = runBlocking<Unit> {
        fixture.create()
        fixture.checkpoints.onReached = { step ->
            if (step == StorageCheckpoint.PREPARE_POINTER_WRITTEN) error("transient read failure")
        }
        val driversBefore = fixture.driverFactory.drivers.size

        assertFailsWith<IllegalStateException> { fixture.router.prepareAccountStore(MAIN_ACCOUNT) }

        fixture.driverFactory.drivers.drop(driversBefore).forEach { (_, driver) -> verify { driver.close() } }
    }

    @Test
    fun prepareAccountStore_pointerOutlivedItsStore_recreatesTheStoreThePointerNames() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(MAIN_ACCOUNT)
        val directory = fixture.storeDirectory(MAIN_ACCOUNT)
        val key = fixture.storeKey(MAIN_ACCOUNT)
        fixture.vault.close()
        directory.deleteRecursively()
        fixture.reloginOk()
        assertNull(fixture.router.account(MAIN_ACCOUNT))

        fixture.addAccount(MAIN_ACCOUNT)
        fixture.writeKitRows(MAIN_ACCOUNT, "after")

        assertEquals(directory, fixture.storeDirectory(MAIN_ACCOUNT))
        assertContentEquals(key, fixture.storeKey(MAIN_ACCOUNT))
        fixture.reloginOk()
        assertEquals(1, fixture.store(MAIN_ACCOUNT).walletsDao().enabledCoins(MAIN_ACCOUNT).size)
    }

    @Test
    fun unlock_corruptStoreNextToAHealthyOne_opensTheHealthyAndClosesTheCorrupt() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(HEALTHY_ACCOUNT)
        fixture.addAccount(BROKEN_ACCOUNT)
        fixture.writeKitRows(HEALTHY_ACCOUNT, "before")
        val brokenKey = fixture.storeKey(BROKEN_ACCOUNT)
        val brokenDirectory = fixture.storeDirectory(BROKEN_ACCOUNT)
        fixture.vault.close()
        val brokenFile = assertNotNull(brokenDirectory.listFiles()).first { !it.name.endsWith(".lck") }
        brokenFile.writeBytes(ByteArray(brokenFile.length().toInt()) { 7 })

        fixture.reloginOk()

        assertEquals(mapOf(HEALTHY_ACCOUNT to 0), fixture.visibleAccounts())
        assertEquals(1, fixture.store(HEALTHY_ACCOUNT).walletsDao().enabledCoins(HEALTHY_ACCOUNT).size)
        assertNull(fixture.router.account(BROKEN_ACCOUNT))
        assertEquals(1, fixture.pointerCount(BROKEN_ACCOUNT))
        verify { fixture.driverFactory.driverFor(brokenKey).close() }
    }

    @Test
    fun unlock_fatalErrorOpeningALaterStore_closesTheStoresAlreadyOpen() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(HEALTHY_ACCOUNT)
        fixture.addAccount(BROKEN_ACCOUNT)
        val healthyKey = fixture.storeKey(HEALTHY_ACCOUNT)
        fixture.driverFactory.failFor = fixture.storeKey(BROKEN_ACCOUNT)

        assertIs<UnlockOutcome.StorageUnavailable>(fixture.relogin())

        verify { fixture.driverFactory.driverFor(healthyKey).close() }
        fixture.driverFactory.failFor = null
    }

    private suspend fun withoutAccess(directory: File, block: suspend () -> Unit) {
        val path = directory.toPath()
        assumeTrue(Files.getFileAttributeView(path, PosixFileAttributeView::class.java) != null)
        val permissions = Files.getPosixFilePermissions(path)
        Files.setPosixFilePermissions(path, emptySet<PosixFilePermission>())
        try {
            block()
        } finally {
            Files.setPosixFilePermissions(path, permissions)
        }
    }
}
