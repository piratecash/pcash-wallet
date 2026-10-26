package cash.p.terminal.modules.blockchainstatus

import io.horizontalsystems.core.entities.BlockchainType

val BlockchainType.logTag: String
    get() = when (this) {
        BlockchainType.Bitcoin -> "BTC"
        BlockchainType.Litecoin -> "LTC"
        BlockchainType.BitcoinCash -> "BCH"
        BlockchainType.Dash -> "DASH"
        BlockchainType.ECash -> "XEC"
        BlockchainType.Dogecoin -> "DOGE"
        BlockchainType.Cosanta -> "COSA"
        BlockchainType.PirateCash -> "PCASH"
        else -> uid
    }
