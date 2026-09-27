package cash.p.terminal.modules.paycore

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Used as a navigation route argument: androidx.navigation resolves the enum by its original name. */
@Keep
@Serializable
enum class PayCoreTicker {
    @SerialName("RUB")
    RUB,

    @SerialName("USDT")
    USDT, // trc20

    @SerialName("USDT_ERC20")
    USDT_ERC20,

    @SerialName("USDT_SPL")
    USDT_SPL
}
