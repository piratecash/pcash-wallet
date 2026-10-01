package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.ThorchainKitManager
import cash.p.terminal.wallet.title

class ThorchainBlockchainStatusProvider(
    private val kitManager: ThorchainKitManager,
) : BlockchainStatusProvider {

    override val blockchainName: String = kitManager.blockchainType.title
    override val kitVersion: String = BuildConfig.THORCHAIN_KIT_VERSION
    override val logFilterTag: String = kitManager.blockchainType.uid

    override val kitStarted: Boolean
        get() = kitManager.kitStartedFlow.value

    override fun getStatus() = statusFromMap(blockchainName, kitManager.statusInfo)
}
