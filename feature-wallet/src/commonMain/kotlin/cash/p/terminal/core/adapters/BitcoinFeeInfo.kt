package cash.p.terminal.core.adapters

import io.horizontalsystems.bitcoincore.models.Address
import io.horizontalsystems.bitcoincore.storage.UnspentOutput
import java.math.BigDecimal

data class BitcoinFeeInfo(
    val unspentOutputs: List<UnspentOutput>,
    val fee: BigDecimal,
    val changeValue: BigDecimal?,
    val changeAddress: Address?,
    val selectedUtxoCount: Int = unspentOutputs.size
)
