package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsModule
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeamBlockchainStatusProviderTest {
    @Test
    fun beamIsStatusOnlyAndUsesTheNativeWalletAdapter() {
        val wallet = mockk<Wallet> {
            every { token } returns mockk<Token> {
                every { blockchainType } returns BlockchainType.Beam
            }
        }
        val adapter = mockk<BeamAdapter> {
            every { statusInfo } returns mapOf("Sync State" to "Ready")
        }
        val walletManager = mockk<IWalletManager> {
            every { activeWallets } returns listOf(wallet)
        }
        val adapterManager = mockk<IAdapterManager> {
            every { getAdapterForWallet<BeamAdapter>(wallet) } returns adapter
        }

        val provider = BeamBlockchainStatusProvider(walletManager, adapterManager)

        assertTrue(BlockchainType.Beam in BlockchainSettingsModule.statusOnlyBlockchainTypes)
        assertEquals(BuildConfig.BEAM_SDK_VERSION, provider.kitVersion)
        assertEquals("beam", provider.logFilterTag)
        assertTrue(provider.kitStarted)
        assertEquals("Ready", (provider.getStatus().sections.single().items.single() as StatusItem.KeyValue).value)
    }

    @Test
    fun missingNativeAdapterIsNotReportedAsStarted() {
        val provider = BeamBlockchainStatusProvider(
            walletManager = mockk { every { activeWallets } returns emptyList() },
            adapterManager = mockk(),
        )

        assertFalse(provider.kitStarted)
        assertTrue(provider.getStatus().sections.isEmpty())
    }
}
