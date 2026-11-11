package cash.p.terminal.shared.desktop.vault

import cash.p.beam.BeamWalletSession
import cash.p.beam.RestoreSource
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BeamAccountDeletionPreflight
import cash.p.terminal.core.managers.BeamDeletionState
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamSessionFactory
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.storage.RestoreSettingDao
import cash.p.terminal.core.storage.SingleDatabaseStorageRouter
import cash.p.terminal.shared.desktop.DesktopBeamResetMarker
import cash.p.terminal.shared.desktop.session.mnemonicAccount
import cash.p.terminal.wallet.AccountOrigin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBeamKitDatabaseKeyProviderTest {

    private val fixture = VaultLevelsFixture()
    private val deleted = mutableSetOf<String>()
    private val accounts = mockk<AccountsDao> {
        every { isAvailable(any()) } answers { firstArg<String>() !in deleted }
        every { getDeletedIds() } answers { deleted.toList() }
        every { getIds() } returns emptyList()
    }
    private val restore = mockk<RestoreSettingDao>(relaxed = true)
    private val databases = mockk<AppDatabaseProvider> {
        every { database.accountsDao() } returns accounts
        every { database.restoreSettingDao() } returns restore
    }
    private var locator = locator()
    private var marker = DesktopBeamResetMarker(fixture.vault)
    private var keys = keyProvider()
    private val storageId = locator.storageId(ACCOUNT_ID, BeamNetwork.Mainnet)

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun keyForInitialization_firstKeyForStorage_isNewOnceAndRecordSurvivesReopen() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)

        val first = keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet)
        val second = keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet)
        relogin()
        val afterReopen = keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet)

        assertTrue(first.isNew)
        assertEquals(32, first.bytes.size)
        assertFalse(second.isNew)
        assertContentEquals(first.bytes, second.bytes)
        assertFalse(afterReopen.isNew)
        assertContentEquals(first.bytes, afterReopen.bytes)
        assertEquals(setOf(storageId), keys.storageIds())
    }

    @Test
    fun keyForInitialization_databaseWithoutRecord_isNotNewAndRecordsIt() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        writeDatabase()

        assertFalse(keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet).isNew)
        assertEquals(setOf(storageId), keys.storageIds())
    }

    @Test
    fun keyForInitialization_lockedVault_throwsWithoutRecord() {
        assertFailsWith<StorageLockedException> { keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet) }

        assertFalse(keys.hasAnyKey())
    }

    @Test
    fun keyForInitialization_deletedOrResetPending_throwsWithoutRecord() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        deleted += ACCOUNT_ID
        assertFailsWith<IllegalStateException> { keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet) }
        deleted.clear()
        marker.set()

        assertFailsWith<IllegalStateException> { keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet) }

        assertFalse(keys.hasAnyKey())
    }

    @Test
    fun removeStorageKey_storageStillPresent_throwsAndKeepsRecordUntilErased() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet)
        writeDatabase()

        assertFailsWith<IllegalStateException> { keys.removeStorageKey(storageId) }
        assertTrue(keys.hasAnyKey())
        check(locator.storagePath(ACCOUNT_ID).deleteRecursively())
        keys.removeStorageKey(storageId)

        assertFalse(keys.hasAnyKey())
    }

    @Test
    fun open_createdAccountWithoutRecord_createsNew() = runTest {
        fixture.create()
        val sdk = sdk()
        val account = mnemonicAccount(isBackedUp = true)
        fixture.router.prepareAccountStore(account.id)

        sessionFactory(sdk).open(account, BeamNetwork.Mainnet)

        coVerify(exactly = 1) { sdk.createNew(any(), any(), any()) }
        coVerify(exactly = 0) { sdk.restore(any(), any(), any(), any()) }
    }

    @Test
    fun open_restoredAccountOrIssuedRecord_restoresFromSnapshot() = runTest {
        fixture.create()
        val sdk = sdk()
        val restored = mnemonicAccount(isBackedUp = true).copy(origin = AccountOrigin.Restored)
        val created = mnemonicAccount(isBackedUp = true)
        listOf(restored, created).forEach { fixture.router.prepareAccountStore(it.id) }
        keys.keyForInitialization(created.id, BeamNetwork.Mainnet)

        sessionFactory(sdk).open(restored, BeamNetwork.Mainnet)
        sessionFactory(sdk).open(created, BeamNetwork.Mainnet)

        coVerify(exactly = 2) { sdk.restore(any(), any(), any(), RestoreSource.SnapshotThenScan()) }
        coVerify(exactly = 0) { sdk.createNew(any(), any(), any()) }
    }

    @Test
    fun cleanupDeleted_issuedStorage_erasesStorageThenDropsRecord() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet)
        writeDatabase()
        deleted += ACCOUNT_ID

        preflight().cleanupDeleted(listOf(ACCOUNT_ID))

        assertFalse(locator.storagePath(ACCOUNT_ID).exists())
        assertFalse(keys.hasAnyKey())
    }

    @Test
    fun prepareExplicitReset_issuedStorage_dropsRecordsAndKeepsMarker() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        keys.keyForInitialization(ACCOUNT_ID, BeamNetwork.Mainnet)
        writeDatabase()

        preflight().prepareExplicitReset()

        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
        assertTrue(marker.isSet)
    }

    private fun locator(): BeamStorageLocator {
        val vault = fixture.vault
        return DesktopBeamStorageLocator(
            vault,
            DesktopKitPlatformContexts(fixture.files, vault),
            DesktopKitStorageNaming(fixture.files, vault),
        )
    }

    private fun keyProvider() =
        DesktopBeamKitDatabaseKeyProvider(fixture.vault, locator, BeamDeletionState(SingleDatabaseStorageRouter(databases), marker))

    private suspend fun relogin() {
        fixture.reloginOk()
        locator = locator()
        marker = DesktopBeamResetMarker(fixture.vault)
        keys = keyProvider()
    }

    private fun writeDatabase() {
        val database = locator.databaseFile(ACCOUNT_ID)
        check(database.parentFile.mkdirs())
        database.writeText("synthetic-ciphertext")
    }

    private fun sdk() = mockk<BeamSessionFactory.Factory> {
        coEvery { createNew(any(), any(), any()) } returns mockk<BeamWalletSession>()
        coEvery { restore(any(), any(), any(), any()) } returns mockk<BeamWalletSession>()
    }

    private fun TestScope.sessionFactory(sdk: BeamSessionFactory.Factory) = BeamSessionFactory(
        keys, locator, TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
        mockk<RestoreSettingsManager> { every { hasBeamRestoreIntent(any()) } returns false }, sdk,
    )

    private fun TestScope.preflight() = BeamAccountDeletionPreflight(
        locator, lazy { keys }, mockk(relaxed = true),
        TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
        BeamDeletionState(SingleDatabaseStorageRouter(databases), marker), lazy { mockk(relaxed = true) }, lazy { mockk(relaxed = true) },
    )

    private companion object {
        const val ACCOUNT_ID = "beam-desktop-account"
    }
}
