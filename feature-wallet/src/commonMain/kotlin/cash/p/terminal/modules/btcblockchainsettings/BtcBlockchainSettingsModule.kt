package cash.p.terminal.modules.btcblockchainsettings

import org.jetbrains.compose.resources.DrawableResource

internal object BtcBlockchainSettingsModule {

    data class ViewItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val selected: Boolean,
        val icon: BlockchainSettingsIcon
    )

    sealed class BlockchainSettingsIcon {
        data class ApiIcon(val resId: DrawableResource): BlockchainSettingsIcon()
        data class BlockchainIcon(val url: String): BlockchainSettingsIcon()
    }

}
