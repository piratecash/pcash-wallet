package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.core.app.ApplicationProvider
import cash.p.beam.BeamLogLevel
import cash.p.beam.BeamSdkConfig
import cash.p.beam.BeamWalletSession
import cash.p.beam.RestoreSource
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MnemonicDerivation
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [27])
class BeamSessionFactoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val keys = mockk<BeamDatabaseKeyProvider>(relaxUnitFun = true)
    private val locator = mockk<BeamStorageLocator>()
    private val seed = ByteArray(64) { 7 }
    private val key = ByteArray(32) { 9 }
    private val mnemonic = mockk<AccountType.Mnemonic>()
    private val driver = RecordingFactory()
    private val restoreSettings = RestoreSettingsTestFixture()
    private var keyReturned = false

    init {
        every { mnemonic.seed } returns seed
    }

    @Test
    fun open_pendingDeletion_rejectsBeforeKeyAccessOrSdkRestore() = runTest {
        val factory = factory()
        every { keys.ensureAvailable(ACCOUNT) } throws IllegalStateException("deletion pending")

        assertFailsWith<IllegalStateException> { factory.open(account(AccountOrigin.Restored), BeamNetwork.Mainnet) }

        verify(exactly = 0) { keys.keyForInitialization(any(), any()) }
        coVerify(exactly = 0) { mnemonic.seed }
        assertEquals(0, driver.calls)
    }

    @Test
    fun open_createdWithoutDatabaseOrKey_createsAfterKeyAndWipesCopies() = runTest {
        val factory = factory()
        assertSame(driver.session, factory.open(account(AccountOrigin.Created), BeamNetwork.Mainnet))
        assertEquals("create", driver.operation)
        assertEquals(BeamLogLevel.None, driver.config?.logLevel)
        // Recovery trust model: single-node recovery, as the official Beam wallet does. This fails
        // if the SDK default flips or someone arms the quorum without deciding to.
        assertEquals(false, driver.config?.requireRecoveryQuorum)
        assertWipedSecrets()
        assertTrue(seed.all { it == 7.toByte() })
    }

    @Test
    fun open_createdWithExistingKeyWithoutDatabase_usesSnapshotThenScan() = runTest {
        factory(keyIsNew = false).open(account(AccountOrigin.Created), BeamNetwork.Mainnet)
        assertEquals("restore", driver.operation)
        assertEquals(RestoreSource.SnapshotThenScan(), driver.source)
        assertWipedSecrets()
    }

    @Test
    fun open_restoredWithoutDatabase_usesSnapshotThenScan() = runTest {
        factory().open(account(AccountOrigin.Restored), BeamNetwork.Mainnet)
        assertEquals("restore", driver.operation)
        assertEquals(RestoreSource.SnapshotThenScan(), driver.source)
        assertWipedSecrets()
    }

    @Test
    fun open_existingDatabaseForEitherOrigin_opensWithoutDerivingSeed() = runTest {
        val factory = factory(databaseExists = true)
        AccountOrigin.entries.forEach { origin ->
            factory.open(account(origin), BeamNetwork.Mainnet)
            assertEquals("open", driver.operation)
        }
        coVerify(exactly = 0) { mnemonic.seed }
    }

    @Test
    fun open_keyUnavailable_neverCallsSdk() = runTest {
        val factory = factory(databaseExists = true)
        val failures = listOf(
            BeamDatabaseKeyException("Missing key"),
            BeamDatabaseKeyException("Corrupt key"),
            BeamDatabaseKeyLockedException(mockk<UserNotAuthenticatedException>()),
        )
        failures.forEach { failure ->
            every { keys.keyForInitialization(ACCOUNT, BeamNetwork.Mainnet) } throws failure
            val thrown = assertFailsWith<IllegalStateException> {
                factory.open(account(AccountOrigin.Created), BeamNetwork.Mainnet)
            }
            assertEquals(failure::class, thrown::class)
            assertEquals(failure.message, thrown.message)
        }
        assertEquals(0, driver.calls)
    }

    @Test
    fun open_failedExistingDatabase_doesNotFallBackToCreation() = runTest {
        val factory = factory(databaseExists = true)
        driver.failure = IllegalStateException("Cannot open database")
        assertFailsWith<IllegalStateException> {
            factory.open(account(AccountOrigin.Created), BeamNetwork.Mainnet)
        }
        assertEquals("open", driver.operation)
        assertEquals(1, driver.calls)
        assertArrayEquals(ByteArray(32), key)
    }

    @Test
    fun open_creationFails_wipesSeedAndKey() = runTest {
        val factory = factory()
        driver.failure = IllegalStateException("Cannot create database")
        assertFailsWith<IllegalStateException> {
            factory.open(account(AccountOrigin.Created), BeamNetwork.Mainnet)
        }
        assertWipedSecrets()
    }

    @Test
    fun open_duplicateCreatedAfterRestartWithNewKey_usesSnapshotThenScan() = runTest {
        val account = account(AccountOrigin.Created)
        restoreSettings.manager().saveBeamRestoreIntent(account)

        factory().open(account, BeamNetwork.Mainnet)

        assertEquals("restore", driver.operation)
        assertEquals(RestoreSource.SnapshotThenScan(), driver.source)
        assertWipedSecrets()
    }

    @Test
    fun open_invalidIntentOrStorageFailure_neverCallsSdkOrDerivesSeed() = runTest {
        val account = account(AccountOrigin.Created)
        val factory = factory()
        restoreSettings.storage.save(listOf(RestoreSettingRecord(
            account.id, BlockchainType.Beam.uid, "beam_restore_intent", "corrupt",
        )))
        assertFailsWith<IllegalStateException> { factory.open(account, BeamNetwork.Mainnet) }
        every { restoreSettings.storage.restoreSettings(any(), any()) } throws
            IllegalStateException("Storage unavailable")
        assertFailsWith<IllegalStateException> { factory.open(account, BeamNetwork.Mainnet) }

        assertEquals(0, driver.calls)
        verify(exactly = 0) { mnemonic.seed }
        assertArrayEquals(ByteArray(32), key)
    }

    @Test
    fun open_existingDatabaseWithUnreadableIntent_opensWithoutReadingIntent() = runTest {
        every { restoreSettings.storage.restoreSettings(any(), any()) } throws
            IllegalStateException("Storage unavailable")

        factory(databaseExists = true).open(account(AccountOrigin.Created), BeamNetwork.Mainnet)

        assertEquals("open", driver.operation)
        verify(exactly = 0) { restoreSettings.storage.restoreSettings(any(), any()) }
    }

    @Test
    fun open_failedRestoreThenRestart_retainsIntentAndNeverCreates() = runTest {
        val account = account(AccountOrigin.Created)
        restoreSettings.manager().saveBeamRestoreIntent(account)
        driver.failure = IllegalStateException("Restore interrupted")

        assertFailsWith<IllegalStateException> { factory().open(account, BeamNetwork.Mainnet) }
        assertEquals("restore", driver.operation)
        assertWipedSecrets()
        assertTrue(restoreSettings.manager().hasBeamRestoreIntent(account))
        driver.failure = null
        factory(keyIsNew = false).open(account, BeamNetwork.Mainnet)

        assertEquals("restore", driver.operation)
        assertEquals(2, driver.calls)
        assertTrue(restoreSettings.manager().hasBeamRestoreIntent(account))
    }

    @Test
    fun open_sameMnemonicAndPassphraseDistinctAccounts_keepsKeysAndStorageIsolated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locator = BeamStorageLocator(context)
        val mnemonic = AccountType.Mnemonic(
            List(11) { "abandon" } + "about", "synthetic-passphrase", MnemonicDerivation.Legacy,
        )
        val source = account(AccountOrigin.Created).copy(type = mnemonic)
        val duplicate = source.copy(id = "$ACCOUNT-copy")
        val sourceKey = keyProvider(context, locator).keyFor(source.id)
        val sourceDatabase = locator.databaseFile(source.id)
        sourceDatabase.parentFile?.mkdirs()
        sourceDatabase.writeText("source-database-sentinel")
        restoreSettings.manager().saveBeamRestoreIntent(duplicate)
        val sdk = mockk<BeamSessionFactory.Factory>()
        val session = mockk<BeamWalletSession>()
        coEvery { sdk.restore(any(), any(), any(), any()) } answers {
            assertArrayEquals(mnemonic.seed, secondArg<ByteArray>())
            assertFalse(sourceKey.contentEquals(thirdArg<ByteArray>()))
            session
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
        val restartedKeys = keyProvider(context, locator)

        BeamSessionFactory(restartedKeys, locator, dispatchers, restoreSettings.manager(), sdk)
            .open(duplicate, BeamNetwork.Mainnet)

        assertNotEquals(source.id, duplicate.id)
        assertNotEquals(locator.storagePath(source.id), locator.storagePath(duplicate.id))
        assertFalse(sourceKey.contentEquals(restartedKeys.keyFor(duplicate.id)))
        assertEquals("source-database-sentinel", sourceDatabase.readText())
        assertFalse(locator.databaseFile(duplicate.id).exists())
        coVerify(exactly = 1) {
            sdk.restore(match { it.storagePath == locator.storagePath(duplicate.id).absolutePath },
                any(), any(), RestoreSource.SnapshotThenScan())
        }
        coVerify(exactly = 0) { sdk.createNew(any(), any(), any()) }
        coVerify(exactly = 0) { sdk.openExisting(any(), any()) }
        verifySiblingSurvivesDeletion(locator, restartedKeys, dispatchers, source, duplicate)
    }

    private suspend fun verifySiblingSurvivesDeletion(
        locator: BeamStorageLocator,
        keys: BeamDatabaseKeyProvider,
        dispatchers: DispatcherProvider,
        source: Account,
        duplicate: Account,
    ) {
        val siblingKey = keys.keyFor(duplicate.id)
        val siblingDatabase = locator.databaseFile(duplicate.id)
        check(siblingDatabase.parentFile?.mkdirs() == true)
        siblingDatabase.writeText("duplicate-database-sentinel")
        val cleanup = BeamAccountDeletionPreflight(
            locator, lazy { keys }, mockk(relaxed = true), dispatchers,
            mockk(relaxed = true), lazy { mockk(relaxed = true) }, lazy { mockk(relaxed = true) },
        )

        cleanup.cleanupDeleted(listOf(source.id))

        assertFalse(locator.storagePath(source.id).exists())
        assertFalse(keys.hasKey(source.id))
        assertEquals("duplicate-database-sentinel", siblingDatabase.readText())
        assertArrayEquals(siblingKey, keys.keyFor(duplicate.id))
    }

    private fun keyProvider(context: Context, locator: BeamStorageLocator) = BeamDatabaseKeyProvider(
        context, locator, mockk<IEncryptionManager> {
            every { encrypt(any()) } answers { "test:" + firstArg<String>() }
            every { decrypt(any()) } answers { firstArg<String>().removePrefix("test:") }
        }, mockk<BeamDeletionState>(relaxed = true),
    )

    private fun TestScope.factory(
        databaseExists: Boolean = false,
        keyIsNew: Boolean = !databaseExists,
    ): BeamSessionFactory {
        val directory = temporaryFolder.newFolder()
        val database = directory.resolve("wallet.db")
        if (databaseExists) database.createNewFile()
        every { locator.storagePath(ACCOUNT, BeamNetwork.Mainnet) } returns directory
        every { locator.databaseFile(ACCOUNT, BeamNetwork.Mainnet) } returns database
        every { keys.keyForInitialization(ACCOUNT, BeamNetwork.Mainnet) } answers {
            keyReturned = true
            BeamDatabaseKeyProvider.Key(key, isNew = keyIsNew)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
        return BeamSessionFactory(keys, locator, dispatchers, restoreSettings.manager(), driver)
    }

    private fun account(origin: AccountOrigin) = Account(ACCOUNT, "BEAM", mnemonic, origin, 0)

    private fun assertWipedSecrets() {
        assertArrayEquals(ByteArray(32), key)
        assertArrayEquals(ByteArray(64), driver.seed)
    }

    private inner class RecordingFactory : BeamSessionFactory.Factory {
        val session = mockk<BeamWalletSession>()
        var operation: String? = null
        var config: BeamSdkConfig? = null
        var source: RestoreSource? = null
        var seed: ByteArray? = null
        var failure: Exception? = null
        var calls = 0

        override suspend fun openExisting(config: BeamSdkConfig, databaseKey: ByteArray) =
            record("open", config, databaseKey)

        override suspend fun createNew(
            config: BeamSdkConfig,
            seed: ByteArray,
            databaseKey: ByteArray,
        ): BeamWalletSession {
            this.seed = seed
            return record("create", config, databaseKey)
        }

        override suspend fun restore(
            config: BeamSdkConfig,
            seed: ByteArray,
            databaseKey: ByteArray,
            source: RestoreSource,
        ): BeamWalletSession {
            this.seed = seed
            this.source = source
            return record("restore", config, databaseKey)
        }

        private fun record(operation: String, config: BeamSdkConfig, key: ByteArray): BeamWalletSession {
            check(keyReturned)
            assertSame(this@BeamSessionFactoryTest.key, key)
            this.operation = operation
            this.config = config
            calls++
            failure?.let { throw it }
            return session
        }
    }

    private companion object {
        const val ACCOUNT = "beam-session-test"
    }
}
