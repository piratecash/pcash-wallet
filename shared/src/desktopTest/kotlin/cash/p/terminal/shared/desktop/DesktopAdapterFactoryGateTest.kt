package cash.p.terminal.shared.desktop

import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class DesktopAdapterFactoryGateTest {

    private val btcBlockchainManager = mockk<BtcBlockchainManager>(relaxed = true)

    private val factory = AdapterFactory(
        btcBlockchainManager = btcBlockchainManager,
        evmBlockchainManager = mockk(relaxed = true),
        evmSyncSourceManager = mockk(relaxed = true),
        solanaKitManager = mockk(relaxed = true),
        tronKitManager = mockk(relaxed = true),
        tonKitManager = mockk(relaxed = true),
        stellarKitManager = mockk(relaxed = true),
        thorchainKitManagers = mockk(relaxed = true),
        moneroKitManager = mockk(relaxed = true),
        backgroundManager = mockk(relaxed = true),
        restoreSettingsManager = mockk(relaxed = true),
        coinManager = mockk(relaxed = true),
        evmLabelManager = mockk(relaxed = true),
        appearanceSettingsStore = mockk(relaxed = true),
        masterNodesRepository = mockk(relaxed = true),
        getBnbAddressUseCase = mockk(relaxed = true),
        feeRateProvider = mockk(relaxed = true),
        dispatcherProvider = mockk(relaxed = true),
        walletManager = mockk(relaxed = true),
        platformChainSupport = DesktopPlatformChainSupport,
        zcashHardwareSigners = DesktopNoHardwareSigners,
        stackingManager = mockk(relaxed = true),
    )

    @Test
    fun getAdapterOrNull_bitcoinWalletOnDesktop_buildsItsAdapterThroughItsKit() = runTest {
        assertNull(factory.getAdapterOrNull(bitcoinWallet(hardware = false)))
        verify { btcBlockchainManager.syncMode(BlockchainType.Bitcoin, any()) }
    }

    @Test
    fun getAdapterOrNull_hardwareBitcoinWalletOnDesktop_returnsNullWithoutTouchingItsKit() = runTest {
        assertNull(factory.getAdapterOrNull(bitcoinWallet(hardware = true)))
        verify { btcBlockchainManager wasNot Called }
    }

    private fun bitcoinWallet(hardware: Boolean) = mockk<Wallet>(relaxed = true) {
        every { token.blockchainType } returns BlockchainType.Bitcoin
        every { token.type } returns TokenType.Derived(TokenType.Derivation.Bip84)
        every { account.isHardwareWalletAccount } returns hardware
    }
}
