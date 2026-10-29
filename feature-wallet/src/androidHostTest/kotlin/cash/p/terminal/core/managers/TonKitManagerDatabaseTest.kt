package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.core.TonHttpClient
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
        mockkObject(TonKit.Companion)
        coEvery { TonKit.migrateDatabase(any(), any(), any(), any()) } returns DatabaseMigrationResult(0, 1)
        coEvery { TonKit.clear(any(), any(), any()) } just runs
        every { TonKit.createHttpClient(any(), any()) } returns mockk()
        coEvery { TonKit.getInstance(any(), any(), any(), any(), any(), any()) } returns tonKit
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
        val wrapper = createManager().getTonKitWrapper(account, TonWalletVersion.V4R2, BlockchainType.Ton)

        assertSame(tonKit, wrapper.tonKit)
        coVerifyOrder {
            keyProvider.awaitKey(ACCOUNT_ID)
            TonKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey)
            TonKit.getInstance(any(), Network.MainNet, any(), ACCOUNT_ID, databaseKey, any())
        }
    }

    @Test
    fun getTonKitWrapper_migrationFails_propagatesWithoutCreatingKit() = runTest {
        coEvery { TonKit.migrateDatabase(any(), any(), any(), any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1)
        val manager = createManager()

        assertFailsWith<InsufficientDatabaseMigrationSpaceException> {
            manager.getTonKitWrapper(account, TonWalletVersion.V4R2, BlockchainType.Ton)
        }

        verifyNoKitCreated()
        assertTrue(manager.startedKitsFlow.value.isEmpty())
        assertNull(manager.currentAccount)
    }

    @Test
    fun getTonKitWrapper_unsupportedAccount_throwsWithoutTouchingKey() = runTest {
        val solanaAccount = account.copy(
            type = AccountType.SolanaAddress("Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS")
        )

        assertFailsWith<IllegalArgumentException> {
            createManager().getTonKitWrapper(solanaAccount, TonWalletVersion.V4R2, BlockchainType.Ton)
        }

        coVerify(exactly = 0) { keyProvider.awaitKey(any()) }
        coVerify(exactly = 0) { TonKit.migrateDatabase(any(), any(), any(), any()) }
    }

    @Test
    fun getNonActiveTonKitWrapper_watchAccount_migratesBeforeCreatingKit() = runTest {
        val wrapper = createManager().getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, BlockchainType.Ton)

        assertSame(tonKit, wrapper.tonKit)
        coVerifyOrder {
            TonKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey)
            TonKit.getInstance(any(), Network.MainNet, any(), ACCOUNT_ID, databaseKey, any())
        }
    }

    @Test
    fun getNonActiveTonKitWrapper_clearInProgress_waitsForClearToFinish() = runTest {
        val clearGate = CompletableDeferred<Unit>()
        coEvery { TonKit.clear(any(), any(), any()) } coAnswers { clearGate.await() }
        val manager = createManager()
        launch { manager.clear(ACCOUNT_ID) }
        runCurrent()

        val wrapper = async { manager.getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, BlockchainType.Ton) }
        runCurrent()

        coVerify(exactly = 0) { keyProvider.awaitKey(any()) }

        clearGate.complete(Unit)

        assertSame(tonKit, wrapper.await().tonKit)
        coVerifyOrder {
            TonKit.clear(any(), Network.TestNet, W5_WALLET_ID)
            keyProvider.remove(ACCOUNT_ID)
            keyProvider.awaitKey(ACCOUNT_ID)
            TonKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey)
        }
    }

    @Test
    fun clear_currentAccount_stopsKitAndDeletesAllNetworksBeforeRemovingKey() = runTest {
        val manager = createManager()
        manager.getTonKitWrapper(account, TonWalletVersion.V4R2, BlockchainType.Ton)

        manager.clear(ACCOUNT_ID)

        coVerifyOrder {
            tonKit.stop()
            TonKit.clear(any(), Network.MainNet, ACCOUNT_ID)
            TonKit.clear(any(), Network.TestNet, ACCOUNT_ID)
            TonKit.clear(any(), Network.MainNet, W5_WALLET_ID)
            TonKit.clear(any(), Network.TestNet, W5_WALLET_ID)
            keyProvider.remove(ACCOUNT_ID)
        }
        assertTrue(manager.startedKitsFlow.value.isEmpty())
    }

    @Test
    fun clear_otherAccount_keepsCurrentKitRunning() = runTest {
        val manager = createManager()
        manager.getTonKitWrapper(account, TonWalletVersion.V4R2, BlockchainType.Ton)

        manager.clear(OTHER_ACCOUNT_ID)

        coVerify(exactly = 0) { tonKit.stop() }
        coVerify(exactly = 1) { TonKit.clear(any(), Network.MainNet, OTHER_ACCOUNT_ID) }
        assertSame(tonKit, manager.startedKitsFlow.value[TonWalletVersion.V4R2]?.tonKit)
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

    @Test
    fun getTonKitWrapper_bothVersions_opensSeparateDatabasesWithAccountKeyAndSharedClient() = runTest {
        val httpClient = mockk<TonHttpClient>()
        every { TonKit.createHttpClient(any(), any()) } returns httpClient
        val manager = createManager()

        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.V4R2, BlockchainType.Ton)
        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.W5, BlockchainType.Ton)

        coVerify(exactly = 1) { TonKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey) }
        coVerify(exactly = 1) { TonKit.migrateDatabase(any(), Network.MainNet, W5_WALLET_ID, databaseKey) }
        coVerify(exactly = 1) {
            TonKit.getInstance(any(), Network.MainNet, any(), ACCOUNT_ID, databaseKey, httpClient)
        }
        coVerify(exactly = 1) {
            TonKit.getInstance(any(), Network.MainNet, any(), W5_WALLET_ID, databaseKey, httpClient)
        }
        verify(exactly = 1) { TonKit.createHttpClient(any(), any()) }
        assertEquals(setOf(TonWalletVersion.V4R2, TonWalletVersion.W5), manager.startedKitsFlow.value.keys)
    }

    @Test
    fun getTonKitWrapper_otherAccount_stopsKitsOfBothVersions() = runTest {
        val w5Kit = w5KitForWalletId()
        val otherKit = mockk<TonKit>(relaxed = true)
        coEvery { TonKit.getInstance(any(), any(), any(), OTHER_ACCOUNT_ID, any(), any()) } returns otherKit
        val manager = createManager()
        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.V4R2, BlockchainType.Ton)
        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.W5, BlockchainType.Ton)

        manager.getTonKitWrapper(account.copy(id = OTHER_ACCOUNT_ID), TonWalletVersion.V4R2, BlockchainType.Ton)

        coVerify(exactly = 1) { tonKit.stop() }
        coVerify(exactly = 1) { w5Kit.stop() }
        assertEquals(
            mapOf(TonWalletVersion.V4R2 to otherKit),
            manager.startedKitsFlow.value.mapValues { it.value.tonKit },
        )
        assertEquals(OTHER_ACCOUNT_ID, manager.currentAccount?.id)
    }

    @Test
    fun unlink_w5LastUse_stopsOnlyW5Kit() = runTest {
        val w5Kit = w5KitForWalletId()
        val manager = createManager()
        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.V4R2, BlockchainType.Ton)
        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.W5, BlockchainType.Ton)

        manager.unlink(mnemonicAccount, TonWalletVersion.W5)

        coVerify(exactly = 1) { w5Kit.stop() }
        coVerify(exactly = 0) { tonKit.stop() }
        assertEquals(setOf(TonWalletVersion.V4R2), manager.startedKitsFlow.value.keys)
        assertSame(mnemonicAccount, manager.currentAccount)
    }

    @Test
    fun unlink_lastVersion_releasesAccount() = runTest {
        val manager = createManager()
        manager.getTonKitWrapper(mnemonicAccount, TonWalletVersion.W5, BlockchainType.Ton)

        manager.unlink(mnemonicAccount, TonWalletVersion.W5)

        assertTrue(manager.startedKitsFlow.value.isEmpty())
        assertNull(manager.currentAccount)
    }

    @Test
    fun getTonKitWrapper_watchAccountW5_throwsWithoutTouchingKey() = runTest {
        assertFailsWith<IllegalArgumentException> {
            createManager().getTonKitWrapper(account, TonWalletVersion.W5, BlockchainType.Ton)
        }

        coVerify(exactly = 0) { keyProvider.awaitKey(any()) }
    }

    private fun w5KitForWalletId(): TonKit {
        val w5Kit = mockk<TonKit>(relaxed = true)
        coEvery { TonKit.getInstance(any(), any(), any(), W5_WALLET_ID, any(), any()) } returns w5Kit
        return w5Kit
    }

    private fun verifyNoKitCreated() {
        coVerify(exactly = 0) { TonKit.getInstance(any(), any(), any(), any(), any(), any()) }
    }

    private fun createManager() = TonKitManager(
        backgroundManager = mockk<IBackgroundManager>(relaxed = true) {
            every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
        },
        hardwarePublicKeyStorage = mockk(relaxed = true),
        platformContexts = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = offlineModeManager,
        tonKitDatabaseKeyProvider = keyProvider,
    ).also { createdManager = it }

    private val mnemonicAccount = Account(
        id = ACCOUNT_ID,
        name = "Ton",
        type = AccountType.Mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
            "",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

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
        const val W5_WALLET_ID = "$ACCOUNT_ID-w5"
    }
}
