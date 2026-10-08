package cash.p.terminal.wallet.models

import com.google.gson.annotations.SerializedName
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import java.math.BigDecimal

@CommonParcelize
data class CoinCategory(
    val uid: String,
    val name: String,
    val description: Map<String, String>,
    @SerializedName("market_cap")
    val marketCap: BigDecimal?,
    @SerializedName("change_24h")
    val diff24H: BigDecimal?,
    @SerializedName("change_1w")
    val diff1W: BigDecimal?,
    @SerializedName("change_1m")
    val diff1M: BigDecimal?,
) : CommonParcelable {

    override fun toString(): String {
        return "CoinCategory [uid: $uid; name: $name; descriptionCount: ${description.size}]"
    }

}
