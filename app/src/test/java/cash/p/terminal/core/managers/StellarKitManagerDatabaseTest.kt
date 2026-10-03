package cash.p.terminal.core.managers

import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.stellarkit.Network
import io.horizontalsystems.stellarkit.StellarKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.stellar.sdk.KeyPair
import kotlin.test.assertFailsWith

class StellarKitManagerDatabaseTest {

    private val account = watchAccount(ACCOUNT_ID)
    private val databaseKey = ByteArray(32) { it.toByte() }
    private val keyProvider = mockk<StellarKitDatabaseKeyProvider>()
    private val hardwarePublicKeyStorage = mockk<HardwarePublicKeyStorage>()
    private val stellarKit = mockk<StellarKit>(relaxed = true)
    private val backgroundManager = mockk<BackgroundManager> {
        every { stateFlow } returns MutableStateFlow(BackgroundManagerState.Unknown)
    }

    private var createdManager: StellarKitManager? = null

    @Before
    fun setUp() {
        CoreApp.instance = mockk(relaxed = true)
        mockkObject(StellarKit.Companion)
        coEvery { StellarKit.migrateDatabase(any(), any(), any(), any()) } returns DatabaseMigrationResult(0, 1)
        every { StellarKit.clear(any(), any(), any()) } returns Unit
        every {
            StellarKit.getInstance(
                stellarWallet = any(),
                network = any(),
                context = any(),
                walletId = any(),
                databaseKey = any(),
                eventListenerFactory = any(),
            )
        } returns stellarKit
        every { keyProvider.keyFor(any()) } returns databaseKey
        every { keyProvider.remove(any()) } returns Unit
    }

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = StellarKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
        unmockkAll()
    }

    @Test
    fun getStellarKitWrapper_watchAccount_migratesBeforeCreatingKitWithSameKey() = runTest {
        val wrapper = createManager().getStellarKitWrapper(account)

        assertSame(stellarKit, wrapper.stellarKit)
        coVerifyOrder {
            StellarKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey)
            StellarKit.getInstance(
                stellarWallet = any(),
                network = Network.MainNet,
                context = any(),
                walletId = ACCOUNT_ID,
                databaseKey = databaseKey,
                eventListenerFactory = any(),
            )
        }
    }

    @Test
    fun getStellarKitWrapper_databaseKeyLocked_retriesThenCreatesKit() = runTest {
        every { keyProvider.keyFor(ACCOUNT_ID) } throws
            KitDatabaseKeyLockedException("StellarKit", mockk()) andThen databaseKey

        val wrapper = createManager().getStellarKitWrapper(account)

        assertSame(stellarKit, wrapper.stellarKit)
        verify(exactly = 2) { keyProvider.keyFor(ACCOUNT_ID) }
        coVerify(exactly = 1) { StellarKit.migrateDatabase(any(), Network.MainNet, ACCOUNT_ID, databaseKey) }
    }

    @Test
    fun getStellarKitWrapper_migrationFails_propagatesWithoutCreatingKit() = runTest {
        coEvery { StellarKit.migrateDatabase(any(), any(), any(), any()) } throws
            DatabaseMigrationConflictException("migration conflict")
        val manager = createManager()

        assertFailsWith<DatabaseMigrationConflictException> { manager.getStellarKitWrapper(account) }

        verifyNoKitCreated()
        assertNull(manager.stellarKitWrapper)
        assertNull(manager.currentAccount)
    }

    @Test
    fun getStellarKitWrapper_unsupportedAccount_throwsWithoutTouchingKey() = runTest {
        val tonAccount = account.copy(type = AccountType.TonAddress("UQD5mxRgCuRNLxKxeOjG6r14iSroLF5FtomPnet-sgP5xNJb"))

        assertFailsWith<UnsupportedAccountException> { createManager().getStellarKitWrapper(tonAccount) }

        verify(exactly = 0) { keyProvider.keyFor(any()) }
        coVerify(exactly = 0) { StellarKit.migrateDatabase(any(), any(), any(), any()) }
    }

    @Test
    fun getStellarKitWrapper_otherAccount_destroysOldKitOnceAfterStartJobCancelled() = runTest {
        val startEntered = CompletableDeferred<Unit>()
        val startCancelled = CompletableDeferred<Unit>()
        coEvery { stellarKit.start() } coAnswers {
            startEntered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                startCancelled.complete(Unit)
            }
        }
        var startCancelledBeforeDestroy = false
        coEvery { stellarKit.destroy() } coAnswers { startCancelledBeforeDestroy = startCancelled.isCompleted }
        val manager = createManager()
        manager.getStellarKitWrapper(account)
        startEntered.await()

        manager.getStellarKitWrapper(watchAccount("other-account-id"))

        coVerify(exactly = 1) { stellarKit.destroy() }
        assertTrue(startCancelledBeforeDestroy)
    }

    @Test
    fun getAddress_hardwareCard_returnsAccountIdWithoutCreatingKit() = runTest {
        val publicKey = KeyPair.random().publicKey
        val hardwarePublicKey = mockk<HardwarePublicKey> {
            every { derivedPublicKey } returns publicKey
        }
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(ACCOUNT_ID, BlockchainType.Stellar)
        } returns hardwarePublicKey
        val hardwareAccount = account.copy(
            type = AccountType.HardwareCard(
                cardId = "card-id",
                backupCardsCount = 0,
                walletPublicKey = "wallet-public-key",
                signedHashes = 0,
            )
        )

        val address = createManager().getAddress(hardwareAccount)

        assertEquals(KeyPair.fromPublicKey(publicKey).accountId, address)
        verifyNoKitCreated()
    }

    @Test
    fun clear_currentAccount_destroysKitAndClearsEveryNetworkBeforeRemovingKey() = runTest {
        val manager = createManager()
        manager.getStellarKitWrapper(account)

        manager.clear(ACCOUNT_ID)

        coVerifyOrder {
            stellarKit.destroy()
            StellarKit.clear(any(), Network.MainNet, ACCOUNT_ID)
            StellarKit.clear(any(), Network.TestNet, ACCOUNT_ID)
            keyProvider.remove(ACCOUNT_ID)
        }
        coVerify(exactly = 1) { stellarKit.destroy() }
        assertNull(manager.stellarKitWrapper)
    }

    @Test
    fun clear_fileClearFails_keepsKey() = runTest {
        every { StellarKit.clear(any(), any(), any()) } throws DatabaseMigrationConflictException("clear conflict")

        assertFailsWith<DatabaseMigrationConflictException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun clear_keyRemovalFails_propagates() = runTest {
        every { keyProvider.remove(any()) } throws KitDatabaseKeyException("remove failed")

        assertFailsWith<KitDatabaseKeyException> { createManager().clear(ACCOUNT_ID) }

        verify(exactly = 1) { StellarKit.clear(any(), Network.MainNet, ACCOUNT_ID) }
    }

    private fun verifyNoKitCreated() {
        verify(exactly = 0) {
            StellarKit.getInstance(
                stellarWallet = any(),
                network = any(),
                context = any(),
                walletId = any(),
                databaseKey = any(),
                eventListenerFactory = any(),
            )
        }
        verify(exactly = 0) {
            StellarKit.getInstance(
                signer = any(),
                network = any(),
                context = any(),
                walletId = any(),
                databaseKey = any(),
                eventListenerFactory = any(),
            )
        }
    }

    private fun createManager() = StellarKitManager(
        backgroundManager = backgroundManager,
        hardwarePublicKeyStorage = hardwarePublicKeyStorage,
        trezorClient = mockk(relaxed = true),
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = mockk(relaxed = true),
        stellarKitDatabaseKeyProvider = keyProvider,
    ).also { createdManager = it }

    private fun watchAccount(id: String) = Account(
        id = id,
        name = "Stellar",
        type = AccountType.StellarAddress("GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
