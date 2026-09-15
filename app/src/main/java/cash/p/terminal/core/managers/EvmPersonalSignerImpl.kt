package cash.p.terminal.core.managers

import cash.p.terminal.core.to0xHexString
import cash.p.terminal.feature.miniapp.domain.usecase.EvmPersonalSigner
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.withContext

class EvmPersonalSignerImpl(
    private val evmSignerFactory: EvmSignerFactory,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val dispatcherProvider: DispatcherProvider
) : EvmPersonalSigner {

    private val chain get() = evmBlockchainManager.getChain(BlockchainType.BinanceSmartChain)

    override suspend fun address(account: Account): String? = withContext(dispatcherProvider.io) {
        evmSignerFactory.resolveAddress(account, BlockchainType.BinanceSmartChain, chain)?.hex
    }

    override suspend fun signPersonalMessage(account: Account, message: String): String? {
        val signer = evmSignerFactory.createSigner(account, BlockchainType.BinanceSmartChain, chain)
            ?: return null
        return EvmMessageSigning.signPersonalMessage(signer, message.toByteArray()).to0xHexString()
    }
}
