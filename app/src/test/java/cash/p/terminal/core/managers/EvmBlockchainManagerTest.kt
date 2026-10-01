package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.factories.EvmAccountManagerFactory
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.merkleiokit.MerkleTransactionAdapter
import io.horizontalsystems.nftkit.core.NftKit
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class EvmBlockchainManagerTest {

    private val accountManagerFactory = mockk<EvmAccountManagerFactory>(relaxed = true)
    private val mockEvmKit = mockk<EthereumKit>(relaxed = true)
    private val databaseKeys = mockk<EvmKitDatabaseKeys>(relaxed = true)
    private val context = mockk<Context>()

    private val manager = EvmBlockchainManager(
        backgroundManager = mockk<BackgroundManager>(relaxed = true),
        syncSourceManager = mockk<EvmSyncSourceManager>(relaxed = true) {
            every { syncSourceObservable } returns PublishSubject.create()
        },
        marketKit = mockk<MarketKitWrapper>(relaxed = true),
        accountManagerFactory = accountManagerFactory,
        backgroundKeepAliveManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        offlineModeManager = mockk(relaxed = true),
        databaseKeys = databaseKeys,
        context = context,
    )

    private var createdManager: EvmKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { evmKitManager ->
            val scopeField = EvmKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(evmKitManager) as CoroutineScope).cancel()
        }
        unmockkAll()
    }

    @Test
    fun syncTransactionHistory_onlyCreatedManagers_areCalled() {
        val evmKitManager = manager.getEvmKitManager(BlockchainType.Ethereum)
        createdManager = evmKitManager
        setEvmKitWrapper(evmKitManager, mockEvmKit)
        clearMocks(accountManagerFactory, answers = false)

        manager.syncTransactionHistory()

        verify(exactly = 1) { mockEvmKit.syncTransactions() }
        verify(exactly = 0) { accountManagerFactory.evmAccountManager(any(), any()) }
    }

    @Test
    fun clear_account_clearsEveryModuleOnEveryChainThenRemovesKey() = runTest {
        mockKitClears()
        val chains = EvmBlockchainManager.blockchainTypes.map(manager::getChain)

        manager.clear(ACCOUNT_ID)

        assertEquals(11, chains.distinct().size)
        coVerifyOrder {
            chains.forEach { chain ->
                EthereumKit.clear(context, chain, ACCOUNT_ID)
                Erc20Kit.clear(context, chain, ACCOUNT_ID)
                MerkleTransactionAdapter.clear(context, chain, ACCOUNT_ID)
                NftKit.clear(context, chain, ACCOUNT_ID)
            }
            databaseKeys.remove(ACCOUNT_ID)
        }
    }

    @Test
    fun clear_moduleClearFails_keepsKey() = runTest {
        mockKitClears()
        coEvery { Erc20Kit.clear(context, Chain.Polygon, ACCOUNT_ID) } throws IOException("rename failed")

        assertFailsWith<IOException> { manager.clear(ACCOUNT_ID) }

        coVerify(exactly = 0) { databaseKeys.remove(any()) }
    }

    @Test
    fun clear_runningKitOfAccount_stopsKitBeforeClearing() = runTest {
        mockKitClears()
        val evmKitManager = manager.getEvmKitManager(BlockchainType.Ethereum)
        createdManager = evmKitManager
        setEvmKitWrapper(evmKitManager, mockEvmKit)
        setField(evmKitManager, "currentAccount", account())

        manager.clear(ACCOUNT_ID)

        coVerifyOrder {
            mockEvmKit.stop()
            EthereumKit.clear(context, Chain.Ethereum, ACCOUNT_ID)
        }
        assertNull(evmKitManager.evmKitWrapper)
    }

    private fun mockKitClears() {
        mockkObject(EthereumKit, Erc20Kit, MerkleTransactionAdapter, NftKit)
        coEvery { EthereumKit.clear(any(), any(), any()) } just Runs
        coEvery { Erc20Kit.clear(any(), any(), any()) } just Runs
        coEvery { MerkleTransactionAdapter.clear(any(), any(), any()) } just Runs
        coEvery { NftKit.clear(any(), any(), any()) } just Runs
    }

    private fun setEvmKitWrapper(evmKitManager: EvmKitManager, evmKit: EthereumKit) {
        setField(
            evmKitManager,
            "evmKitWrapper",
            EvmKitWrapper(
                evmKit = evmKit,
                nftKit = null,
                blockchainType = BlockchainType.Ethereum,
                signer = null,
                merkleTransactionAdapter = null,
                databaseKey = ByteArray(32),
            )
        )
    }

    private fun setField(evmKitManager: EvmKitManager, name: String, value: Any?) {
        EvmKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(evmKitManager, value)
    }

    private fun account() = Account(
        id = ACCOUNT_ID,
        name = "Evm",
        type = AccountType.EvmAddress("0x000000000000000000000000000000000000dEaD"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
