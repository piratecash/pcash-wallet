package cash.p.terminal.core.managers

import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class SolanaKitManagerDatabaseTest {

    private val account = watchAccount(ACCOUNT_ID)
    private val databaseKey = ByteArray(32) { it.toByte() }
    private val keyProvider = mockk<SolanaKitDatabaseKeyProvider>()
    private val solanaKit = mockk<SolanaKit>(relaxed = true)

    private var createdManager: SolanaKitManager? = null

    @Before
    fun setUp() {
        CoreApp.instance = mockk(relaxed = true)
        mockkObject(SolanaKit.Companion)
        coEvery { SolanaKit.migrateDatabase(any(), any(), any()) } returns DatabaseMigrationResult(0, 2)
        every { SolanaKit.clear(any(), any()) } returns Unit
        coEvery {
            SolanaKit.getInstance(
                context = any(),
                addressString = any(),
                rpcSource = any(),
                walletId = any(),
                databaseKey = any(),
                limitFirstTimeTransactionCount = any(),
                limitTimeTransactionCount = any(),
                networkErrorListener = any(),
            )
        } returns solanaKit
        every { keyProvider.keyFor(any()) } returns databaseKey
        every { keyProvider.remove(any()) } returns Unit
    }

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = SolanaKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
        unmockkAll()
    }

    @Test
    fun getSolanaKitWrapper_watchAccount_migratesBeforeCreatingKitWithSameKey() = runTest {
        val wrapper = createManager().getSolanaKitWrapper(account)

        assertSame(solanaKit, wrapper.solanaKit)
        coVerifyOrder {
            SolanaKit.migrateDatabase(any(), ACCOUNT_ID, databaseKey)
            SolanaKit.getInstance(
                context = any(),
                addressString = any(),
                rpcSource = any(),
                walletId = ACCOUNT_ID,
                databaseKey = databaseKey,
                limitFirstTimeTransactionCount = any(),
                limitTimeTransactionCount = any(),
                networkErrorListener = any(),
            )
        }
    }

    @Test
    fun getSolanaKitWrapper_databaseKeyLocked_retriesThenCreatesKit() = runTest {
        every { keyProvider.keyFor(ACCOUNT_ID) } throws
            KitDatabaseKeyLockedException("SolanaKit", mockk()) andThen databaseKey

        val wrapper = createManager().getSolanaKitWrapper(account)

        assertSame(solanaKit, wrapper.solanaKit)
        verify(exactly = 2) { keyProvider.keyFor(ACCOUNT_ID) }
        coVerify(exactly = 1) { SolanaKit.migrateDatabase(any(), ACCOUNT_ID, databaseKey) }
    }

    @Test
    fun getSolanaKitWrapper_migrationFails_propagatesWithoutCreatingKit() = runTest {
        coEvery { SolanaKit.migrateDatabase(any(), any(), any()) } throws
            DatabaseMigrationConflictException("migration conflict")
        val manager = createManager()

        assertFailsWith<DatabaseMigrationConflictException> { manager.getSolanaKitWrapper(account) }

        verifyNoKitCreated()
        assertNull(manager.solanaKitWrapper)
        assertNull(manager.currentAccount)
    }

    @Test
    fun getSolanaKitWrapper_unsupportedAccount_throwsWithoutTouchingKey() = runTest {
        val tonAccount = account.copy(
            type = AccountType.TonAddress("UQD5mxRgCuRNLxKxeOjG6r14iSroLF5FtomPnet-sgP5xNJb")
        )

        assertFailsWith<UnsupportedAccountException> { createManager().getSolanaKitWrapper(tonAccount) }

        verify(exactly = 0) { keyProvider.keyFor(any()) }
        coVerify(exactly = 0) { SolanaKit.migrateDatabase(any(), any(), any()) }
    }

    @Test
    fun clear_currentAccount_stopsKitAndDeletesFilesBeforeRemovingKey() = runTest {
        val manager = createManager()
        manager.getSolanaKitWrapper(account)

        manager.clear(ACCOUNT_ID)

        coVerifyOrder {
            solanaKit.stop()
            SolanaKit.clear(any(), ACCOUNT_ID)
            keyProvider.remove(ACCOUNT_ID)
        }
        assertNull(manager.solanaKitWrapper)
    }

    @Test
    fun clear_fileClearFails_keepsKey() = runTest {
        every { SolanaKit.clear(any(), any()) } throws DatabaseMigrationConflictException("clear conflict")

        assertFailsWith<DatabaseMigrationConflictException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun clear_keyRemovalFails_propagates() = runTest {
        every { keyProvider.remove(any()) } throws KitDatabaseKeyException("remove failed")

        assertFailsWith<KitDatabaseKeyException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 1) { SolanaKit.clear(any(), ACCOUNT_ID) }
    }

    private fun verifyNoKitCreated() {
        coVerify(exactly = 0) {
            SolanaKit.getInstance(
                context = any(),
                addressString = any(),
                rpcSource = any(),
                walletId = any(),
                databaseKey = any(),
                limitFirstTimeTransactionCount = any(),
                limitTimeTransactionCount = any(),
                networkErrorListener = any(),
            )
        }
    }

    private fun createManager() = SolanaKitManager(
        rpcSourceManager = mockk(relaxed = true),
        walletManager = mockk(relaxed = true),
        backgroundManager = mockk(relaxed = true),
        hardwarePublicKeyStorage = mockk(relaxed = true),
        trezorClient = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = mockk(relaxed = true),
        solanaKitDatabaseKeyProvider = keyProvider,
    ).also { createdManager = it }

    private fun watchAccount(id: String) = Account(
        id = id,
        name = "Solana",
        type = AccountType.SolanaAddress("Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
