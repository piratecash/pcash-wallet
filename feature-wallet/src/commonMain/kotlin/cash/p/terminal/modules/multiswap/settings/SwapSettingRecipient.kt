package cash.p.terminal.modules.multiswap.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.multiswap.settings.ui.RecipientAddress
import cash.p.terminal.wallet.Token

data class SwapSettingRecipient(
    val settings: Map<String, Any?>,
    val tokenOut: Token
) : ISwapSetting {
    override val id = "recipient"

    val value = settings[id] as? Address

    @Composable
    override fun GetContent(
        navigation: HSNavigation,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    ) {
        RecipientAddress(
            token = tokenOut,
            navigation = navigation,
            initial = value,
            onError = onError,
            onValueChange = onValueChange
        )
    }

    fun getEthereumKitAddress(): io.horizontalsystems.ethereumkit.models.Address? {
        val hex = value?.hex ?: return null

        return try {
            io.horizontalsystems.ethereumkit.models.Address(hex)
        } catch (err: Exception) {
            null
        }
    }
}
