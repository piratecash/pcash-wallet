package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.shared.desktop.DesktopMoneroWalletStorage
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopKitStorageNamingTest {
    private val fixture = VaultLevelsFixture()
    private val naming get() = DesktopKitStorageNaming(fixture.files, fixture.vault)
    private val contexts get() = DesktopKitPlatformContexts(fixture.files, fixture.vault)

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun resolverFor_sameAccountAfterALevelChange_namesTheSameFile() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val name = naming.resolverFor(MAIN_ACCOUNT).resolve(DEFAULT_NAME)
        val directory = contexts.bitcoinDatabaseDir(MAIN_ACCOUNT)

        fixture.router.setLevel(listOf(MAIN_ACCOUNT), 1)
        fixture.reloginOk(DURESS_PASSWORD)

        assertEquals(name, naming.resolverFor(MAIN_ACCOUNT).resolve(DEFAULT_NAME))
        assertEquals(directory, contexts.bitcoinDatabaseDir(MAIN_ACCOUNT))
    }

    @Test
    fun resolverFor_otherAccountOrName_neverCollides() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val resolver = naming.resolverFor(MAIN_ACCOUNT)
        val name = resolver.resolve(DEFAULT_NAME)

        assertNotEquals(name, resolver.resolve("$DEFAULT_NAME-other"))
        assertNotEquals(name, naming.resolverFor(DURESS_ACCOUNT).resolve(DEFAULT_NAME))
        assertTrue(name.matches(Regex("[0-9a-f]{32}")), name)
    }

    @Test
    fun walletsRoot_everyLevel_isTheSameDirectoryOutsideAnyAccount() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val root = DesktopMoneroWalletStorage(fixture.files, naming).walletsRoot()

        fixture.reloginOk(DURESS_PASSWORD)

        assertEquals(root, DesktopMoneroWalletStorage(fixture.files, naming).walletsRoot())
        assertEquals(fixture.files.kitsDirectory, root.parentFile)
        assertTrue(root.name.matches(Regex("[0-9a-f]{32}")), root.name)
    }

    @Test
    fun resolverFor_noOpenStore_throwsStorageLocked() = runBlocking<Unit> {
        fixture.create()
        assertFailsWith<StorageLockedException> { naming.resolverFor(MAIN_ACCOUNT) }

        fixture.vault.close()

        assertFailsWith<StorageLockedException> { naming.sharedResolver }
        assertFailsWith<StorageLockedException> { contexts.zcashBaseDir(MAIN_ACCOUNT) }
    }

    private companion object {
        const val DEFAULT_NAME = "ethereum-mainnet.db"
    }
}
