package cash.p.terminal.core.managers

import cash.p.terminal.core.adapters.TronAdapter
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.network.Network
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
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

class TronKitManagerDatabaseTest {

    private val account = Account(
        id = ACCOUNT_ID,
        name = "Tron",
        type = AccountType.TronAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE"),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val databaseKey = ByteArray(32) { it.toByte() }
    private val keyProvider = mockk<TronKitDatabaseKeyProvider>()
    private val tronKit = mockk<TronKit>(relaxed = true)
    private val backgroundManager = mockk<IBackgroundManager> {
        every { stateFlow } returns MutableStateFlow(BackgroundManagerState.Unknown)
    }

    private var createdManager: TronKitManager? = null

    @Before
    fun setUp() {
        mockkObject(SharedAppConfig, TronKit.Companion, TronAdapter)
        every { SharedAppConfig.trongridApiKeys } returns listOf("api-key")
        coEvery { TronKit.migrateDatabase(any(), any(), any(), any()) } returns DatabaseMigrationResult(0, 1)
        every {
            TronKit.getInstance(
                application = any(),
                address = any(),
                network = any(),
                tronGridApiKeys = any(),
                walletId = any(),
                databaseKey = any(),
                eventListenerFactory = any(),
            )
        } returns tronKit
    }

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = TronKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
        unmockkAll()
    }

    @Test
    fun getTronKitWrapper_watchAccount_migratesBeforeCreatingKitWithSameKey() = runTest {
        coEvery { keyProvider.awaitKey(ACCOUNT_ID) } returns databaseKey

        val wrapper = createManager().getTronKitWrapper(account)

        assertSame(tronKit, wrapper.tronKit)
        coVerifyOrder {
            TronKit.migrateDatabase(any(), Network.Mainnet, ACCOUNT_ID, databaseKey)
            TronKit.getInstance(
                application = any(),
                address = any(),
                network = Network.Mainnet,
                tronGridApiKeys = any(),
                walletId = ACCOUNT_ID,
                databaseKey = databaseKey,
                eventListenerFactory = any(),
            )
        }
    }

    @Test
    fun getTronKitWrapper_migrationFails_propagatesWithoutCreatingKit() = runTest {
        coEvery { keyProvider.awaitKey(ACCOUNT_ID) } returns databaseKey
        coEvery { TronKit.migrateDatabase(any(), any(), any(), any()) } throws
            DatabaseMigrationConflictException("migration conflict")
        val manager = createManager()

        assertFailsWith<DatabaseMigrationConflictException> { manager.getTronKitWrapper(account) }

        verify(exactly = 0) {
            TronKit.getInstance(
                application = any(),
                address = any(),
                network = any(),
                tronGridApiKeys = any(),
                walletId = any(),
                databaseKey = any(),
                eventListenerFactory = any(),
            )
        }
        assertNull(manager.tronKitWrapper)
        assertNull(manager.currentAccount)
    }

    @Test
    fun clear_account_clearsFilesBeforeRemovingKey() = runTest {
        every { TronAdapter.clear(any(), any()) } returns Unit
        every { keyProvider.remove(any()) } returns Unit

        createManager().clear(ACCOUNT_ID)

        verifyOrder {
            TronAdapter.clear(any(), ACCOUNT_ID)
            keyProvider.remove(ACCOUNT_ID)
        }
    }

    @Test
    fun clear_fileClearFails_keepsKey() = runTest {
        every { TronAdapter.clear(any(), any()) } throws DatabaseMigrationConflictException("clear conflict")

        assertFailsWith<DatabaseMigrationConflictException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun clear_keyRemovalFails_propagates() = runTest {
        every { TronAdapter.clear(any(), any()) } returns Unit
        every { keyProvider.remove(any()) } throws KitDatabaseKeyException("remove failed")

        assertFailsWith<KitDatabaseKeyException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 1) { TronAdapter.clear(any(), ACCOUNT_ID) }
    }

    @Test
    fun clear_runningKitOfAccount_stopsKitBeforeClearingFiles() = runTest {
        val manager = createManagerWithRunningKit()

        manager.clear(ACCOUNT_ID)

        verifyOrder {
            tronKit.stop()
            TronAdapter.clear(any(), ACCOUNT_ID)
        }
        assertNull(manager.tronKitWrapper)
    }

    @Test
    fun clear_otherAccount_keepsRunningKit() = runTest {
        val manager = createManagerWithRunningKit()

        manager.clear(OTHER_ACCOUNT_ID)

        verify(exactly = 0) { tronKit.stop() }
        verify(exactly = 1) { TronAdapter.clear(any(), OTHER_ACCOUNT_ID) }
        assertSame(tronKit, manager.tronKitWrapper?.tronKit)
    }

    private suspend fun createManagerWithRunningKit() = createManager().also { manager ->
        coEvery { keyProvider.awaitKey(ACCOUNT_ID) } returns databaseKey
        every { TronAdapter.clear(any(), any()) } returns Unit
        every { keyProvider.remove(any()) } returns Unit
        manager.getTronKitWrapper(account)
    }

    private fun createManager() = TronKitManager(
        backgroundManager = backgroundManager,
        hardwarePublicKeyStorage = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        hardwareSigners = mockk(relaxed = true),
        platformContexts = mockk(relaxed = true),
        offlineModeManager = mockk(relaxed = true),
        tronKitDatabaseKeyProvider = keyProvider,
    ).also { createdManager = it }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val OTHER_ACCOUNT_ID = "other-account-id"
    }
}
