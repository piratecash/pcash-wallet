package cash.p.terminal.wallet

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import io.horizontalsystems.core.entities.Blockchain
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import java.util.*

@CommonParcelize
data class Token(
    val coin: Coin,
    val blockchain: Blockchain,
    val type: TokenType,
    val decimals: Int
) : CommonParcelable {

    val blockchainType: BlockchainType
        get() = blockchain.type

    val tokenQuery: TokenQuery
        get() = TokenQuery(blockchainType, type)

    val fullCoin: FullCoin
        get() = FullCoin(coin, listOf(this))

    override fun equals(other: Any?): Boolean =
        other is Token &&
                other.coin == coin &&
                other.blockchain == blockchain &&
                other.type == type &&
                other.decimals == decimals

    override fun hashCode(): Int =
        Objects.hash(coin, blockchain, type, decimals)

}
