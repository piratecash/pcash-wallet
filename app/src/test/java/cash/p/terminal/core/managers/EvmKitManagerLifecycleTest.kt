package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.merkleiokit.MerkleTransactionAdapter
import io.horizontalsystems.nftkit.core.NftKit
import io.horizontalsystems.oneinchkit.OneInchKit
import io.horizontalsystems.uniswapkit.UniswapKit
import io.horizontalsystems.uniswapkit.UniswapV3Kit
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EvmKitManagerLifecycleTest {

    private lateinit var context: Context
    private val evmKit = mockk<EthereumKit>(relaxed = true)
    private val databaseKey = ByteArray(KEY_SIZE) { it.toByte() }
    private val createdManagers = mutableListOf<EvmKitManager>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
        val signerFactory = mockk<EvmSignerFactory> {
            coEvery { resolveAddress(any(), any(), any()) } returns Address(WATCH_ADDRESS)
            coEvery { createSigner(any(), any(), any()) } returns null
        }
        startKoin { modules(module { single { signerFactory } }) }
        mockKitCompanions()
    }

    @After
    fun tearDown() {
        createdManagers.forEach { manager ->
            val scopeField = EvmKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
        stopKoin()
        unmockkAll()
        preferences().edit().clear().commit()
    }

    @Test
    fun getEvmKitWrapper_watchAccount_migratesEveryDatabaseBeforeOpeningWithSameKey() = runTest {
        val databaseKeys = mockk<EvmKitDatabaseKeyProvider> {
            coEvery { awaitKey(ACCOUNT_ID) } returns databaseKey
        }
        val manager = createManager(databaseKeys)

        val wrapper = manager.getEvmKitWrapper(account(), BlockchainType.Ethereum)

        coVerifyOrder {
            databaseKeys.awaitKey(ACCOUNT_ID)
            EthereumKit.migrateDatabase(context, Chain.Ethereum, ACCOUNT_ID, databaseKey)
            Erc20Kit.migrateDatabases(context, Chain.Ethereum, ACCOUNT_ID, databaseKey)
            MerkleTransactionAdapter.migrateDatabase(context, Chain.Ethereum, ACCOUNT_ID, databaseKey)
            NftKit.migrateDatabase(context, Chain.Ethereum, ACCOUNT_ID, databaseKey)
            EthereumKit.getInstance(
                context, any<Address>(), Chain.Ethereum, any(), any(), ACCOUNT_ID, databaseKey,
                any(), any(), any(),
            )
            MerkleTransactionAdapter.getInstance(
                any(), any(), Chain.Ethereum, context, ACCOUNT_ID, databaseKey, any(), any(), any(), any(),
            )
            evmKit.start()
        }
        assertSame(databaseKey, wrapper.databaseKey)
    }

    @Test
    fun getEvmKitWrapper_migrationFails_throwsWithoutOpeningKit() = runTest {
        coEvery { Erc20Kit.migrateDatabases(any(), any(), any(), any()) } throws IllegalStateException("migration")
        val manager = createManager(mockk { coEvery { awaitKey(ACCOUNT_ID) } returns databaseKey })

        assertFailsWith<IllegalStateException> {
            manager.getEvmKitWrapper(account(), BlockchainType.Ethereum)
        }

        coVerify(exactly = 0) {
            EthereumKit.getInstance(any(), any<Address>(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertNull(manager.evmKitWrapper)
    }

    @Test
    fun getEvmKitWrapper_storedKeyLockedThenUnlocked_createsKitWithStoredKey() = runTest {
        val plainEncryption = PrefixEncryptionManager()
        val encrypted = plainEncryption.encrypt(Base64.encodeToString(databaseKey, Base64.NO_WRAP))
        preferences().edit().putString("$KEY_PREFIX$ACCOUNT_ID", encrypted).commit()
        val lockedEncryption = mockk<IEncryptionManager> {
            every { decrypt(encrypted) } throws mockk<UserNotAuthenticatedException>() andThen
                plainEncryption.decrypt(encrypted)
        }
        val manager = createManager(EvmKitDatabaseKeyProvider(context, lockedEncryption))
        val openedKey = slot<ByteArray>()
        coEvery {
            EthereumKit.getInstance(
                any(), any<Address>(), any(), any(), any(), any(), capture(openedKey), any(), any(), any(),
            )
        } returns evmKit

        manager.getEvmKitWrapper(account(), BlockchainType.Ethereum)

        verify(exactly = 2) { lockedEncryption.decrypt(encrypted) }
        assertArrayEquals(databaseKey, openedKey.captured)
    }

    @Test
    fun stopFor_otherAccount_keepsKitRunning() = runTest {
        val manager = createManager(mockk { coEvery { awaitKey(ACCOUNT_ID) } returns databaseKey })
        manager.getEvmKitWrapper(account(), BlockchainType.Ethereum)

        manager.stopFor("other-account")

        verify(exactly = 0) { evmKit.stop() }
        assertNotNull(manager.evmKitWrapper)
    }

    @Test
    fun stopFor_currentAccount_stopsKit() = runTest {
        val manager = createManager(mockk { coEvery { awaitKey(ACCOUNT_ID) } returns databaseKey })
        manager.getEvmKitWrapper(account(), BlockchainType.Ethereum)

        manager.stopFor(ACCOUNT_ID)

        verify(exactly = 1) { evmKit.stop() }
        assertNull(manager.evmKitWrapper)
    }

    @Test
    fun getEvmKitWrapper_merkleGetInstanceThrows_stopsCreatedKitAndNextCallCreatesFreshKit() = runTest {
        coEvery {
            MerkleTransactionAdapter.getInstance(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("merkle") andThen null
        val manager = createManager(mockk { coEvery { awaitKey(ACCOUNT_ID) } returns databaseKey })

        assertFailsWith<IllegalStateException> {
            manager.getEvmKitWrapper(account(), BlockchainType.Ethereum)
        }

        verify(exactly = 1) { evmKit.stop() }
        assertNull(manager.evmKitWrapper)

        assertNotNull(manager.getEvmKitWrapper(account(), BlockchainType.Ethereum))
        coVerify(exactly = 2) {
            EthereumKit.getInstance(any(), any<Address>(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun getEvmKitWrapper_cancelledDuringMerkleGetInstance_stopsCreatedKit() = runTest {
        coEvery {
            MerkleTransactionAdapter.getInstance(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers { awaitCancellation() }
        val manager = createManager(mockk { coEvery { awaitKey(ACCOUNT_ID) } returns databaseKey })
        val creation = launch { manager.getEvmKitWrapper(account(), BlockchainType.Ethereum) }
        runCurrent()

        creation.cancelAndJoin()

        verify(exactly = 1) { evmKit.stop() }
        assertNull(manager.evmKitWrapper)
    }

    private fun mockKitCompanions() {
        mockkObject(EthereumKit, Erc20Kit, MerkleTransactionAdapter, NftKit, UniswapKit, UniswapV3Kit, OneInchKit)
        mockkObject(AppConfigProvider)
        every { AppConfigProvider.merkleIoKey } returns "merkle-key"
        coEvery { EthereumKit.migrateDatabase(any(), any(), any(), any()) } returns mockk()
        coEvery { Erc20Kit.migrateDatabases(any(), any(), any(), any()) } returns mockk()
        coEvery { MerkleTransactionAdapter.migrateDatabase(any(), any(), any(), any()) } returns mockk()
        coEvery { NftKit.migrateDatabase(any(), any(), any(), any()) } returns mockk()
        coEvery {
            EthereumKit.getInstance(any(), any<Address>(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns evmKit
        coEvery {
            MerkleTransactionAdapter.getInstance(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        every { Erc20Kit.addTransactionSyncer(any()) } just Runs
        every { Erc20Kit.addDecorators(any()) } just Runs
        every { UniswapKit.addDecorators(any()) } just Runs
        every { UniswapV3Kit.addDecorators(any()) } just Runs
        every { OneInchKit.addDecorators(any()) } just Runs
    }

    private fun createManager(databaseKeys: EvmKitDatabaseKeyProvider): EvmKitManager {
        val syncSourceManager = mockk<EvmSyncSourceManager>(relaxed = true) {
            every { syncSourceObservable } returns PublishSubject.create()
        }
        val backgroundManager = mockk<BackgroundManager> {
            every { stateFlow } returns MutableStateFlow(BackgroundManagerState.Unknown)
        }
        return EvmKitManager(
            chain = Chain.Ethereum,
            backgroundManager = backgroundManager,
            syncSourceManager = syncSourceManager,
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = mockk(relaxed = true),
            databaseKeys = databaseKeys,
            context = context,
        ).also(createdManagers::add)
    }

    private fun account() = Account(
        id = ACCOUNT_ID,
        name = "Watch",
        type = AccountType.EvmAddress(WATCH_ADDRESS),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private fun preferences() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private class PrefixEncryptionManager : IEncryptionManager {
        override fun encrypt(data: String) = "$ENCRYPTED_PREFIX$data"
        override fun decrypt(data: String) = data.removePrefix(ENCRYPTED_PREFIX)
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val WATCH_ADDRESS = "0x000000000000000000000000000000000000dEaD"
        const val PREFERENCES_NAME = "evm_kit_database_keys"
        const val KEY_PREFIX = "evm_kit_database_key_"
        const val KEY_SIZE = 32
        const val ENCRYPTED_PREFIX = "encrypted:"
    }
}
