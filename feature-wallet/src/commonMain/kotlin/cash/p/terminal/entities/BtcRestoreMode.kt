package cash.p.terminal.entities

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_SyncModeBlockchain
import cash.p.terminal.resources.SettingsSecurity_SyncModeBlockchainDescription
import cash.p.terminal.resources.SettingsSecurity_SyncModeBlockchair
import cash.p.terminal.resources.SettingsSecurity_SyncModeBlockchairDescription
import cash.p.terminal.resources.SettingsSecurity_SyncModeCosantaExplorer
import cash.p.terminal.resources.SettingsSecurity_SyncModeHybrid
import cash.p.terminal.resources.SettingsSecurity_SyncModeHybridDescription
import cash.p.terminal.resources.SettingsSecurity_SyncModePirateCashExplorer
import io.horizontalsystems.core.entities.BlockchainType
import org.jetbrains.compose.resources.StringResource

enum class BtcRestoreMode(val raw: String) {
    Blockchair("blockchair"),
    Hybrid("hybrid"),
    Blockchain("blockchain");

    fun getTitle(blockchainType: BlockchainType): StringResource = when (this) {
        Blockchair -> {
            when (blockchainType) {
                BlockchainType.Cosanta -> {
                    Res.string.SettingsSecurity_SyncModeCosantaExplorer
                }
                BlockchainType.PirateCash -> {
                    Res.string.SettingsSecurity_SyncModePirateCashExplorer
                }
                else -> {
                    Res.string.SettingsSecurity_SyncModeBlockchair
                }
            }
        }

        Hybrid -> Res.string.SettingsSecurity_SyncModeHybrid
        Blockchain -> Res.string.SettingsSecurity_SyncModeBlockchain
    }

    val description: StringResource
        get() = when (this) {
            Blockchair -> Res.string.SettingsSecurity_SyncModeBlockchairDescription
            Hybrid -> Res.string.SettingsSecurity_SyncModeHybridDescription
            Blockchain -> Res.string.SettingsSecurity_SyncModeBlockchainDescription
        }

}
