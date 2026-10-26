package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.network.binance.api.EthereumRpcApi
import cash.p.terminal.core.managers.EvmSyncSourceManager
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EvmContractAddressValidator(
    private val ethereumRpcApi: EthereumRpcApi,
    private val excludedContractValidator: ExcludedContractValidator,
    private val evmSyncSourceManager: EvmSyncSourceManager,
) : ContractAddressValidator {

    override suspend fun isContract(address: String, blockchainType: BlockchainType): Boolean? =
        withContext(Dispatchers.IO) {
            return@withContext runCatching {
                val rpcUrl = requireNotNull(
                    evmSyncSourceManager.defaultSyncSources(blockchainType).firstOrNull()
                ).uri.toString()
                val code = ethereumRpcApi.getCode(rpcUrl, address)
                if (excludedContractValidator.isKnownNotContract(code, blockchainType)) {
                    return@runCatching false
                }
                code != null && code != "0x"
            }.getOrElse { null }
        }
}
