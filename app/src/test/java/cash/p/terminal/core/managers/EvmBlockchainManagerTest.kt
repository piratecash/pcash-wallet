package cash.p.terminal.core.managers

import cash.p.terminal.core.factories.EvmAccountManagerFactory
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test

class EvmBlockchainManagerTest {

    private val accountManagerFactory = mockk<EvmAccountManagerFactory>(relaxed = true)
    private val mockEvmKit = mockk<EthereumKit>(relaxed = true)

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

    private fun setEvmKitWrapper(evmKitManager: EvmKitManager, evmKit: EthereumKit) {
        val field = EvmKitManager::class.java.getDeclaredField("evmKitWrapper").apply {
            isAccessible = true
        }
        field.set(
            evmKitManager,
            EvmKitWrapper(
                evmKit = evmKit,
                nftKit = null,
                blockchainType = BlockchainType.Ethereum,
                signer = null,
                merkleTransactionAdapter = null,
            )
        )
    }
}
