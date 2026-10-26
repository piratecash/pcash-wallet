package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.ChainAccountManagersStarter
import cash.p.terminal.core.managers.StellarAccountManager
import cash.p.terminal.core.managers.ThorchainAccountManager
import cash.p.terminal.core.managers.ThorchainAccountManagers
import cash.p.terminal.core.managers.TonAccountManager
import cash.p.terminal.core.managers.TronAccountManager
import cash.p.terminal.wallet.policy.PlatformChainSupport
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

class DesktopChainAccountManagersStarterTest {

    private val tronAccountManager = mockk<TronAccountManager>(relaxed = true)
    private val tonAccountManager = mockk<TonAccountManager>(relaxed = true)
    private val stellarAccountManager = mockk<StellarAccountManager>(relaxed = true)
    private val thorchainAccountManager = mockk<ThorchainAccountManager>(relaxed = true) {
        every { blockchainType } returns BlockchainType.Thorchain
    }
    private val mayachainAccountManager = mockk<ThorchainAccountManager>(relaxed = true) {
        every { blockchainType } returns BlockchainType.Mayachain
    }

    @Test
    fun start_desktopChainSupport_startsEveryAccountManager() {
        starter(DesktopPlatformChainSupport).start()

        verify { tronAccountManager.start() }
        verify { tonAccountManager.start() }
        verify { stellarAccountManager.start() }
        verify { thorchainAccountManager.start() }
        verify { mayachainAccountManager.start() }
    }

    @Test
    fun start_noChainSupported_startsNoAccountManager() {
        val noChain = mockk<PlatformChainSupport> {
            every { isSupported(any<BlockchainType>()) } returns false
        }

        starter(noChain).start()

        verify(exactly = 0) { tronAccountManager.start() }
        verify(exactly = 0) { tonAccountManager.start() }
        verify(exactly = 0) { stellarAccountManager.start() }
        verify(exactly = 0) { thorchainAccountManager.start() }
        verify(exactly = 0) { mayachainAccountManager.start() }
    }

    private fun starter(platformChainSupport: PlatformChainSupport) = ChainAccountManagersStarter(
        tronAccountManager = tronAccountManager,
        tonAccountManager = tonAccountManager,
        stellarAccountManager = stellarAccountManager,
        thorchainAccountManagers = mockk<ThorchainAccountManagers> {
            every { all } returns listOf(thorchainAccountManager, mayachainAccountManager)
        },
        platformChainSupport = platformChainSupport,
    )
}
