package cash.p.terminal.core.address

import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.entities.Address
import cash.p.terminal.network.addresscheck.api.HashDitApi
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

class HashDitAddressValidator(
    private val api: HashDitApi,
    private val evmBlockchains: EvmBlockchains,
) {
    val supportedBlockchainTypes =
        listOf(BlockchainType.Ethereum, BlockchainType.BinanceSmartChain, BlockchainType.Polygon)

    suspend fun isClear(address: Address, token: Token): Boolean {
        return isClear(address, token.blockchainType)
    }

    suspend fun isClear(address: Address, blockchainType: BlockchainType): Boolean {
        if (!supportedBlockchainTypes.contains(blockchainType)) throw UnsupportedBlockchainType()

        val chain = evmBlockchains.getChain(blockchainType)
        return api.riskLevel(chain.id, address.hex) < MAX_CLEAR_RISK_LEVEL
    }

    fun supports(token: Token): Boolean {
        return supportedBlockchainTypes.contains(token.blockchainType)
    }

    private companion object {
        const val MAX_CLEAR_RISK_LEVEL = 4
    }
}

class UnsupportedBlockchainType : Exception()
