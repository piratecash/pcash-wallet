package cash.p.terminal.modules.blockchainsettings

import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsModule.BlockchainItem
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockchainSettingsServiceDesktopTest {

    private val tron = Blockchain(BlockchainType.Tron, "Tron", null)
    private var service: BlockchainSettingsService? = null

    @After
    fun tearDown() {
        service?.stop()
    }

    @Test
    fun start_blockchainStatusUnavailable_buildsNoStatusOnlyItems() {
        val items = startedService(isBlockchainStatusAvailable = false).blockchainItems

        assertEquals(emptyList<BlockchainItem>(), items.filterIsInstance<BlockchainItem.StatusOnly>())
    }

    @Test
    fun start_blockchainStatusAvailable_buildsStatusOnlyItems() {
        val items = startedService(isBlockchainStatusAvailable = true).blockchainItems

        assertEquals(listOf(tron), items.filterIsInstance<BlockchainItem.StatusOnly>().map { it.blockchain })
    }

    private fun startedService(isBlockchainStatusAvailable: Boolean): BlockchainSettingsService {
        val btcBlockchainManager = mockk<BtcBlockchainManager> {
            every { restoreModeUpdatedObservable } returns PublishSubject.create()
            every { transactionSortModeUpdatedObservable } returns PublishSubject.create()
            every { allBlockchains } returns emptyList()
        }
        val solanaRpcSourceManager = mockk<SolanaRpcSourceManager> {
            every { rpcSourceUpdateObservable } returns PublishSubject.create()
            every { blockchain } returns null
        }
        val marketKit = mockk<MarketKitWrapper> {
            every { blockchain(BlockchainType.Zcash.uid) } returns null
            every { blockchains(any()) } returns listOf(tron)
        }
        val zcashServerManager = mockk<ZcashServerManager> {
            every { serverSelectedFlow } returns MutableSharedFlow()
        }
        val platformPages = mockk<WalletPlatformPages> {
            every { this@mockk.isBlockchainStatusAvailable } returns isBlockchainStatusAvailable
        }
        return BlockchainSettingsService(
            btcBlockchainManager = btcBlockchainManager,
            evmBlockchainManager = mockk<EvmBlockchains> { every { allBlockchains } returns emptyList() },
            evmSyncSourceManager = mockk { every { syncSourceObservable } returns PublishSubject.create() },
            solanaRpcSourceManager = solanaRpcSourceManager,
            marketKit = marketKit,
            zcashServerManager = zcashServerManager,
            platformPages = platformPages,
        ).also {
            service = it
            it.start()
        }
    }
}
