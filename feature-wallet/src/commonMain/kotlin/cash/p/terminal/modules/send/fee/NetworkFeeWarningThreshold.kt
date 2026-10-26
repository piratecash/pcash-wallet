package cash.p.terminal.modules.send.fee

import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

private val TRX_WARNING_THRESHOLD = BigDecimal("50")
private val TON_WARNING_THRESHOLD = BigDecimal("0.5")

fun getWarningThreshold(blockchainType: BlockchainType): BigDecimal? {
    return when (blockchainType) {
        BlockchainType.Tron -> TRX_WARNING_THRESHOLD
        BlockchainType.Ton -> TON_WARNING_THRESHOLD
        else -> null
    }
}
