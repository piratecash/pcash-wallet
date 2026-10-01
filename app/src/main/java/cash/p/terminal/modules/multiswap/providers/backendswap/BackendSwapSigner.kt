package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.core.to0xHexString
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import kotlinx.coroutines.withContext

class BackendSwapSigner(
    private val evmSignerFactory: EvmSignerFactory,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private class EvmKey(val blockchainType: BlockchainType, val address: Address)

    @Volatile
    private var keysByAccountId: Map<String, EvmKey> = emptyMap()

    suspend fun walletAddress(account: Account): String? = evmKey(account)?.address?.eip55

    suspend fun sign(account: Account, message: CreateSwapMessage): String {
        val signer = evmKey(account)?.let { key ->
            withContext(dispatcherProvider.io) {
                evmSignerFactory.createSigner(
                    account,
                    key.blockchainType,
                    evmBlockchainManager.getChain(key.blockchainType),
                )
            }
        }
        checkNotNull(signer) { "No EVM signer for the account" }
        return EvmMessageSigning.signTypedData(signer, message.toTypedDataJson()).to0xHexString()
    }

    private suspend fun evmKey(account: Account): EvmKey? {
        if (account.isWatchAccount || !canSignTypedData(account)) return null
        keysByAccountId[account.id]?.let { return it }
        // Hardware keys are stored per blockchain; every EVM chain shares one address.
        val key = withContext(dispatcherProvider.io) {
            EvmBlockchainManager.blockchainTypes.firstNotNullOfOrNull { blockchainType ->
                evmSignerFactory.resolveAddress(account, blockchainType, evmBlockchainManager.getChain(blockchainType))
                    ?.let { EvmKey(blockchainType, it) }
            }
        } ?: return null
        keysByAccountId = keysByAccountId + (account.id to key)
        return key
    }

    private fun canSignTypedData(account: Account): Boolean = when (val type = account.type) {
        is AccountType.TrezorDevice ->
            TrezorModelSupport.supportsTypedData(TrezorModel.fromInternalModel(type.model), type.firmwareVersion)
        else -> true
    }
}
