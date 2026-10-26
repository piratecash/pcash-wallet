package cash.p.terminal.core.managers

import cash.p.terminal.wallet.policy.PlatformChainSupport
import io.horizontalsystems.core.entities.BlockchainType

/** Starts the account managers of the chains this platform runs. */
class ChainAccountManagersStarter(
    private val tronAccountManager: TronAccountManager,
    private val tonAccountManager: TonAccountManager,
    private val stellarAccountManager: StellarAccountManager,
    private val thorchainAccountManagers: ThorchainAccountManagers,
    private val platformChainSupport: PlatformChainSupport,
) {
    fun start() {
        if (platformChainSupport.isSupported(BlockchainType.Tron)) tronAccountManager.start()
        if (platformChainSupport.isSupported(BlockchainType.Ton)) tonAccountManager.start()
        if (platformChainSupport.isSupported(BlockchainType.Stellar)) stellarAccountManager.start()
        thorchainAccountManagers.all
            .filter { platformChainSupport.isSupported(it.blockchainType) }
            .forEach(ThorchainAccountManager::start)
    }
}
