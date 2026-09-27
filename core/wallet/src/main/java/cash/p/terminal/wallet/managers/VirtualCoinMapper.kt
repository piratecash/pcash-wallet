package cash.p.terminal.wallet.managers

import io.horizontalsystems.core.entities.BlockchainType

data class VirtualCoinMapping(
    val realCoinUid: String,
    val blockchainType: BlockchainType,
    val virtualCoinUid: String
)

class VirtualCoinMapper {

    val allMappings: List<VirtualCoinMapping> = listOf(
        VirtualCoinMapping(
            realCoinUid = "binance-bridged-usdt-bnb-smart-chain",
            blockchainType = BlockchainType.BinanceSmartChain,
            virtualCoinUid = "tether"
        )
    )

    private val realCoinUidIndex: Map<String, VirtualCoinMapping> by lazy {
        allMappings.associateBy { it.realCoinUid }
    }

    fun getVirtualCoinUidForRealCoinUid(realCoinUid: String): String? =
        realCoinUidIndex[realCoinUid]?.virtualCoinUid
}
