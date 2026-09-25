package cash.p.terminal.core.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.RestoreSettingDao
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [27])
@OptIn(ExperimentalCoroutinesApi::class)
class BeamAccountDeletionPreflightTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val context = mockk<Context>()
    private val accounts = mockk<AccountsDao>()
    private val restore = mockk<RestoreSettingDao>(relaxed = true)
    private val database = mockk<AppDatabase> {
        every { accountsDao() } returns accounts
        every { restoreSettingDao() } returns restore
    }
    private val existing = mutableSetOf("first", "second", "hidden")
    private val deleted = mutableSetOf<String>()
    private val owner = mockk<BeamSessionOwner>(relaxed = true)
    private val adapters = mockk<IAdapterManager>(relaxed = true)
    private val wallets = mockk<IEnabledWalletStorage>(relaxed = true)
    private val encryption = mockk<IEncryptionManager> {
        every { encrypt(any()) } answers { "synthetic:" + firstArg<String>() }
        every { decrypt(any()) } answers { firstArg<String>().removePrefix("synthetic:") }
    }
    private lateinit var state: BeamDeletionState
    private lateinit var locator: BeamStorageLocator
    private lateinit var keys: BeamDatabaseKeyProvider

    @Before
    fun setUp() {
        every { context.noBackupFilesDir } returns temporaryFolder.root
        every { context.getSharedPreferences(any(), any()) } answers {
            application.getSharedPreferences(firstArg(), secondArg())
        }
        preferences("beam_database_keys").edit().clear().commit()
        preferences("beam_deletion").edit().clear().commit()
        every { accounts.getIds() } answers { existing.toList() }
        every { accounts.getDeletedIds() } answers { deleted.toList() }
        every { accounts.isAvailable(any()) } answers { firstArg<String>() in existing - deleted }
        every { owner.current } returns null
        state = BeamDeletionState(context, database)
        locator = spyk(BeamStorageLocator(context))
        keys = spyk(BeamDatabaseKeyProvider(context, locator, encryption, state))
    }

    @Test
    fun cleanupDeleted_emptyBeam_succeedsOnlyWithTombstone() = runTest {
        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }
        verify { listOf(owner, adapters, encryption) wasNot Called }
        deleted += "first"

        guard().ensureCanDelete(listOf("first"))
        guard().cleanupDeleted(listOf("first"))

        coVerifyOrder {
            owner.fenceDeletion(listOf("first"))
            adapters.stopAdapters(listOf("first"), BlockchainType.Beam)
            owner.drainDeleted(listOf("first"))
            restore.deleteBeam(listOf("first"))
        }
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
    }

    @Test
    fun cleanupDeleted_accountScope_preservesSiblingKeyDatabaseAndRestoreIntent() = runTest {
        val firstKey = keys.keyFor("first")
        val secondKey = keys.keyFor("second")
        assertFalse(firstKey.contentEquals(secondKey))
        artifacts("first")
        artifacts("second")
        deleted += "first"

        guard().cleanupDeleted(listOf("first"))

        assertFalse(locator.storagePath("first").exists())
        assertFalse(keys.hasKey("first"))
        assertContentEquals(secondKey, keys.keyFor("second"))
        assertTrue(locator.databaseFile("second").readText() == "synthetic-ciphertext")
        assertTrue(File(locator.storagePath("second"), ".beam-recovery/recovery.bin").exists())
        verify(exactly = 0) { restore.deleteBeam(match { "second" in it }) }
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
    }

    @Test
    fun cleanupDeleted_drainSuspended_doesNotEraseUntilOwnerHasClosed() = runTest {
        prepareDeleted()
        val drained = CompletableDeferred<Unit>()
        coEvery { owner.drainDeleted(any()) } coAnswers { drained.await() }
        val cleanup = async { guard().cleanupDeleted(listOf("first")) }
        runCurrent()

        assertFalse(cleanup.isCompleted)
        assertRetained()
        drained.complete(Unit)
        cleanup.await()
        assertFalse(locator.storagePath("first").exists())
        assertFalse(keys.hasKey("first"))
    }

    @Test
    fun cleanupDeleted_stopFailure_preservesDatabaseWrapperAndLinkage() = runTest {
        prepareDeleted()
        coEvery { adapters.stopAdapters(any(), BlockchainType.Beam) } throws IOException("stop")

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertRetained()
        coVerify(exactly = 0) { owner.drainDeleted(any()) }
    }

    @Test
    fun cleanupDeleted_closeFailure_preservesDatabaseWrapperAndLinkage() = runTest {
        prepareDeleted()
        coEvery { owner.drainDeleted(any()) } throws IOException("close")

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertRetained()
    }

    @Test
    fun cleanupDeleted_eraseFailure_preservesWrapperAndLinkage() = runTest {
        prepareDeleted()
        every { locator.erase(any()) } throws IOException("erase")

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertRetained()
    }

    @Test
    fun cleanupDeleted_partialEraseFailure_retainsKeyAndBlocksRestoreOnRestart() = runTest {
        prepareDeleted()
        every { locator.erase(any()) } answers {
            check(locator.databaseFile("first").delete())
            throw IOException("partial erase")
        }

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertTrue(keys.hasKey("first"))
        assertTrue(File(locator.storagePath("first"), "wallet.db-wal").exists())
        restart()
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        verify(exactly = 0) { restore.deleteBeam(any()) }
    }

    @Test
    fun cleanupDeleted_wrapperFailure_retainsIntentAndRetriesWithoutKeyCreation() = runTest {
        prepareDeleted()
        val id = locator.storageId("first", BeamNetwork.Mainnet)
        every { keys.removeStorageKey(id) } throws IOException("wrapper")

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }
        assertFalse(locator.storagePath("first").exists())
        assertTrue(keys.hasKey("first"))
        verify(exactly = 0) { restore.deleteBeam(any()) }

        restart()
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        guard().cleanupDeleted(listOf("first"))
        assertFalse(keys.hasKey("first"))
        assertFalse(locator.storagePath("first").exists())
    }

    @Test
    fun cleanupDeleted_processDeathAfterDatabase_restartsInCleanupOnly() = runTest {
        prepareDeleted()
        locator.erase(locator.storageId("first", BeamNetwork.Mainnet))

        restart()
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        guard().cleanupDeleted(listOf("first"))

        assertFalse(keys.hasKey("first"))
        assertFalse(locator.storagePath("first").exists())
        verify(exactly = 1) { encryption.encrypt(any()) }
    }

    @Test
    fun cleanupDeleted_processDeathAfterWrapper_restartsInCleanupOnly() = runTest {
        prepareDeleted()
        locator.erase(locator.storageId("first", BeamNetwork.Mainnet))
        keys.remove("first")

        restart()
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        guard().cleanupDeleted(listOf("first"))
        existing -= "first"
        deleted -= "first"

        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        verify(exactly = 1) { encryption.encrypt(any()) }
    }

    @Test
    fun cleanupDeleted_linkageFailure_keepsTombstoneAndRetryDoesNotRestore() = runTest {
        prepareDeleted()
        every { restore.deleteBeam(any()) } throws IOException("linkage")

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertTrue("first" in deleted)
        assertFalse(keys.hasKey("first"))
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        every { restore.deleteBeam(any()) } returns Unit
        guard().cleanupDeleted(listOf("first"))
    }

    @Test
    fun prepareExplicitReset_hiddenAndOrphanData_erasesBeforeGlobalClearAndRetainsMarker() = runTest {
        keys.keyFor("hidden")
        artifacts("hidden")
        artifacts("orphan")
        preferences("beam_database_keys").edit()
            .putString(locator.storageId("wrapper-only", BeamNetwork.Mainnet), "damaged-wrapper").commit()

        guard().prepareExplicitReset()

        assertFalse(locator.hasAnyData())
        assertFalse(keys.hasAnyKey())
        assertTrue(state.resetPending)
        assertFailsWith<IllegalStateException> { keys.keyFor("hidden") }
        assertFailsWith<AccountDeletionBlockedException> { guard().ensureCanReset() }
        assertFailsWith<AccountDeletionBlockedException> { guard().finishExplicitReset() }
        existing.clear()
        guard().finishExplicitReset()
        assertFalse(state.resetPending)
        assertFailsWith<IllegalStateException> { keys.keyFor("hidden") }
    }

    @Test
    fun prepareExplicitReset_shredsEveryWrapperBeforeTouchingAnyFile() = runTest {
        keys.keyFor("first")
        artifacts("first")
        val id = locator.storageId("first", BeamNetwork.Mainnet)
        val recorded = mutableListOf<String>()
        every { keys.shredStorageKey(any()) } answers {
            recorded += "shred:" + firstArg<String>()
            // The ciphertext is still on disk: that is what makes this a crypto-shred.
            assertTrue(locator.databaseFile("first").exists())
            callOriginal()
        }
        every { locator.erase(any()) } answers {
            recorded += "erase:" + firstArg<String>()
            assertFalse(keys.hasAnyKey())
            callOriginal()
        }

        guard().prepareExplicitReset()

        assertEquals(listOf("shred:$id", "erase:$id"), recorded)
        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
    }

    @Test
    fun prepareExplicitReset_stopNeverCompletes_stillShredsAndErases() = runTest {
        keys.keyFor("first")
        artifacts("first")
        coEvery { owner.drainDeleted(any()) } coAnswers { CompletableDeferred<Unit>().await() }

        guard().prepareExplicitReset()

        verify { owner.fenceDeletion(existing.toList()) }
        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
        assertTrue(state.resetPending)
        guard().ensureExplicitResetCleaned()
    }

    @Test
    fun prepareExplicitReset_stopFailure_doesNotBlockReset() = runTest {
        keys.keyFor("first")
        artifacts("first")
        coEvery { adapters.stopAdapters(any(), BlockchainType.Beam) } throws IOException("stop")

        guard().prepareExplicitReset()

        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
        guard().ensureExplicitResetCleaned()
    }

    @Test
    fun prepareExplicitReset_eraseFailure_shredsKeysKeepsMarkerAndAllowsGlobalClear() = runTest {
        keys.keyFor("first")
        artifacts("first")
        every { locator.erase(any()) } throws IOException("erase")

        guard().prepareExplicitReset()

        assertFalse(keys.hasAnyKey())
        assertTrue(locator.databaseFile("first").exists())
        assertTrue(state.resetPending)
        // Leftover ciphertext is unreadable, so the global clear may proceed...
        guard().ensureExplicitResetCleaned()
        // ...but the marker stays until the leftover is gone.
        assertFailsWith<AccountDeletionBlockedException> { guard().finishExplicitReset() }
        assertTrue(state.resetPending)
    }

    // AccountCleaner.clearAccounts() runs inside the explicit reset too: cleanupDeleted() must then
    // behave like the reset cleanup, not like the fail-closed account deletion.
    @Test
    fun cleanupDeleted_duringExplicitReset_stopNeverCompletes_doesNotBlock() = runTest(timeout = 5.seconds) {
        keys.keyFor("first")
        artifacts("first")
        coEvery { owner.drainDeleted(any()) } coAnswers { CompletableDeferred<Unit>().await() }
        val ids = state.accountIds

        guard().prepareExplicitReset()
        guard().cleanupDeleted(ids)

        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
        assertTrue(state.resetPending)
        guard().ensureExplicitResetCleaned()
    }

    @Test
    fun cleanupDeleted_duringExplicitReset_stickyStopFailure_doesNotThrow() = runTest {
        keys.keyFor("first")
        artifacts("first")
        coEvery { owner.drainDeleted(any()) } throws IllegalStateException("BEAM session cleanup failed")
        val ids = state.accountIds

        guard().prepareExplicitReset()
        guard().cleanupDeleted(ids)

        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
        assertTrue(state.resetPending)
    }

    @Test
    fun cleanupDeleted_duringExplicitReset_eraseFailure_keepsMarkerAndProceeds() = runTest {
        keys.keyFor("first")
        artifacts("first")
        every { locator.erase(any()) } throws IOException("erase")
        val ids = state.accountIds

        guard().prepareExplicitReset()
        guard().cleanupDeleted(ids)

        assertFalse(keys.hasAnyKey())
        assertTrue(locator.databaseFile("first").exists())
        assertTrue(state.resetPending)
        guard().ensureExplicitResetCleaned()
        // The leftover ciphertext keeps the marker for resumePendingReset().
        assertFailsWith<AccountDeletionBlockedException> { guard().finishExplicitReset() }
        assertTrue(state.resetPending)
    }

    @Test
    fun prepareExplicitReset_linkageFailureAfterShred_keepsMarkerAndProceeds() = runTest {
        keys.keyFor("first")
        artifacts("first")
        existing.clear()
        every { restore.deleteAllBeam() } throws IOException("linkage")
        every { restore.hasBeamSettings() } returns true

        guard().prepareExplicitReset()

        assertFalse(keys.hasAnyKey())
        assertFalse(locator.hasAnyData())
        assertTrue(state.resetPending)
        // The global clear may proceed; appDatabase.clearAllTables() removes the same rows.
        guard().ensureExplicitResetCleaned()
        // The surviving restore intent keeps the marker for the next startup.
        assertFailsWith<AccountDeletionBlockedException> { guard().finishExplicitReset() }
        assertTrue(state.resetPending)
    }

    @Test
    fun resumePendingReset_restartWithShreddedKeysAndLeftoverFiles_removesFilesAndClearsMarker() = runTest {
        artifacts("first")
        state.beginReset()
        existing.clear()
        restart()

        guard().resumePendingReset()

        assertFalse(locator.hasAnyData())
        assertFalse(keys.hasAnyKey())
        assertFalse(state.resetPending)
        verify { encryption wasNot Called }
    }

    @Test
    fun prepareExplicitReset_emptyAccountDirectory_removesInterruptedDeletionRemainder() = runTest {
        check(locator.storagePath("orphan").parentFile?.mkdirs() == true)
        guard().prepareExplicitReset()
        assertFalse(locator.hasAnyData())
    }

    @Test
    fun prepareExplicitReset_markerCommitFailure_hasZeroDestructiveEffects() = runTest {
        artifacts("first")
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.commit() } returns false
        val preferences = mockk<SharedPreferences> { every { edit() } returns editor }
        every { context.getSharedPreferences("beam_deletion", Context.MODE_PRIVATE) } returns preferences
        state = BeamDeletionState(context, database)

        assertFailsWith<AccountDeletionBlockedException> { guard().prepareExplicitReset() }

        assertTrue(locator.databaseFile("first").exists())
        verify { listOf(owner, adapters, keys) wasNot Called }
        verify(exactly = 0) { restore.deleteBeam(any()) }
    }

    @Test
    fun resumePendingReset_failedIntentVisibleInMemory_requiresSuccessfulCommitBeforeCleanup() = runTest {
        artifacts("first")
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.commit() } returns false
        val preferences = mockk<SharedPreferences> {
            every { contains("reset_pending") } returns true
            every { edit() } returns editor
        }
        every { context.getSharedPreferences("beam_deletion", Context.MODE_PRIVATE) } returns preferences
        state = BeamDeletionState(context, database)

        assertFailsWith<AccountDeletionBlockedException> { guard().resumePendingReset() }
        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertTrue(locator.databaseFile("first").exists())
        verify { listOf(owner, adapters, keys) wasNot Called }
    }

    @Test
    fun finishExplicitReset_markerCommitFailure_reinstatesMarkerAndReportsFailure() = runTest {
        existing.clear()
        val persisted = preferences("beam_deletion")
        persisted.edit().putBoolean("reset_pending", true).commit()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.remove("reset_pending") } answers {
            persisted.edit().remove("reset_pending").commit()
            editor
        }
        every { editor.putBoolean("reset_pending", true) } answers {
            persisted.edit().putBoolean("reset_pending", true).commit()
            editor
        }
        every { editor.commit() } returns false
        val preferences = mockk<SharedPreferences> {
            every { contains("reset_pending") } answers { persisted.contains("reset_pending") }
            every { edit() } returns editor
        }
        every { context.getSharedPreferences("beam_deletion", Context.MODE_PRIVATE) } returns preferences
        state = BeamDeletionState(context, database)

        assertFailsWith<AccountDeletionBlockedException> { guard().finishExplicitReset() }

        assertTrue(persisted.contains("reset_pending"))
        assertFailsWith<IllegalStateException> { state.ensureAvailable("first") }
    }

    @Test
    fun resumePendingReset_restartAfterDatabaseRemoval_removesOrphanWrapperOnly() = runTest {
        keys.keyFor("first")
        artifacts("first")
        state.beginReset()
        locator.erase(locator.storageId("first", BeamNetwork.Mainnet))

        restart()
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        guard().resumePendingReset()

        assertFalse(keys.hasAnyKey())
        assertTrue(state.resetPending)
        assertTrue(existing.isNotEmpty())
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
    }

    @Test
    fun resumePendingReset_processDeathAfterGlobalClear_finishesMarkerWithoutRecreation() = runTest {
        state.beginReset()
        existing.clear()
        restart()

        guard().resumePendingReset()

        assertFalse(state.resetPending)
        assertFailsWith<IllegalStateException> { keys.keyFor("first") }
        verify { restore.deleteAllBeam() }
        verify { encryption wasNot Called }
    }

    @Test
    fun ensureCanReset_restoreIntentWithoutEnabledBeam_failsClosedWithoutDecrypting() = runTest {
        every { restore.hasBeamSettings() } returns true

        assertFailsWith<AccountDeletionBlockedException> { guard().ensureCanReset() }

        verify { listOf(owner, adapters, keys, encryption) wasNot Called }
        verify(exactly = 0) { restore.deleteBeam(any()) }
    }

    @Test
    fun ensureCanReset_databaseOrCorruptWrapper_preservesCiphertextWithoutDecrypting() = runTest {
        artifacts("orphan")
        assertFailsWith<AccountDeletionBlockedException> { guard().ensureCanReset() }
        assertTrue(locator.databaseFile("orphan").exists())
        locator.erase(locator.storageId("orphan", BeamNetwork.Mainnet))
        preferences("beam_database_keys").edit().putString("damaged-wrapper", "corrupt").commit()

        assertFailsWith<AccountDeletionBlockedException> { guard().ensureCanReset() }

        assertTrue(keys.hasAnyKey())
        verify { encryption wasNot Called }
    }

    @Test
    fun ensureCanReset_noBeam_preservesNonBeamFlow() = runTest {
        guard().ensureCanReset()
        verify { listOf(owner, adapters, encryption) wasNot Called }
    }

    @Test
    fun ensureCanReset_inventoryFailure_failsClosed() = runTest {
        every { wallets.enabledWallets } throws IOException("inventory")
        assertFailsWith<AccountDeletionBlockedException> { guard().ensureCanReset() }
    }

    @Test
    fun cleanupDeleted_cancelledStop_preservesData() = runTest {
        prepareDeleted()
        coEvery { adapters.stopAdapters(any(), BlockchainType.Beam) } throws CancellationException()
        assertFailsWith<CancellationException> { guard().cleanupDeleted(listOf("first")) }
        assertRetained()
    }

    @Test
    fun cleanupDeleted_symlinkToSibling_rejectsBeforeErasingEitherWallet() = runTest {
        prepareDeleted()
        artifacts("second")
        Files.createSymbolicLink(
            File(locator.storagePath("first"), "outside").toPath(), locator.storagePath("second").toPath(),
        )

        assertFailsWith<AccountDeletionBlockedException> { guard().cleanupDeleted(listOf("first")) }

        assertRetained()
        assertTrue(locator.databaseFile("second").exists())
    }

    private fun restart() {
        state = BeamDeletionState(context, database)
        keys = BeamDatabaseKeyProvider(context, locator, encryption, state)
    }

    private fun prepareDeleted() {
        keys.keyFor("first")
        artifacts("first")
        deleted += "first"
    }

    private fun artifacts(id: String) {
        val directory = locator.storagePath(id)
        check(directory.mkdirs())
        File(directory, "wallet.db").writeText("synthetic-ciphertext")
        File(directory, "wallet.db-wal").writeText("synthetic-wal")
        File(directory, "wallet.db-shm").writeText("synthetic-shm")
        File(directory, ".beam-recovery").mkdir()
        File(directory, ".beam-recovery/recovery.bin").writeText("synthetic-cache")
    }

    private fun assertRetained() {
        assertTrue(locator.databaseFile("first").exists())
        assertTrue(keys.hasKey("first"))
        verify(exactly = 0) { restore.deleteBeam(any()) }
    }

    private fun preferences(name: String) = application.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun TestScope.guard() = BeamAccountDeletionPreflight(
        locator, lazy { keys }, wallets,
        TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
        state, lazy { owner }, lazy { adapters },
    )
}
