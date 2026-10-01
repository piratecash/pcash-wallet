package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.Network
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class TonKitManagerDatabaseTest {

    private val account = watchAccount(ACCOUNT_ID)
    private val databaseKey = ByteArray(32) { it.toByte() }
    private val keyProvider = mockk<TonKitDatabaseKeyProvider>()
    private val tonKit = mockk<TonKit>(relaxed = true)
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)

    private var createdManager: TonKitManager? = null

    @Before
    fun setUp() {
        CoreApp.instance = mockk(relaxed = true)
        mockkObject(TonKit.Companion)
        coEvery { TonKit.migrateDatabase(any(), any(), any(), any()) } returns DatabaseMigrationResult(0, 1)
        coEvery { TonKit.clear(any(), any(), any()) } just runs
        coEvery { TonKit.getInstance(any(), any(), any(), any(), any(), any(), any()) } returns tonKit
        coEvery { keyProvider.awaitKey(any()) } returns databaseKey
        every { keyProvider.remove(any()) } just runs
        // Keeps the background lifecycle job away from the network in these tests.
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns true
    }

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = TonKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
        unmockkAll()
    }

    @Test
    fun getTonKitWrapper_watchAccount_migratesBeforeCreatingKitWithSameKey() = runTest {
        val wrapper = createManager().getTonKitWrapper(account, BlockchainType.Ton)

        assertSame(tonKit, wrapper.tonKit)
        coVerifyOrder {
            keyProvider.awaitKey(ACCOUNT_ID)
            TonKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey)
            TonKit.getInstance(any(), Network.MainNet, any(), ACCOUNT_ID, databaseKey, any(), any())
        }
    }

    @Test
    fun getTonKitWrapper_migrationFails_propagatesWithoutCreatingKit() = runTest {
        coEvery { TonKit.migrateDatabase(any(), any(), any(), any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1)
        val manager = createManager()

        assertFailsWith<InsufficientDatabaseMigrationSpaceException> {
            manager.getTonKitWrapper(account, BlockchainType.Ton)
        }

        verifyNoKitCreated()
        assertNull(manager.tonKitWrapper)
        assertNull(manager.currentAccount)
    }

    @Test
    fun getTonKitWrapper_unsupportedAccount_throwsWithoutTouchingKey() = runTest {
        val solanaAccount = account.copy(
            type = AccountType.SolanaAddress("Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS")
        )

        assertFailsWith<IllegalArgumentException> {
            createManager().getTonKitWrapper(solanaAccount, BlockchainType.Ton)
        }

        coVerify(exactly = 0) { keyProvider.awaitKey(any()) }
        coVerify(exactly = 0) { TonKit.migrateDatabase(any(), any(), any(), any()) }
    }

    @Test
    fun getNonActiveTonKitWrapper_watchAccount_migratesBeforeCreatingKit() = runTest {
        val wrapper = createManager().getNonActiveTonKitWrapper(account, BlockchainType.Ton)

        assertSame(tonKit, wrapper.tonKit)
        coVerifyOrder {
            TonKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey)
            TonKit.getInstance(any(), Network.MainNet, any(), ACCOUNT_ID, databaseKey, any(), any())
        }
    }

    @Test
    fun clear_currentAccount_stopsKitAndDeletesAllNetworksBeforeRemovingKey() = runTest {
        val manager = createManager()
        manager.getTonKitWrapper(account, BlockchainType.Ton)

        manager.clear(ACCOUNT_ID)

        coVerifyOrder {
            tonKit.stop()
            TonKit.clear(any(), Network.MainNet, ACCOUNT_ID)
            TonKit.clear(any(), Network.TestNet, ACCOUNT_ID)
            keyProvider.remove(ACCOUNT_ID)
        }
        assertNull(manager.tonKitWrapper)
    }

    @Test
    fun clear_otherAccount_keepsCurrentKitRunning() = runTest {
        val manager = createManager()
        manager.getTonKitWrapper(account, BlockchainType.Ton)

        manager.clear(OTHER_ACCOUNT_ID)

        coVerify(exactly = 0) { tonKit.stop() }
        coVerify(exactly = 1) { TonKit.clear(any(), Network.MainNet, OTHER_ACCOUNT_ID) }
        assertSame(tonKit, manager.tonKitWrapper?.tonKit)
    }

    @Test
    fun clear_fileClearFails_keepsKey() = runTest {
        coEvery { TonKit.clear(any(), any(), any()) } throws DatabaseMigrationConflictException("clear conflict")

        assertFailsWith<DatabaseMigrationConflictException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun clear_keyRemovalFails_propagates() = runTest {
        every { keyProvider.remove(any()) } throws KitDatabaseKeyException("remove failed")

        assertFailsWith<KitDatabaseKeyException> { createManager().clear(ACCOUNT_ID) }

        coVerify(exactly = 1) { TonKit.clear(any(), Network.MainNet, ACCOUNT_ID) }
    }

    private fun verifyNoKitCreated() {
        coVerify(exactly = 0) { TonKit.getInstance(any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun createManager() = TonKitManager(
        backgroundManager = mockk<BackgroundManager>(relaxed = true) {
            every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
        },
        hardwarePublicKeyStorage = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = offlineModeManager,
        tonKitDatabaseKeyProvider = keyProvider,
    ).also { createdManager = it }

    private fun watchAccount(id: String) = Account(
        id = id,
        name = "Ton",
        type = AccountType.TonAddress("EQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqB2N"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val OTHER_ACCOUNT_ID = "other-account-id"
    }
}
